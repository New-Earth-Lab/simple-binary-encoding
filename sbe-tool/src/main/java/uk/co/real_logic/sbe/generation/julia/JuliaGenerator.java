/*
 * Copyright 2013-2024 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.sbe.generation.julia;

import static uk.co.real_logic.sbe.generation.Generators.toUpperFirstChar;
import static uk.co.real_logic.sbe.generation.julia.JuliaUtil.formatStructName;
import static uk.co.real_logic.sbe.generation.julia.JuliaUtil.formatPropertyName;
import static uk.co.real_logic.sbe.generation.julia.JuliaUtil.formatReadBytes;
import static uk.co.real_logic.sbe.generation.julia.JuliaUtil.formatWriteBytes;
import static uk.co.real_logic.sbe.generation.julia.JuliaUtil.juliaTypeName;
import static uk.co.real_logic.sbe.ir.GenerationUtil.collect;
import static uk.co.real_logic.sbe.ir.GenerationUtil.collectFields;
import static uk.co.real_logic.sbe.ir.GenerationUtil.collectGroups;
import static uk.co.real_logic.sbe.ir.GenerationUtil.collectVarData;
import static uk.co.real_logic.sbe.ir.GenerationUtil.findEndSignal;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.agrona.Strings;
import org.agrona.Verify;
import org.agrona.generation.OutputManager;

import uk.co.real_logic.sbe.PrimitiveType;
import uk.co.real_logic.sbe.generation.CodeGenerator;
import uk.co.real_logic.sbe.generation.Generators;
import uk.co.real_logic.sbe.ir.Encoding;
import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.ir.Signal;
import uk.co.real_logic.sbe.ir.Token;

/**
 * Codec generator for the Julia programming language.
 */
@SuppressWarnings("MethodLength")
public class JuliaGenerator implements CodeGenerator
{
    private static final String BASE_INDENT = "";
    private static final String INDENT = "    ";

    private final Ir ir;
    private final OutputManager outputManager;

    private final Set<String> modules = new TreeSet<>();

    /**
     * Create a new Julia language {@link CodeGenerator}.
     *
     * @param ir                            for the messages and types.
     * @param outputManager                 for generating the codecs to.
     */
    public JuliaGenerator(final Ir ir, final OutputManager outputManager)
    {
        Verify.notNull(ir, "ir");
        Verify.notNull(outputManager, "outputManager");

        this.ir = ir;
        this.outputManager = outputManager;
    }

    private void generateGroupStruct(
        final StringBuilder sb,
        final String groupStructName,
        final String indent,
        final List<Token> fields,
        final List<Token> groups)
    {
        new Formatter(sb).format("\n" +
            indent + "mutable struct %1$s{T<:AbstractArray{UInt8},R<:Ref{Int64}}\n" +
            indent + "    buffer::T\n" +
            indent + "    offset::Int64\n" +
            indent + "    position_ptr::R\n" +
            indent + "    block_length::Int64\n" +
            indent + "    acting_version::Int64\n" +
            indent + "    initial_position::Int64\n" +
            indent + "    count::Int64\n" +
            indent + "    index::Int64\n" +
            indent + "end\n\n",
            groupStructName);
    }

    private static void generateGroupMethods(
        final StringBuilder sb,
        final String groupStructName,
        final List<Token> tokens,
        final int index,
        final String indent)
    {
        final String dimensionsStructName = formatStructName(tokens.get(index + 1).name());
        final int dimensionHeaderLength = tokens.get(index + 1).encodedLength();
        final int blockLength = tokens.get(index).encodedLength();
        final Token numInGroupToken = Generators.findFirst("numInGroup", tokens, index);

        new Formatter(sb).format(
            indent + "@inline function %1$sDecoder(buffer, position_ptr, acting_version)\n" +
            indent + "    dimensions = %2$s(buffer, position_ptr[], acting_version)\n" +
            indent + "    initial_position = position_ptr[]\n" +
            indent + "    position_ptr[] += %3$d\n" +
            indent + "    return %1$s(buffer, 0, position_ptr, Int64(blockLength(dimensions)),\n" +
            indent + "        acting_version, initial_position, Int64(numInGroup(dimensions)), 0)\n" +
            indent + "end\n",
            groupStructName,
            dimensionsStructName,
            dimensionHeaderLength);

        final long minCount = numInGroupToken.encoding().applicableMinValue().longValue();
        final String minCheck = minCount > 0 ? "count < " + minCount + " || " : "";

        new Formatter(sb).format(
            indent + "@inline function %1$sEncoder(buffer, count, position_ptr, acting_version)\n" +
            indent + "    if %2$scount > %3$d\n" +
            indent + "        error(lazy\"count outside of allowed range [E110]\")\n" +
            indent + "    end\n" +
            indent + "    dimensions = %4$s(buffer, position_ptr[], acting_version)\n" +
            indent + "    blockLength!(dimensions, %5$d)\n" +
            indent + "    numInGroup!(dimensions, count)\n" +
            indent + "    initial_position = position_ptr[]\n" +
            indent + "    position_ptr[] += %6$d\n" +
            indent + "    return %1$s(buffer, 0, position_ptr, %5$d, acting_version, initial_position,\n" +
            indent + "        count, 0)\n" +
            indent + "end\n",
            groupStructName,
            minCheck,
            numInGroupToken.encoding().applicableMaxValue().longValue(),
            dimensionsStructName,
            blockLength,
            dimensionHeaderLength);

        new Formatter(sb).format("\n" +
            indent + "sbe_header_size(::%3$s) = %1$d\n" +
            indent + "sbe_block_length(::%3$s) = %2$d\n" +
            indent + "sbe_acting_block_length(g::%3$s) = g.block_length\n" +
            indent + "sbe_position(g::%3$s) = g.position_ptr[]\n" +
            indent + "@inline sbe_position!(g::%3$s, position) = g.position_ptr[] = sbe_check_position(g, position)\n" +
            indent + "sbe_position_ptr(g::%3$s) = g.position_ptr\n" +
            indent + "@inline sbe_check_position(g::%3$s, position) = (checkbounds(g.buffer, position + 1); position)\n" +
            indent + "@inline function next!(g::%3$s)\n" +
            indent + "    if g.index >= g.count\n" +
            indent + "        error(lazy\"index >= count [E108]\")\n" +
            indent + "    end\n" +
            indent + "    g.offset = sbe_position(g)\n" +
            indent + "    if (g.offset + 1 + g.block_length) > Base.length(g.buffer)\n" +
            indent + "        error(lazy\"buffer too short for next group index [E108]\")\n" +
            indent + "    end\n" +
            indent + "    sbe_position!(g, g.offset + g.block_length)\n" +
            indent + "    g.index += 1\n" +
            indent + "    return g\n" +
            indent + "end\n" +
            indent + "@inline function Base.iterate(g::%3$s, state = nothing)\n" +
            indent + "    if g.index < g.count\n" +
            indent + "        g.offset = sbe_position(g)\n" +
            indent + "        sbe_position!(g, g.offset + g.block_length)\n" +
            indent + "        g.index += 1\n" +
            indent + "        return g, nothing\n" +
            indent + "    else\n" +
            indent + "        return nothing\n" +
            indent + "    end\n" +
            indent + "end\n" +
            indent + "Base.eltype(::Type{<:%3$s}) = %3$s\n" +
            indent + "Base.isdone(g::%3$s, state...) = g.index >= g.count\n" +
            indent + "Base.length(g::%3$s) = g.count\n",
            dimensionHeaderLength,
            blockLength,
            groupStructName);

        new Formatter(sb).format("\n" +
            indent + "function reset_count_to_index!(g::%1$s)\n" +
            indent + "    g.count = g.index\n" +
            indent + "    dimensions = %2$s(g.buffer, g.initial_position, g.acting_version)\n" +
            indent + "    numInGroup!(dimensions, g.count)\n" +
            indent + "    return g.count\n" +
            indent + "end\n",
            groupStructName,
            dimensionsStructName);
    }

    private static void generateGroupProperty(
        final StringBuilder sb,
        final String groupName,
        final String outerStructName,
        final Token token,
        final String indent)
    {
        final String propertyName = formatPropertyName(groupName);
        final String groupStructName = formatStructName(groupName);
        final int version = token.version();

        if (version > 0)
        {
            new Formatter(sb).format("\n" +
                indent + "function %2$s(m::%1$s)\n" +
                indent + "    if m.acting_version < %3$d\n" +
                indent + "        return %4$s(m.buffer, 0, sbe_position_ptr(m), 0, m.acting_version, 0, 0, 0)\n" +
                indent + "    end\n" +
                indent + "    return %4$sDecoder(m.buffer, sbe_position_ptr(m), m.acting_version)\n" +
                indent + "end\n",
                outerStructName,
                propertyName,
                version,
                groupStructName);
        }
        else
        {
            new Formatter(sb).format("\n" +
                indent + "function %2$s(m::%1$s)\n" +
                indent + "    return %3$sDecoder(m.buffer, sbe_position_ptr(m), m.acting_version)\n" +
                indent + "end\n",
                outerStructName,
                propertyName,
                groupStructName);
        }

        new Formatter(sb).format("\n" +
            indent + "@inline function %2$s_count!(m::%1$s, count)\n" +
            indent + "    return %3$sEncoder(m.buffer, count, sbe_position_ptr(m), m.acting_version)\n" +
            indent + "end\n",
            outerStructName,
            propertyName,
            groupStructName);

        new Formatter(sb).format(
            indent + "%1$s_id(::%3$s) = %2$d\n",
            propertyName,
            token.id(),
            outerStructName);

        new Formatter(sb).format(
            indent + "%1$s_since_version(::%3$s) = %2$d\n" +
            indent + "%1$s_in_acting_version(m::%3$s) = m.acting_version >= %1$s_since_version(m)\n",
            propertyName,
            version,
            outerStructName);
    }

    private static CharSequence generateChoiceNotPresentCondition(final int sinceVersion)
    {
        if (0 == sinceVersion)
        {
            return "";
        }

        return String.format(
            "    if m.acting_version < %1$d\n" +
            "        return false\n" +
            "    end\n\n",
            sinceVersion);
    }

    private static CharSequence generateArrayFieldNotPresentCondition(
        final int sinceVersion, final String indent)
    {
        if (0 == sinceVersion)
        {
            return "";
        }

        return String.format(
            indent + "    if m.acting_version < %1$d\n" +
            indent + "        return @inbounds reinterpret(%1$s, view(m.buffer, 1:0))\n" +
            indent + "    end\n\n",
            sinceVersion);
    }

    private static CharSequence generateArrayLengthNotPresentCondition(
        final int sinceVersion, final String indent)
    {
        if (0 == sinceVersion)
        {
            return "";
        }

        return String.format(
            indent + "    if m.acting_version < %1$d\n" +
            indent + "        return 0\n" +
            indent + "    end\n\n",
            sinceVersion);
    }

    private static CharSequence generateStringNotPresentCondition(
        final int sinceVersion, final String indent)
    {
        if (0 == sinceVersion)
        {
            return "";
        }

        return String.format(
            indent + "    if m.acting_version < %1$d\n" +
            indent + "        return T(view(m.buffer, 1:0))\n" +
            indent + "    end\n\n",
            sinceVersion);
    }

    private static CharSequence generateTypeFieldNotPresentCondition(
        final int sinceVersion, final String indent)
    {
        if (0 == sinceVersion)
        {
            return "";
        }

        return String.format(
            indent + "    if m.acting_version < %1$d\n" +
            indent + "        return view(m.buffer, 1:0)\n" +
            indent + "    end\n\n",
            sinceVersion);
    }

    private static CharSequence generateEnumDeclaration(final String name, final Token encodingToken)
    {
        final Encoding encoding = encodingToken.encoding();
        return "@enum " + name + "::" + juliaTypeName(encoding.primitiveType()) + " begin\n";
    }

    private static void generateFieldMetaAttributeMethod(
        final StringBuilder sb, final Token token, final String indent, final String structName)
    {
        final Encoding encoding = token.encoding();
        final String propertyName = formatPropertyName(token.name());
        final String epoch = encoding.epoch() == null ? "" : encoding.epoch();
        final String timeUnit = encoding.timeUnit() == null ? "" : encoding.timeUnit();
        final String semanticType = encoding.semanticType() == null ? "" : encoding.semanticType();

        new Formatter(sb).format("\n" +
            "function %2$s_meta_attribute(::%1$s, meta_attribute)\n",
            structName,
            propertyName);

        if (!Strings.isEmpty(epoch))
        {
            new Formatter(sb).format(
                indent + "    meta_attribute === :epoch && return :%1$s\n",
                epoch);
        }

        if (!Strings.isEmpty(timeUnit))
        {
            new Formatter(sb).format(
                indent + "    meta_attribute === :time_unit && return :%1$s\n",
                timeUnit);
        }

        if (!Strings.isEmpty(semanticType))
        {
            new Formatter(sb).format(
                indent + "    meta_attribute === :semantic_type && return :%1$s\n",
                semanticType);
        }

        new Formatter(sb).format(
            indent + "    meta_attribute === :presence && return :%1$s\n",
            encoding.presence().toString().toLowerCase());

        new Formatter(sb).format(
            indent + "    error(lazy\"unknown attribute: $meta_attribute\")\n" +
            indent + "end\n");
    }

    private static CharSequence generateEnumFieldNotPresentCondition(
        final int sinceVersion, final String enumName, final String indent)
    {
        if (0 == sinceVersion)
        {
            return "";
        }

        return String.format(
            indent + "    if m.acting_version < %1$d\n" +
            indent + "        return %2$s_NULL_VALUE\n" +
            indent + "    end\n\n",
            sinceVersion,
            enumName);
    }

    private static void generateBitsetProperty(
        final StringBuilder sb,
        final String propertyName,
        final Token token,
        final String indent,
        final String structName)
    {
        final String bitsetName = formatStructName(token.applicableTypeName());
        final int offset = token.offset();

        new Formatter(sb).format(
            indent + "%2$s(m::%4$s) = %1$s(m.buffer, m.offset + %3$d, m.acting_version)\n",
            bitsetName,
            propertyName,
            offset,
            structName);

        new Formatter(sb).format(
            indent + "%1$s_encoding_length(::%3$s) = %2$d\n\n",
            propertyName,
            token.encoding().primitiveType().size(),
            structName);
    }

    private static void generateCompositeProperty(
        final StringBuilder sb,
        final String propertyName,
        final Token token,
        final String indent,
        final String structName)
    {
        final String compositeName = formatStructName(token.applicableTypeName());
        final int offset = token.offset();

        new Formatter(sb).format(
            indent + "%2$s(m::%4$s) = %1$s(m.buffer, m.offset + %3$d, m.acting_version)\n\n",
            compositeName,
            propertyName,
            offset,
            structName);
    }

    /**
     * Generate the composites for dealing with the message header.
     *
     * @throws IOException if an error is encountered when writing the output.
     */
    public void generateMessageHeaderStub() throws IOException
    {
        generateComposite(ir.headerStructure().tokens());
    }

    private List<String> generateTypeStubs() throws IOException
    {
        final List<String> typesToInclude = new ArrayList<>();

        for (final List<Token> tokens : ir.types())
        {
            switch (tokens.get(0).signal())
            {
                case BEGIN_ENUM:
                    generateEnum(tokens);
                    typesToInclude.add(tokens.get(0).applicableTypeName());
                    break;

                default:
                    break;
            }
        }

        for (final List<Token> tokens : ir.types())
        {
            switch (tokens.get(0).signal())
            {
                case BEGIN_SET:
                    generateChoiceSet(tokens);
                    typesToInclude.add(tokens.get(0).applicableTypeName());
                    break;

                default:
                    break;
            }
        }

        for (final List<Token> tokens : ir.types())
        {
            switch (tokens.get(0).signal())
            {
                case BEGIN_COMPOSITE:
                    generateComposite(tokens);
                    typesToInclude.add(tokens.get(0).applicableTypeName());
                    break;

                default:
                    break;
            }
        }

        return typesToInclude;
    }

    private List<String> generateTypesToIncludes(final List<Token> tokens)
    {
        final List<String> typesToInclude = new ArrayList<>();

        for (final Token token : tokens)
        {
            switch (token.signal())
            {
                case BEGIN_ENUM:
                case BEGIN_SET:
                case BEGIN_COMPOSITE:
                    typesToInclude.add(token.applicableTypeName());
                    break;

                default:
                    break;
            }
        }

        return typesToInclude;
    }

    /**
     * {@inheritDoc}
     */
    public void generate() throws IOException
    {
        generateUtils();

        generateMessageHeaderStub();
        final List<String> typesToInclude = generateTypeStubs();
        final List<String> messagesToInclude = generateMessages();

        final String moduleName = namespacesToModuleName(ir.namespaces());

        try (Writer fileOut = outputManager.createOutput(moduleName))
        {
            fileOut.append(generateModule(moduleName, typesToInclude, messagesToInclude));
        }
    }

    private List<String> generateMessages() throws IOException
    {
        final List<String> messagesToInclude = new ArrayList<>();

        for (final List<Token> tokens : ir.messages())
        {
            final Token msgToken = tokens.get(0);
            final String structName = formatStructName(msgToken.name());

            try (Writer out = outputManager.createOutput(structName))
            {
                final List<Token> messageBody = tokens.subList(1, tokens.size() - 1);
                int i = 0;

                final List<Token> fields = new ArrayList<>();
                i = collectFields(messageBody, i, fields);

                final List<Token> groups = new ArrayList<>();
                i = collectGroups(messageBody, i, groups);

                final List<Token> varData = new ArrayList<>();
                collectVarData(messageBody, i, varData);

                final StringBuilder sb = new StringBuilder();

                // generateGroupStructs(sb, groups, BASE_INDENT);

                sb.append(generateMessageFlyweightStruct(structName, fields, groups));
                sb.append(generateMessageFlyweightMethods(structName, msgToken));

                generateFields(sb, structName, fields, BASE_INDENT);
                generateGroups(sb, groups, BASE_INDENT, structName);
                generateVarData(sb, structName, varData, BASE_INDENT);
                generateDisplay(sb, msgToken.name(), structName, fields, groups, varData, INDENT);
                sb.append(generateMessageLength(groups, varData, BASE_INDENT, structName));

                out.append(generateFileHeader());
                out.append(sb);
            }

            messagesToInclude.add(structName);
        }

        return messagesToInclude;
    }

    private void generateUtils() throws IOException
    {
        try (Writer out = outputManager.createOutput("Utils"))
        {
            out.append(generateFileHeader());
            out.append(
                "@inline encode_le!(::Type{T}, buffer, offset, value) where {T} = @inbounds reinterpret(T, view(buffer, offset+1:offset+sizeof(T)))[] = htol(value)\n" +
                "@inline encode_be!(::Type{T}, buffer, offset, value) where {T} = @inbounds reinterpret(T, view(buffer, offset+1:offset+sizeof(T)))[] = hton(value)\n" +
                "@inline decode_le(::Type{T}, buffer, offset) where {T} = @inbounds ltoh(reinterpret(T, view(buffer, offset+1:offset+sizeof(T)))[])\n" +
                "@inline decode_be(::Type{T}, buffer, offset) where {T} = @inbounds ntoh(reinterpret(T, view(buffer, offset+1:offset+sizeof(T)))[])\n\n");
        }
    }

    private void generateGroupStructs(
        final StringBuilder sb,
        final List<Token> tokens,
        final String indent)
    {
        for (int i = 0, size = tokens.size(); i < size; i++)
        {
            final Token groupToken = tokens.get(i);
            if (groupToken.signal() != Signal.BEGIN_GROUP)
            {
                throw new IllegalStateException("tokens must begin with BEGIN_GROUP: token=" + groupToken);
            }

            final String groupStructName = formatStructName(groupToken.name());

            ++i;
            final int groupHeaderTokenCount = tokens.get(i).componentTokenCount();
            i += groupHeaderTokenCount;

            final List<Token> fields = new ArrayList<>();
            i = collectFields(tokens, i, fields);

            final List<Token> groups = new ArrayList<>();
            i = collectGroups(tokens, i, groups);

            final List<Token> varData = new ArrayList<>();
            i = collectVarData(tokens, i, varData);

            generateGroupStructs(sb, groups, indent);

            generateGroupStruct(sb, groupStructName, indent, fields, groups);
        }
    }

    private void generateGroups(
        final StringBuilder sb,
        final List<Token> tokens,
        final String indent,
        final String outerStructName)
    {
        for (int i = 0, size = tokens.size(); i < size; i++)
        {
            final Token groupToken = tokens.get(i);
            if (groupToken.signal() != Signal.BEGIN_GROUP)
            {
                throw new IllegalStateException("tokens must begin with BEGIN_GROUP: token=" + groupToken);
            }

            final String groupName = groupToken.name();
            final String groupStructName = formatStructName(groupToken.name());

            final int groupStart = i;

            ++i;
            final int groupHeaderTokenCount = tokens.get(i).componentTokenCount();
            i += groupHeaderTokenCount;

            final List<Token> fields = new ArrayList<>();
            i = collectFields(tokens, i, fields);

            final List<Token> groups = new ArrayList<>();
            i = collectGroups(tokens, i, groups);

            final List<Token> varData = new ArrayList<>();
            i = collectVarData(tokens, i, varData);

            generateGroupStruct(sb, groupStructName, indent, fields, groups);
            generateGroupMethods(sb, groupStructName, tokens, groupStart, indent);
            generateFields(sb, groupStructName, fields, indent);
            generateGroups(sb, groups, indent, groupStructName);
            generateVarData(sb, groupStructName, varData, indent);

            sb.append(generateGroupDisplay(groupStructName, fields, groups, varData, indent));
            sb.append(generateMessageLength(groups, varData, indent, groupStructName));

            generateGroupProperty(sb, groupName, outerStructName, groupToken, indent);
        }
    }

    private void generateVarData(
        final StringBuilder sb,
        final String structName,
        final List<Token> tokens,
        final String indent)
    {

        for (int i = 0, size = tokens.size(); i < size; )
        {
            final Token token = tokens.get(i);
            if (token.signal() != Signal.BEGIN_VAR_DATA)
            {
                throw new IllegalStateException("tokens must begin with BEGIN_VAR_DATA: token=" + token);
            }

            final String propertyName = formatPropertyName(token.name());
            final Token lengthToken = Generators.findFirst("length", tokens, i);
            final Token varDataToken = Generators.findFirst("varData", tokens, i);
            final String characterEncoding = varDataToken.encoding().characterEncoding();
            final int lengthOfLengthField = lengthToken.encodedLength();
            final String lengthJuliaType = juliaTypeName(lengthToken.encoding().primitiveType());
            final String varDataJuliaType = juliaTypeName(varDataToken.encoding().primitiveType());
            final int version = token.version();

            generateFieldMetaAttributeMethod(sb, token, indent, structName);

            new Formatter(sb).format("\n" +
                indent + "%1$s_character_encoding(::%3$s) = \"%2$s\"\n",
                propertyName,
                characterEncoding,
                structName);

            new Formatter(sb).format(
                    indent + "%1$s_since_version(::%4$s) = %2$d\n" +
                    indent + "%1$s_in_acting_version(m::%4$s) = m.acting_version >= %2$d\n" +
                    indent + "%1$s_id(::%4$s) = %3$d\n",
                    propertyName,
                    token.version(),
                    token.id(),
                    structName);

            new Formatter(sb).format(
                indent + "%1$s_header_length(::%3$s) = %2$d\n",
                propertyName,
                lengthOfLengthField,
                structName);

            new Formatter(sb).format(
                indent + "@inline function %1$s_length(m::%4$s)\n" +
                "%2$s" +
                indent + "    return %3$s\n"+
                indent + "end\n",
                propertyName,
                generateArrayLengthNotPresentCondition(version, BASE_INDENT),
                generateGet(lengthToken, "m.buffer", "sbe_position(m)"),
                structName);

            new Formatter(sb).format("\n" +
                indent + "@inline function %1$s_length!(m::%4$s, n)\n" +
                "%2$s" +
                indent + "    if !checkbounds(Bool, m.buffer, sbe_position(m) + 1 + 4 + n)\n" +
                indent + "        error(lazy\"buffer too short for data length\")\n" +
                indent + "    end\n" +
                indent + "    return %3$s\n" +
                indent + "end\n",
                propertyName,
                generateArrayLengthNotPresentCondition(version, BASE_INDENT),
                generatePut(lengthToken, "m.buffer", "sbe_position(m)", "n"),
                structName);

            new Formatter(sb).format("\n" +
                indent + "@inline function skip_%1$s!(m::%4$s)\n" +
                "%2$s" +
                indent + "    len = %1$s_length(m)\n" +
                indent + "    pos = sbe_position(m) + len + %3$d\n" +
                indent + "    sbe_position!(m, pos)\n" +
                indent + "    return len\n" +
                indent + "end\n",
                propertyName,
                generateArrayLengthNotPresentCondition(token.version(), indent),
                lengthOfLengthField,
                structName);

            new Formatter(sb).format("\n" +
                indent + "@inline function %1$s(m::%6$s)\n" +
                "%2$s" +
                indent + "    len = %1$s_length(m)\n" +
                indent + "    pos = sbe_position(m) + %3$d\n" +
                indent + "    sbe_position!(m, pos + len)\n" +
                indent + "    return @inbounds reinterpret(%5$s, view(m.buffer, pos+1:pos+len))\n" +
                indent + "end\n",
                propertyName,
                generateTypeFieldNotPresentCondition(token.version(), indent),
                lengthOfLengthField,
                lengthJuliaType,
                varDataJuliaType,
                structName);

            if (null != characterEncoding)
            {
                addModuleToUsing("StringViews");
                new Formatter(sb).format("\n" +
                    indent + "%1$s(T::Type{<:AbstractString}, m::%2$s) = T(%1$s(m))\n" +
                    indent + "%1$s_as_string(m::%2$s) = %1$s(StringView, m)\n",
                    propertyName,
                    structName);
            }

            new Formatter(sb).format("\n" +
                indent + "@inline function Base.resize!(m::%3$s, n)\n" +
                "%5$s" +
                indent + "    %1$s_length!(m, n)\n" +
                indent + "    return %1$s(m)\n" +
                indent + "end\n" +
                indent + "@inline function %1$s!(m::%3$s, src::T) where {T<:AbstractArray{%4$s}}\n" +
                "%5$s" +
                indent + "    len = Base.length(src)\n" +
                indent + "    %1$s_length!(m, len)\n" +
                indent + "    pos = sbe_position(m) + %2$d\n" +
                indent + "    dest = reinterpret(%4$s, view(m.buffer, pos+1:pos+len))\n" +
                indent + "    sbe_position!(m, pos + len)\n" +
                indent + "    if len != 0\n" +
                indent + "        copyto!(dest, src)\n" +
                indent + "    end\n" +
                indent + "    return dest\n" +
                indent + "end\n",
                propertyName,
                lengthOfLengthField,
                structName,
                varDataJuliaType,
                generateTypeFieldNotPresentCondition(token.version(), indent));

            i += token.componentTokenCount();
        }
    }

    private void generateChoiceSet(final List<Token> tokens) throws IOException
    {
        final Token token = tokens.get(0);
        final String choiceName = formatStructName(token.applicableTypeName());

        try (Writer fileOut = outputManager.createOutput(choiceName))
        {
            final StringBuilder out = new StringBuilder();

            out.append(generateFixedFlyweightStruct(choiceName, tokens));
            out.append(generateFixedFlyweightMethods(choiceName, tokens));

            final Encoding encoding = token.encoding();
            new Formatter(out).format("\n" +
                "@inline clear(m::%1$s)::%1$s = %2$s\n",
                choiceName,
                generatePut(token, "m.buffer", "m.offset", "0"));

            new Formatter(out).format(
                "@inline is_empty(m::%1$s) = %2$s == 0\n",
                choiceName,
                generateGet(token, "m.buffer", "m.offset"));

            new Formatter(out).format(
                "@inline raw_value(m::%1$s) = %2$s\n",
                choiceName,
                generateGet(token, "m.buffer", "m.offset"));

            out.append(generateChoices(choiceName, tokens.subList(1, tokens.size() - 1)));
            out.append(generateChoicesDisplay(choiceName, tokens.subList(1, tokens.size() - 1)));
            out.append("\n");

            fileOut.append(generateFileHeader());
            fileOut.append(out);
        }
    }

    private void generateEnum(final List<Token> tokens) throws IOException
    {
        final Token enumToken = tokens.get(0);
        final String enumName = formatStructName(tokens.get(0).applicableTypeName());

        try (Writer fileOut = outputManager.createOutput(enumName))
        {
            final StringBuilder out = new StringBuilder();

            out.append(generateEnumDeclaration(enumName, enumToken));

            out.append(generateEnumValues(tokens.subList(1, tokens.size() - 1), enumName, enumToken));

            out.append(generateEnumDisplay(tokens.subList(1, tokens.size() - 1), enumToken));

            out.append("\n");

            fileOut.append(generateFileHeader());
            fileOut.append(out);
        }
    }

    private void generateComposite(final List<Token> tokens) throws IOException
    {
        final String compositeName = formatStructName(tokens.get(0).applicableTypeName());

        try (Writer fileOut = outputManager.createOutput(compositeName))
        {
            final StringBuilder out = new StringBuilder();

            out.append(generateFixedFlyweightStruct(compositeName, tokens));
            out.append(generateFixedFlyweightMethods(compositeName, tokens));

            out.append(generateCompositePropertyElements(
                compositeName, tokens.subList(1, tokens.size() - 1), BASE_INDENT));

            out.append(generateCompositeDisplay(
                tokens.get(0).applicableTypeName(), tokens.subList(1, tokens.size() - 1)));

            fileOut.append(generateFileHeader());
            fileOut.append(out);
        }
    }

    private CharSequence generateChoices(final String bitsetStructName, final List<Token> tokens)
    {
        final StringBuilder sb = new StringBuilder();

        tokens
            .stream()
            .filter((token) -> token.signal() == Signal.CHOICE)
            .forEach((token) ->
            {
                final String choiceName = formatPropertyName(token.name());
                final PrimitiveType type = token.encoding().primitiveType();
                final String typeName = juliaTypeName(type);
                final String choiceBitPosition = token.encoding().constValue().toString();
                final CharSequence constantOne = generateLiteral(type, "1");

                new Formatter(sb).format("\n" +
                    "@inline function %2$s(m::%1$s)\n" +
                    "%6$s" +
                    "    return %7$s & (%5$s << %4$s) != 0\n" +
                    "end\n",
                    bitsetStructName,
                    choiceName,
                    typeName,
                    choiceBitPosition,
                    constantOne,
                    generateChoiceNotPresentCondition(token.version()),
                    generateGet(token, "m.buffer", "m.offset"));

                new Formatter(sb).format(
                    "@inline function %2$s!(m::%1$s, value::Bool)\n" +
                    "    bits = %7$s\n" +
                    "    if value\n" +
                    "        bits |= %6$s << %5$s\n" +
                    "    else\n" +
                    "        bits &= ~(%6$s << %5$s)\n" +
                    "    end\n" +
                    "    %4$s\n" +
                    "end\n",
                    bitsetStructName,
                    choiceName,
                    typeName,
                    generatePut(token, "m.buffer", "m.offset", "bits"),
                    choiceBitPosition,
                    constantOne,
                    generateGet(token, "m.buffer", "m.offset"));
            });

        return sb;
    }

    private CharSequence generateEnumValues(
        final List<Token> tokens,
        final String enumName,
        final Token encodingToken)
    {
        final StringBuilder sb = new StringBuilder();
        final Encoding encoding = encodingToken.encoding();

        for (final Token token : tokens)
        {
            final CharSequence constVal = generateLiteral(
                token.encoding().primitiveType(), token.encoding().constValue().toString());

            new Formatter(sb).format("    %1$s_%2$s = %3$s\n",
                enumName,
                token.name(),
                constVal);
        }

        final CharSequence nullLiteral = generateLiteral(
            encoding.primitiveType(), encoding.applicableNullValue().toString());

        new Formatter(sb).format("    %1$s_%2$s = %3$s\n",
            enumName,
            "NULL_VALUE",
            nullLiteral);

        sb.append("end\n");

        return sb;
    }

    private CharSequence generateFieldNotPresentCondition(
        final int sinceVersion, final Encoding encoding, final String indent)
    {
        if (0 == sinceVersion)
        {
            return "";
        }

        return String.format(
            "    if m.acting_version < %1$d\n" +
            "        return %2$s\n" +
            "    end\n",
            sinceVersion,
            generateLiteral(encoding.primitiveType(), encoding.applicableNullValue().toString()));
    }


    private String namespacesToModuleName(final CharSequence[] namespaces)
    {
        return toUpperFirstChar(String.join("_", namespaces).replace('.', '_').replace(' ', '_').replace('-', '_'));
    }

    private CharSequence generateModule(
        final String moduleName,
        final List<String> typesToInclude,
        final List<String> messagesToInclude)
    {
        final StringBuilder sb = new StringBuilder();
        final List<String> includes = new ArrayList<>();
        includes.add("Utils");
        includes.addAll(typesToInclude);
        includes.addAll(messagesToInclude);

        sb.append(generateFileHeader());

        sb.append("module ").append(moduleName).append("\n\n");

        if (modules != null && !modules.isEmpty())
        {
            for (final String module : modules)
            {
                sb.append("using ").append(module).append("\n");
            }
            sb.append("\n");
        }

        for (final String incName : includes)
        {
            sb.append("include(\"").append(toUpperFirstChar(incName)).append(".jl\")\n");
        }

        sb.append("end\n");

        return sb;
    }

    private static CharSequence generateFileHeader()
    {
        final StringBuilder sb = new StringBuilder();

        sb.append("# Generated SBE (Simple Binary Encoding) message codec\n");
        sb.append("# Code generated by SBE. DO NOT EDIT.\n\n");

        return sb;
    }

    private CharSequence generateCompositePropertyElements(
        final String containingStructName, final List<Token> tokens, final String indent)
    {
        final StringBuilder sb = new StringBuilder();

        for (int i = 0; i < tokens.size(); )
        {
            final Token fieldToken = tokens.get(i);
            final String propertyName = formatPropertyName(fieldToken.name());

            generateFieldMetaAttributeMethod(sb, fieldToken, indent, containingStructName);
            generateFieldCommonMethods(indent, sb, fieldToken, fieldToken, propertyName, containingStructName);

            switch (fieldToken.signal())
            {
                case ENCODING:
                    generatePrimitiveProperty(sb, containingStructName, propertyName, fieldToken, fieldToken, indent);
                    break;

                case BEGIN_ENUM:
                    generateEnumProperty(sb, containingStructName, fieldToken, propertyName, fieldToken, indent);
                    break;

                case BEGIN_SET:
                    generateBitsetProperty(sb, propertyName, fieldToken, indent, containingStructName);
                    break;

                case BEGIN_COMPOSITE:
                    generateCompositeProperty(sb, propertyName, fieldToken, indent, containingStructName);
                    break;

                default:
                    break;
            }

            i += tokens.get(i).componentTokenCount();
        }

        return sb;
    }

    private void generatePrimitiveProperty(
        final StringBuilder sb,
        final String containingStructName,
        final String propertyName,
        final Token propertyToken,
        final Token encodingToken,
        final String indent)
    {
        generatePrimitiveFieldMetaData(sb, propertyName, encodingToken, indent, containingStructName);

        if (encodingToken.isConstantEncoding())
        {
            generateConstPropertyMethods(sb, containingStructName, propertyName, encodingToken, indent);
        }
        else
        {
            generatePrimitivePropertyMethods(
                sb, containingStructName, propertyName, propertyToken, encodingToken, indent);
        }
    }

    private void generatePrimitivePropertyMethods(
        final StringBuilder sb,
        final String containingStructName,
        final String propertyName,
        final Token propertyToken,
        final Token encodingToken,
        final String indent)
    {
        final int arrayLength = encodingToken.arrayLength();
        if (arrayLength == 1)
        {
            generateSingleValueProperty(sb, containingStructName, propertyName, propertyToken, encodingToken, indent);
        }
        else if (arrayLength > 1)
        {
            generateArrayProperty(sb, containingStructName, propertyName, propertyToken, encodingToken, indent);
        }
    }

    private void generatePrimitiveFieldMetaData(
        final StringBuilder sb,
        final String propertyName,
        final Token token,
        final String indent,
        final String structName)
    {
        final Encoding encoding = token.encoding();
        final PrimitiveType primitiveType = encoding.primitiveType();
        final String juliaTypeName = juliaTypeName(primitiveType);
        final CharSequence nullValueString = generateNullValueLiteral(primitiveType, encoding);

        new Formatter(sb).format(
            indent + "%1$s_null_value(::%4$s) = %3$s\n",
            propertyName,
            juliaTypeName,
            nullValueString,
            structName);

        new Formatter(sb).format(
            indent + "%1$s_min_value(::%4$s) = %3$s\n",
            propertyName,
            juliaTypeName,
            generateLiteral(primitiveType, token.encoding().applicableMinValue().toString()),
            structName);

        new Formatter(sb).format(
            indent + "%1$s_max_value(::%4$s) = %3$s\n",
            propertyName,
            juliaTypeName,
            generateLiteral(primitiveType, token.encoding().applicableMaxValue().toString()),
            structName);

        new Formatter(sb).format(
            indent + "%1$s_encoding_length(::%3$s) = %2$d\n",
            propertyName,
            token.encoding().primitiveType().size() * token.arrayLength(),
            structName);
    }

    private CharSequence generatePut(
        final Token token,
        final String buffer,
        final String index,
        final String value)
    {
        final PrimitiveType primitiveType = token.encoding().primitiveType();
        final ByteOrder byteOrder = token.encoding().byteOrder();
        final String juliaTypeName = juliaTypeName(primitiveType);
        final StringBuilder sb = new StringBuilder();

        new Formatter(sb).format(
            "%1$s(%2$s, %3$s, %4$s, %5$s)",
            formatWriteBytes(byteOrder, primitiveType),
            juliaTypeName,
            buffer,
            index,
            value);

        return sb;
    }

    private CharSequence generateGet(
        final Token token,
        final String buffer,
        final String index)
    {
        final PrimitiveType primitiveType = token.encoding().primitiveType();
        final ByteOrder byteOrder = token.encoding().byteOrder();
        final String juliaTypeName = juliaTypeName(primitiveType);
        final StringBuilder sb = new StringBuilder();

        new Formatter(sb).format(
            "%1$s(%2$s, %3$s, %4$s)",
            formatReadBytes(byteOrder, primitiveType),
            juliaTypeName,
            buffer,
            index);

        return sb;
    }

    private void generateSingleValueProperty(
        final StringBuilder sb,
        final String containingStructName,
        final String propertyName,
        final Token propertyToken,
        final Token encodingToken,
        final String indent)
    {
        final PrimitiveType primitiveType = encodingToken.encoding().primitiveType();
        final String juliaTypeName = juliaTypeName(primitiveType);
        final int offset = encodingToken.offset();

        new Formatter(sb).format("\n" +
            indent + "@inline function %2$s(m::%1$s)\n" +
            "%4$s" +
            indent + "    return %5$s\n" +
            indent + "end\n",
            containingStructName,
            propertyName,
            juliaTypeName,
            generateFieldNotPresentCondition(propertyToken.version(), encodingToken.encoding(), indent),
            generateGet(encodingToken, "m.buffer", "m.offset + " + Integer.toString(offset)));

        new Formatter(sb).format(
            indent + "@inline %2$s!(m::%1$s, value) = %3$s\n",
            containingStructName,
            propertyName,
            generatePut(encodingToken, "m.buffer", "m.offset + " + Integer.toString(offset), "value"));
    }

    private void generateArrayProperty(
        final StringBuilder sb,
        final String containingStructName,
        final String propertyName,
        final Token propertyToken,
        final Token encodingToken,
        final String indent)
    {
        final PrimitiveType primitiveType = encodingToken.encoding().primitiveType();
        final String juliaTypeName = juliaTypeName(primitiveType);
        final int offset = encodingToken.offset();

        final int arrayLength = encodingToken.arrayLength();
        new Formatter(sb).format(
            indent + "%1$s_length(::%3$s) = %2$d\n",
            propertyName,
            arrayLength,
            formatStructName(containingStructName));

        new Formatter(sb).format("\n" +
            indent + "@inline function %2$s(m::%6$s)\n" +
            indent + "%4$s" +
            indent + "    return @inbounds reinterpret(%1$s, view(m.buffer, m.offset+1+%5$d:m.offset+%5$d+sizeof(%1$s)*%3$d))\n" +
            indent + "end\n",
            juliaTypeName,
            propertyName,
            arrayLength,
            generateArrayFieldNotPresentCondition(propertyToken.version(), indent),
            offset,
            formatStructName(containingStructName));

        // Only use StaticArrays for arrays of less than 100 elements
        if (arrayLength < 100)
        {
            addModuleToUsing("StaticArrays");
            new Formatter(sb).format("\n" +
                indent + "@inline function %2$s_static(m::%6$s)\n" +
                indent + "%4$s" +
                indent + "    return @inbounds reinterpret(SVector{%3$d,%1$s}, view(m.buffer, m.offset+1+%5$d:m.offset+%5$d+sizeof(%1$s)*%3$d))[]\n" +
                indent + "end\n",
                juliaTypeName,
                propertyName,
                arrayLength,
                generateArrayFieldNotPresentCondition(propertyToken.version(), indent),
                offset,
                formatStructName(containingStructName));
        }

        if (encodingToken.encoding().primitiveType() == PrimitiveType.CHAR)
        {
            new Formatter(sb).format("\n" +
                indent + "@inline function %2$s(T::Type{<:AbstractString}, m::%6$s)\n" +
                indent + "%4$s" +
                indent + "    return @inbounds T(view(m.buffer, m.offset+1+%5$d:m.offset+%5$d+sizeof(%1$s)*%3$d))\n" +
                indent + "end\n",
                juliaTypeName,
                propertyName,
                arrayLength,
                generateStringNotPresentCondition(propertyToken.version(), indent),
                offset,
                formatStructName(containingStructName));

            addModuleToUsing("StringViews");
            new Formatter(sb).format("\n" +
                indent + "%1$s_as_string(m::%2$s) = %1$s(StringView, m)\n",
                propertyName,
                formatStructName(containingStructName));
        }

        new Formatter(sb).format("\n" +
            indent + "@inline function %2$s!(m::%6$s, value)\n" +
            indent + "%4$s" +
            indent + "    copyto!(reinterpret(%1$s, view(m.buffer, m.offset+1+%5$d:m.offset+%5$d+sizeof(%1$s)*%3$d)), value)\n" +
            indent + "end\n",
            juliaTypeName,
            propertyName,
            arrayLength,
            generateArrayFieldNotPresentCondition(propertyToken.version(), indent),
            offset,
            formatStructName(containingStructName));
    }

    private void generateConstPropertyMethods(
        final StringBuilder sb,
        final String structName,
        final String propertyName,
        final Token token,
        final String indent)
    {
        final String juliaTypeName = juliaTypeName(token.encoding().primitiveType());

        if (token.encoding().primitiveType() != PrimitiveType.CHAR)
        {
            new Formatter(sb).format(
                indent + "%2$s(::%4$s) = %3$s\n",
                juliaTypeName,
                propertyName,
                generateLiteral(token.encoding().primitiveType(), token.encoding().constValue().toString()),
                structName);
        }
        else
        {
            new Formatter(sb).format(
                indent + "%1$s(::%3$s) = \"%2$s\"\n",
                propertyName,
                token.encoding().constValue().toString(),
                structName);
        }
    }

    private CharSequence generateFixedFlyweightStruct(
        final String structName,
        final List<Token> fields)
    {
        return String.format(
            "struct %1$s{T<:AbstractArray{UInt8}}\n" +
                "    buffer::T\n" +
                "    offset::Int64\n" +
                "    acting_version::Int64\n" +
                "end\n\n",
            structName);
    }

    private CharSequence generateFixedFlyweightMethods(
        final String structName,
        final List<Token> fields)
    {
        int size = fields.get(0).encodedLength();
        String sizeValue = String.format("%1$d", size);
        if (size == -1)
        {
            sizeValue = "typemax(UInt64)";
        }

        return String.format(
                "function %1$s(buffer, offset, acting_version)\n" +
                "    checkbounds(buffer, offset + 1 + %2$s)\n" +
                "    return %1$s(buffer, offset, acting_version)\n" +
                "end\n\n" +

                "sbe_encoded_length(::%1$s) = %2$s\n" +
                "sbe_buffer(m::%1$s) = m.buffer\n" +
                "sbe_offset(m::%1$s) = m.offset\n" +
                "sbe_acting_version(m::%1$s) = m.acting_version\n" +
                "sbe_schema_id(::%1$s) = %3$s\n" +
                "sbe_schema_version(::%1$s) = %4$s\n",
            structName,
            sizeValue,
            Integer.toString(ir.id()),
            Integer.toString(ir.version()));
    }

    private CharSequence generateMessageFlyweightStruct(
        final String structName,
        final List<Token> fields,
        final List<Token> groups)
    {
        return String.format(
            "struct %1$s{T<:AbstractArray{UInt8},R<:Ref{Int64}}\n" +
            "    buffer::T\n" +
            "    offset::Int64\n" +
            "    position_ptr::R\n" +
            "    acting_block_length::Int64\n" +
            "    acting_version::Int64\n" +
            "end\n",
            structName);
    }

    private CharSequence generateMessageFlyweightMethods(
        final String structName,
        final Token token)
    {
        final String semanticType = token.encoding().semanticType() == null ? "" : token.encoding().semanticType();
        final String semanticVersion = ir.semanticVersion() == null ? "" : ir.semanticVersion();

        return String.format(
            "@inline function %1$s(buffer, offset, acting_block_length, acting_version)\n" +
            "    position = Ref{Int64}(offset + acting_block_length)\n" +
            "    return %1$s(buffer, offset, position, acting_block_length, acting_version)\n" +
            "end\n\n" +
            "@inline function %1$sDecoder(buffer, offset, acting_block_length, acting_version)\n" +
            "    position = Ref{Int64}(offset + acting_block_length)\n" +
            "    return %1$s(buffer, offset, position, acting_block_length, acting_version)\n" +
            "end\n\n" +
            "@inline function %1$sDecoder(buffer, offset, hdr::MessageHeader)\n" +
            "    return %1$sDecoder(buffer, offset + sbe_encoded_length(hdr),\n" +
            "        Int64(blockLength(hdr)), Int64(version(hdr)))\n" +
            "end\n\n" +
            "@inline function %1$sEncoder(buffer, offset=0)\n" +
            "    return %1$s(buffer, offset, %2$s, %5$s)\n" +
            "end\n\n" +
            "@inline function %1$sEncoder(buffer, offset, hdr::MessageHeader)\n" +
            "    blockLength!(hdr, %2$s)\n" +
            "    templateId!(hdr, %3$s)\n" +
            "    schemaId!(hdr, %4$s)\n" +
            "    version!(hdr, %5$s)\n" +
            "    return %1$s(buffer, offset + sbe_encoded_length(hdr), %2$s, %5$s)\n" +
            "end\n\n" +

            "sbe_buffer(m::%1$s) = m.buffer\n" +
            "sbe_offset(m::%1$s) = m.offset\n" +
            "sbe_acting_version(m::%1$s) = m.acting_version\n" +
            "sbe_position(m::%1$s) = m.position_ptr[]\n" +
            "@inline sbe_position!(m::%1$s, position) = m.position_ptr[] = sbe_check_position(m, position)\n" +
            "sbe_position_ptr(m::%1$s) = m.position_ptr\n" +
            "@inline sbe_check_position(m::%1$s, position) = (checkbounds(m.buffer, position + 1); position)\n" +
            "sbe_block_length(::%1$s) = %2$s\n" +
            "sbe_template_id(::%1$s) = %3$s\n" +
            "sbe_schema_id(::%1$s) = %4$s\n" +
            "sbe_schema_version(::%1$s) = %5$s\n" +
            "sbe_semantic_type(::%1$s) = \"%6$s\"\n" +
            "sbe_semantic_version(::%1$s) = \"%7$s\"\n" +
            "@inline function sbe_rewind!(m::%1$s)\n" +
            "    sbe_position!(m, m.offset + m.acting_block_length)\n" +
            "    return m\n" +
            "end\n\n" +

            "sbe_encoded_length(m::%1$s) = sbe_position(m) - m.offset\n" +
            "@inline function sbe_decoded_length!(m::%1$s)\n" +
            "    skipper = %1$s(m.buffer, m.offset, sbe_block_length(m), m.acting_version)\n" +
            "    skip!(skipper)\n" +
            "    return sbe_encoded_length(skipper)\n" +
            "end\n",
            structName,
            Integer.toString(token.encodedLength()),
            Integer.toString(token.id()),
            Integer.toString(ir.id()),
            Integer.toString(ir.version()),
            semanticType,
            semanticVersion);
    }

    private void generateFields(
        final StringBuilder sb,
        final String containingStructName,
        final List<Token> tokens,
        final String indent)
    {
        for (int i = 0, size = tokens.size(); i < size; i++)
        {
            final Token signalToken = tokens.get(i);
            if (signalToken.signal() == Signal.BEGIN_FIELD)
            {
                final Token encodingToken = tokens.get(i + 1);
                final String propertyName = formatPropertyName(signalToken.name());

                generateFieldMetaAttributeMethod(sb, signalToken, indent, containingStructName);
                generateFieldCommonMethods(indent, sb, signalToken, encodingToken, propertyName, containingStructName);

                switch (encodingToken.signal())
                {
                    case ENCODING:
                        generatePrimitiveProperty(
                            sb, containingStructName, propertyName, signalToken, encodingToken, indent);
                        break;

                    case BEGIN_ENUM:
                        generateEnumProperty(sb, containingStructName, signalToken, propertyName, encodingToken, indent);
                        break;

                    case BEGIN_SET:
                        generateBitsetProperty(sb, propertyName, encodingToken, indent, containingStructName);
                        break;

                    case BEGIN_COMPOSITE:
                        generateCompositeProperty(sb, propertyName, encodingToken, indent, containingStructName);
                        break;

                    default:
                        break;
                }
            }
        }
    }

    private void generateFieldCommonMethods(
        final String indent,
        final StringBuilder sb,
        final Token fieldToken,
        final Token encodingToken,
        final String propertyName,
        final String structName)
    {
        new Formatter(sb).format(
            indent + "%1$s_id(::%3$s) = %2$d\n",
            propertyName,
            fieldToken.id(),
            structName);

        new Formatter(sb).format(
            indent + "%1$s_since_version(::%3$s) = %2$d\n" +
            indent + "%1$s_in_acting_version(m::%3$s) = m.acting_version >= %1$s_since_version(m)\n",
            propertyName,
            fieldToken.version(),
            structName);

        new Formatter(sb).format(
            indent + "%1$s_encoding_offset(::%3$s) = %2$d\n",
            propertyName,
            encodingToken.offset(),
            structName);
    }

    private void generateEnumProperty(
        final StringBuilder sb,
        final String containingStructName,
        final Token fieldToken,
        final String propertyName,
        final Token encodingToken,
        final String indent)
    {
        final String enumName = formatStructName(encodingToken.applicableTypeName());
        final PrimitiveType primitiveType = encodingToken.encoding().primitiveType();
        final String typeName = juliaTypeName(primitiveType);
        final int offset = encodingToken.offset();

        new Formatter(sb).format(
            indent + "%2$s_encoding_length(::%1$s) = %3$d\n",
            containingStructName,
            propertyName,
            fieldToken.encodedLength());

        if (fieldToken.isConstantEncoding())
        {
            final String constValue = fieldToken.encoding().constValue().toString();

            new Formatter(sb).format(
                indent + "%2$s_raw(::%3$s) = %1$s(%5$s_%4$s)\n",
                typeName,
                propertyName,
                containingStructName,
                constValue.substring(constValue.indexOf(".") + 1),
                enumName);

            new Formatter(sb).format(
                indent + "@inline function %2$s(m::%1$s)\n" +
                "%3$s" +
                indent + "    return %5$s_%4$s\n" +
                indent + "end\n",
                containingStructName,
                propertyName,
                generateEnumFieldNotPresentCondition(fieldToken.version(), enumName, indent),
                constValue.substring(constValue.indexOf(".") + 1),
                enumName);
        }
        else
        {
            final String offsetStr = Integer.toString(offset);
            new Formatter(sb).format(
                indent + "@inline function %1$s_raw(m::%4$s)\n" +
                "%2$s" +
                indent + "    return %3$s\n" +
                indent + "end\n",
                propertyName,
                generateFieldNotPresentCondition(fieldToken.version(), encodingToken.encoding(), indent),
                generateGet(encodingToken, "m.buffer", "m.offset + " + offsetStr),
                containingStructName);

            new Formatter(sb).format(
                indent + "@inline function %2$s(m::%1$s)\n" +
                "%3$s" +
                indent + "    return %5$s(%4$s)\n" +
                indent + "end\n",
                containingStructName,
                propertyName,
                generateEnumFieldNotPresentCondition(fieldToken.version(), enumName, indent),
                generateGet(encodingToken, "m.buffer", "m.offset + " + offsetStr),
                enumName);

            new Formatter(sb).format(
                indent + "@inline %2$s!(m::%1$s, value::%3$s) = %4$s\n",
                formatStructName(containingStructName),
                propertyName,
                enumName,
                generatePut(encodingToken, "m.buffer", "m.offset + " + offsetStr, typeName + "(value)"));
        }
    }

    private CharSequence generateNullValueLiteral(
        final PrimitiveType primitiveType,
        final Encoding encoding)
    {
        return generateLiteral(primitiveType, encoding.applicableNullValue().toString());
    }


    private CharSequence generateLiteral(final PrimitiveType type, final String value)
    {
        String literal = "";
        final String juliaTypeName = juliaTypeName(type);

        switch (type)
        {
            case CHAR:
            case UINT8:
            case UINT16:
            case UINT32:
            case UINT64:
            case INT8:
            case INT16:
            case INT32:
            case INT64:
                literal = juliaTypeName + "(" + value + ")";
                break;

            case FLOAT:
                literal = value.endsWith("NaN") ? "NaN32" : juliaTypeName + "(" + value + ")";
                break;

            case DOUBLE:
                literal = value.endsWith("NaN") ? "NaN" : juliaTypeName + "(" + value + ")";
                break;
        }

        return literal;
    }

    private void addModuleToUsing(final String name)
    {
        modules.add(name);
    }


    private void generateDisplay(
        final StringBuilder sb,
        final String containingStructName,
        final String name,
        final List<Token> fields,
        final List<Token> groups,
        final List<Token> varData,
        final String indent)
    {
        new Formatter(sb).format("\n" +
            "function print_fields(io::IO, writer::%1$s{T}) where {T}\n" +
            "    println(io, \"%1$s view over a type $T\")\n" +
            "    println(io, \"SbeBlockLength: \", sbe_block_length(writer))\n" +
            "    println(io, \"SbeTemplateId:  \", sbe_template_id(writer))\n" +
            "    println(io, \"SbeSchemaId:    \", sbe_schema_id(writer))\n" +
            "    println(io, \"SbeSchemaVersion: \", sbe_schema_version(writer))\n" +
            "    sbe_rewind!(writer)\n" +
            "%2$s" +
            "    sbe_rewind!(writer)\n" +
            "end\n",
            formatStructName(name),
            appendDisplay(containingStructName, fields, groups, varData, indent));
    }


    private CharSequence generateGroupDisplay(
        final String groupStructName,
        final List<Token> fields,
        final List<Token> groups,
        final List<Token> varData,
        final String indent)
    {
        return String.format("\n" +
            "function print_fields(io::IO, writer::%1$s{T}) where {T}\n" +
            "    println(io, \"%1$s view over a type $T\")\n" +
            "%2$s" +
            "end\n",
            groupStructName,
            appendDisplay(groupStructName, fields, groups, varData, indent + INDENT));
    }


    private CharSequence generateCompositeDisplay(final String name, final List<Token> tokens)
    {
        return String.format("\n" +
            "function print_fields(io::IO, writer::%1$s{T}) where {T}\n" +
            "    println(io, \"%1$s view over a type $T\")\n" +
            "%2$s" +
            "end\n\n",
            formatStructName(name),
            appendDisplay(formatStructName(name), tokens, new ArrayList<>(), new ArrayList<>(), INDENT));
    }


    private CharSequence appendDisplay(
        final String containingStructName,
        final List<Token> fields,
        final List<Token> groups,
        final List<Token> varData,
        final String indent)
    {
        final StringBuilder sb = new StringBuilder();
        final boolean[] at_least_one = {false};

        for (int i = 0, size = fields.size(); i < size; )
        {
            final Token fieldToken = fields.get(i);
            final Token encodingToken = fields.get(fieldToken.signal() == Signal.BEGIN_FIELD ? i + 1 : i);

            writeTokenDisplay(sb, fieldToken.name(), encodingToken, at_least_one, indent);

            i += fieldToken.componentTokenCount();
        }

        for (int i = 0, size = groups.size(); i < size; i++)
        {
            final Token groupToken = groups.get(i);
            if (groupToken.signal() != Signal.BEGIN_GROUP)
            {
                throw new IllegalStateException("tokens must begin with BEGIN_GROUP: token=" + groupToken);
            }

            if (at_least_one[0])
            {
                sb.append(indent).append("println(io)\n");
            }
            at_least_one[0] = true;

            new Formatter(sb).format(
                indent + "println(io, \"%1$s:\")\n" +
                indent + "for group in %2$s(writer)\n" +
                indent + "    print_fields(io, group)\n" +
                indent + "    println(io)\n" +
                indent + "end\n",
                formatStructName(groupToken.name()),
                formatPropertyName(groupToken.name()),
                groupToken.name());

            i = findEndSignal(groups, i, Signal.END_GROUP, groupToken.name());
        }

        for (int i = 0, size = varData.size(); i < size; )
        {
            final Token varDataToken = varData.get(i);
            if (varDataToken.signal() != Signal.BEGIN_VAR_DATA)
            {
                throw new IllegalStateException("tokens must begin with BEGIN_VAR_DATA: token=" + varDataToken);
            }

            if (at_least_one[0])
            {
                sb.append(indent).append("println(io)\n");
            }
            at_least_one[0] = true;

            final String characterEncoding = varData.get(i + 3).encoding().characterEncoding();
            sb.append(indent).append("print(io, \"").append(varDataToken.name()).append(": \")\n");

            if (null == characterEncoding)
            {
                final String skipFunction = "skip_" + formatPropertyName(varDataToken.name()) + "!(writer)";

                sb.append(indent).append("print(io, ").append(skipFunction).append(")\n")
                    .append(INDENT).append("print(io, \" bytes of raw data\")\n");
            }
            else
            {
                addModuleToUsing("StringViews");
                final String fieldName = formatPropertyName(varDataToken.name()) + "(StringView, writer)";

                sb.append(indent).append("println(io, ").append(fieldName).append(")").append("\n\n");
            }

            i += varDataToken.componentTokenCount();
        }

        return sb;
    }

    private void writeTokenDisplay(
        final StringBuilder sb,
        final String fieldTokenName,
        final Token typeToken,
        final boolean[] at_least_one,
        final String indent)
    {
        if (typeToken.encodedLength() <= 0 || typeToken.isConstantEncoding())
        {
            return;
        }

        if (at_least_one[0])
        {
            sb.append(indent).append("println(io)\n");
        }
        else
        {
            at_least_one[0] = true;
        }

        sb.append(indent).append("print(io, \"").append(fieldTokenName).append(": \")\n");
        final String fieldName = formatPropertyName(fieldTokenName) + "(writer)";

        switch (typeToken.signal())
        {
            case ENCODING:
                if (typeToken.encoding().primitiveType() == PrimitiveType.CHAR)
                {
                    addModuleToUsing("StringViews");
                    sb.append(indent).append("print(io, \"\\\"\")\n");
                    sb.append(indent).append("print(io, " + formatPropertyName(fieldTokenName) + "(StringView, writer))\n");
                    sb.append(indent).append("print(io, \"\\\"\")\n");
                }
                else
                {
                    sb.append(indent).append("print(io, " + fieldName + ")").append("\n");
                }
                break;

            case BEGIN_ENUM:
                sb.append(indent).append("print_fields(io, ").append(fieldName).append(")\n");
                break;

            case BEGIN_SET:
            case BEGIN_COMPOSITE:
                if (0 == typeToken.version())
                {
                    sb.append(indent).append("print_fields(io, ").append(fieldName).append(")\n");
                }
                else
                {
                    new Formatter(sb).format(
                        indent + "if %1$s_in_acting_version(writer)\n" +
                        indent + "    print_fields(io, %2$s)\n" +
                        indent + "end\n",
                        formatPropertyName(fieldTokenName),
                        fieldName);
                }
                break;

            default:
                break;
        }

        sb.append('\n');
    }


    private CharSequence generateChoicesDisplay(
        final String name,
        final List<Token> tokens)
    {
        final StringBuilder sb = new StringBuilder();
        final List<Token> choiceTokens = new ArrayList<>();

        collect(Signal.CHOICE, tokens, 0, choiceTokens);

        new Formatter(sb).format("\n" +
            "function print_fields(io::IO, writer::%1$s)\n",
            name);

        sb.append("    print(io, \"[\")").append("\n");

        if (choiceTokens.size() > 1)
        {
            sb.append("    at_least_one = false\n");
        }

        for (int i = 0, size = choiceTokens.size(); i < size; i++)
        {
            final Token token = choiceTokens.get(i);
            final String propertyString = formatPropertyName(token.name());

            sb.append("    if ").append(propertyString).append("(writer)").append("\n");

            if (i > 0)
            {
                sb.append(
                    "        if at_least_one\n" +
                    "            print(io, \", \")\n" +
                    "        end\n");
            }
            sb.append("        print(io, \"\\\"").append(propertyString).append("\\\"\")\n");

            if (i < (size - 1))
            {
                sb.append("        at_least_one = true\n");
            }
            sb.append("    end\n");
        }

        sb.append("    print(io, \"]\")").append("\n");
        sb.append("end\n");

        return sb;
    }

    private CharSequence generateEnumDisplay(
        final List<Token> tokens,
        final Token encodingToken)
    {
        final String enumName = formatStructName(encodingToken.applicableTypeName());
        final StringBuilder sb = new StringBuilder();

        new Formatter(sb).format("\n" +
            "function print_fields(io::IO, value::%1$s)\n",
            enumName);

        for (final Token token : tokens)
        {
            new Formatter(sb).format(
                "    value == %2$s_%1$s && return print(io, \"%1$s\")\n",
                token.name(),
                enumName);
        }

        sb.append("    value == ").append(enumName).append("_NULL_VALUE && return print(io, \"NULL_VALUE\")").append("\n\n");

        new Formatter(sb).format(
            "    throw(ArgumentError(lazy\"invalid value for Enum %1$s: $value\"))\n" +
            "end\n\n",
            enumName);

        return sb;
    }

    private CharSequence generateMessageLength(
        final List<Token> groups,
        final List<Token> varData,
        final String indent,
        final String structName)
    {
        final StringBuilder sbSkip = new StringBuilder();

        for (int i = 0, size = groups.size(); i < size; i++)
        {
            final Token groupToken = groups.get(i);

            if (groupToken.signal() != Signal.BEGIN_GROUP)
            {
                throw new IllegalStateException("tokens must begin with BEGIN_GROUP: token=" + groupToken);
            }

            final int endSignal = findEndSignal(groups, i, Signal.END_GROUP, groupToken.name());

            new Formatter(sbSkip).format(
                indent + ("    for group in %1$s(m)\n") +
                indent + ("        skip!(group)\n") +
                indent + ("    end\n"),
                formatPropertyName(groupToken.name()));

            i = endSignal;
        }

        for (int i = 0, size = varData.size(); i < size; )
        {
            final Token varDataToken = varData.get(i);

            if (varDataToken.signal() != Signal.BEGIN_VAR_DATA)
            {
                throw new IllegalStateException("tokens must begin with BEGIN_VAR_DATA: token=" + varDataToken);
            }

            new Formatter(sbSkip).format(
                indent + "    skip_%1$s!(m)\n",
                formatPropertyName(varDataToken.name()));

            i += varDataToken.componentTokenCount();
        }

        final StringBuilder sb = new StringBuilder();

        new Formatter(sb).format("\n" +
            indent + "@inline function skip!(m::%1$s)\n" +
            sbSkip +
            indent + "    return\n" +
            indent + "end\n\n",
            structName);

        return sb;
    }
}
