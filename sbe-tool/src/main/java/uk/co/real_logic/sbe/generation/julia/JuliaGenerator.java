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
import static uk.co.real_logic.sbe.generation.julia.JuliaUtil.formatPropertyName;
import static uk.co.real_logic.sbe.generation.julia.JuliaUtil.formatStructName;
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
        final String indent)
    {
        new Formatter(sb).format("\n" +
            indent + "abstract type %1$s{T} end\n\n" +
            indent + "mutable struct %1$sDecoder{T<:AbstractArray{UInt8}} <: %1$s{T}\n" +
            indent + "    const buffer::T\n" +
            indent + "    offset::Int64\n" +
            indent + "    const position_ptr::Base.RefValue{Int64}\n" +
            indent + "    const block_length::UInt16\n" +
            indent + "    const acting_version::UInt16\n" +
            indent + "    const count::UInt16\n" +
            indent + "    index::UInt16\n" +
            indent + "    function %1$sDecoder(buffer::T, offset::Int64, position_ptr::Base.RefValue{Int64},\n" +
            indent + "        block_length::Integer, acting_version::Integer,\n" +
            indent + "        count::Integer, index::Integer) where {T}\n" +
            indent + "        new{T}(buffer, offset, position_ptr, block_length, acting_version, count, index)\n" +
            indent + "    end\n" +
            indent + "end\n\n" +
            indent + "mutable struct %1$sEncoder{T<:AbstractArray{UInt8}} <: %1$s{T}\n" +
            indent + "    const buffer::T\n" +
            indent + "    offset::Int64\n" +
            indent + "    const position_ptr::Base.RefValue{Int64}\n" +
            indent + "    const initial_position::Int64\n" +
            indent + "    const count::UInt16\n" +
            indent + "    index::UInt16\n" +
            indent + "    function %1$sEncoder(buffer::T, offset::Int64, position_ptr::Base.RefValue{Int64},\n" +
            indent + "        initial_position::Int64, count::Integer, index::Integer) where {T}\n" +
            indent + "        new{T}(buffer, offset, position_ptr, initial_position, count, index)\n" +
            indent + "    end\n" +
            indent + "end\n",
            groupStructName);
    }

    private void generateGroupMethods(
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

        new Formatter(sb).format("\n" +
            indent + "@inline function %1$sDecoder(buffer, position_ptr, acting_version)\n" +
            indent + "    dimensions = %2$s(buffer, position_ptr[])\n" +
            indent + "    position_ptr[] += %3$d\n" +
            indent + "    return %1$sDecoder(buffer, 0, position_ptr, blockLength(dimensions),\n" +
            indent + "        acting_version, numInGroup(dimensions), 0)\n" +
            indent + "end\n",
            groupStructName,
            dimensionsStructName,
            dimensionHeaderLength);

        final long minCount = numInGroupToken.encoding().applicableMinValue().longValue();
        final String minCheck = minCount > 0 ? "count < " + minCount + " || " : "";

        new Formatter(sb).format("\n" +
            indent + "@inline function %1$sEncoder(buffer, count, position_ptr)\n" +
            indent + "    if %2$scount > %3$d\n" +
            indent + "        error(\"count outside of allowed range\")\n" +
            indent + "    end\n" +
            indent + "    dimensions = %4$s(buffer, position_ptr[])\n" +
            indent + "    blockLength!(dimensions, %5$s)\n" +
            indent + "    numInGroup!(dimensions, count)\n" +
            indent + "    initial_position = position_ptr[]\n" +
            indent + "    position_ptr[] += %6$d\n" +
            indent + "    return %1$sEncoder(buffer, 0, position_ptr, initial_position, count, 0)\n" +
            indent + "end\n",
            groupStructName,
            minCheck,
            numInGroupToken.encoding().applicableMaxValue().longValue(),
            dimensionsStructName,
            generateLiteral(ir.headerStructure().blockLengthType(), Integer.toString(blockLength)),
            dimensionHeaderLength);

        new Formatter(sb).format("\n" +
            indent + "sbe_header_size(::%3$s) = %1$d\n" +
            indent + "sbe_block_length(::%3$s) = %2$s\n" +
            indent + "sbe_acting_block_length(g::%3$sDecoder) = g.block_length\n" +
            indent + "sbe_acting_block_length(g::%3$sEncoder) = %2$s\n" +
            indent + "sbe_acting_version(g::%3$sDecoder) = g.acting_version\n" +
            indent + "sbe_position(g::%3$s) = g.position_ptr[]\n" +
            indent + "@inline sbe_position!(g::%3$s, position) = g.position_ptr[] = position\n" +
            indent + "sbe_position_ptr(g::%3$s) = g.position_ptr\n" +
            indent + "@inline function next!(g::%3$s)\n" +
            indent + "    if g.index >= g.count\n" +
            indent + "        error(\"index >= count\")\n" +
            indent + "    end\n" +
            indent + "    g.offset = sbe_position(g)\n" +
            indent + "    sbe_position!(g, g.offset + sbe_acting_block_length(g))\n" +
            indent + "    g.index += 1\n" +
            indent + "    return g\n" +
            indent + "end\n" +
            indent + "function Base.iterate(g::%3$s, state=nothing)\n" +
            indent + "    if g.index < g.count\n" +
            indent + "        g.offset = sbe_position(g)\n" +
            indent + "        sbe_position!(g, g.offset + sbe_acting_block_length(g))\n" +
            indent + "        g.index += 1\n" +
            indent + "        return g, state\n" +
            indent + "    else\n" +
            indent + "        return nothing\n" +
            indent + "    end\n" +
            indent + "end\n" +
            indent + "Base.eltype(::Type{<:%3$s}) = %3$s\n" +
            indent + "Base.isdone(g::%3$s, state=nothing) = g.index >= g.count\n" +
            indent + "Base.length(g::%3$s) = g.count\n",
            dimensionHeaderLength,
            generateLiteral(ir.headerStructure().blockLengthType(), Integer.toString(blockLength)),
            groupStructName);

        new Formatter(sb).format("\n" +
            indent + "function reset_count_to_index!(g::%1$sEncoder)\n" +
            indent + "    g.count = g.index\n" +
            indent + "    dimensions = %2$s(g.buffer, g.initial_position)\n" +
            indent + "    numInGroup!(dimensions, g.count)\n" +
            indent + "    return g.count\n" +
            indent + "end\n",
            groupStructName,
            dimensionsStructName);
    }

    private void generateGroupProperty(
        final StringBuilder sb,
        final String groupName,
        final String outerStructName,
        final Token token,
        final String indent)
    {
        final String propertyName = formatPropertyName(groupName);
        final String groupStructName = formatStructName(groupName);
        final int version = token.version();
        final int id = token.id();

        if (version > 0)
        {
            new Formatter(sb).format("\n" +
                indent + "function %2$s(m::%1$s)\n" +
                indent + "    if sbe_acting_version(m) < %3$d\n" +
                indent + "        return %4$sDecoder(m.buffer, 0, sbe_position_ptr(m), 0\n" +
                indent + "            sbe_acting_version(m), 0, 0)\n" +
                indent + "    end\n" +
                indent + "    return %4$sDecoder(m.buffer, sbe_position_ptr(m), sbe_acting_version(m))\n" +
                indent + "end\n",
                outerStructName,
                propertyName,
                version,
                groupStructName);
        }
        else
        {
            new Formatter(sb).format("\n" +
                indent + "@inline function %2$s(m::%1$s)\n" +
                indent + "    return %3$sDecoder(m.buffer, sbe_position_ptr(m), sbe_acting_version(m))\n" +
                indent + "end\n",
                outerStructName,
                propertyName,
                groupStructName);
        }

        new Formatter(sb).format("\n" +
            indent + "@inline function %2$s!(m::%1$s, count)\n" +
            indent + "    return %3$sEncoder(m.buffer, count, sbe_position_ptr(m))\n" +
            indent + "end\n" +
            indent + "%2$s_group_count!(m::%1$sEncoder, count) = %2$s!(m, count)\n",
            outerStructName,
            propertyName,
            groupStructName);

        new Formatter(sb).format(
            indent + "%1$s_id(::%3$s) = %2$s\n",
            propertyName,
            id,
            outerStructName);

        new Formatter(sb).format(
            indent + "%1$s_since_version(::%3$s) = %2$s\n" +
            indent + "%1$s_in_acting_version(m::%3$s) = sbe_acting_version(m) >= %2$s\n",
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
            "    sbe_acting_version(m) < %1$d && return false\n",
            sinceVersion);
    }

    private static CharSequence generateArrayFieldNotPresentCondition(
        final int sinceVersion, final String typeName, final String indent)
    {
        if (0 == sinceVersion)
        {
            return "";
        }

        return String.format(
            indent + "    sbe_acting_version(m) < %1$d && return %2$s[]\n",
            sinceVersion,
            typeName);
    }

    private static CharSequence generateArrayLengthNotPresentCondition(
        final int sinceVersion, final String indent)
    {
        if (0 == sinceVersion)
        {
            return "";
        }

        return String.format(
            indent + "    sbe_acting_version(m) < %1$d && return 0\n",
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
            indent + "    sbe_acting_version(m) < %1$d && return T[]\n",
            sinceVersion);
    }

    private static CharSequence generateTypeFieldNotPresentCondition(
        final int sinceVersion, final String typeName, final String indent)
    {
        if (0 == sinceVersion)
        {
            return "";
        }

        return String.format(
            indent + "    sbe_acting_version(m) < %1$d && return %2$s[]\n",
            sinceVersion,
            typeName);
    }

    private void generateEnumDeclaration(
        final StringBuilder sb,
        final String name,
        final Token encodingToken)
    {
        final Encoding encoding = encodingToken.encoding();
        addModuleToUsing("EnumX");
        sb.append("@enumx T=SbeEnum " + name + "::" + juliaTypeName(encoding.primitiveType()) + " begin\n");
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
                indent + "    meta_attribute === :epoch && return Symbol(\"%1$s\")\n",
                epoch);
        }

        if (!Strings.isEmpty(timeUnit))
        {
            new Formatter(sb).format(
                indent + "    meta_attribute === :time_unit && return Symbol(\"%1$s\")\n",
                timeUnit);
        }

        if (!Strings.isEmpty(semanticType))
        {
            new Formatter(sb).format(
                indent + "    meta_attribute === :semantic_type && return Symbol(\"%1$s\")\n",
                semanticType);
        }

        new Formatter(sb).format(
            indent + "    meta_attribute === :presence && return Symbol(\"%1$s\")\n",
            encoding.presence().toString().toLowerCase());

        new Formatter(sb).format(
            indent + "    return Symbol(\"\")\n" +
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
            indent + "    sbe_acting_version(m) < %1$d && return %2$s.NULL_VALUE\n",
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
        final int size = token.encoding().primitiveType().size();

        new Formatter(sb).format(
            indent + "%2$s(m::%4$s) = %1$s(m.buffer, m.offset + %3$d)\n",
            bitsetName,
            propertyName,
            offset,
            structName);

        new Formatter(sb).format(
            indent + "%1$s_encoding_length(::%3$s) = %2$d\n",
            propertyName,
            size,
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
            indent + "%2$s(m::%4$s) = %1$s(m.buffer, m.offset + %3$d, sbe_acting_version(m))\n",
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

                generateMessageFlyweightStruct(sb, structName, fields, groups, msgToken);
                generateMessageFlyweightMethods(sb, structName, msgToken);

                generateFields(sb, structName, fields, BASE_INDENT);
                generateGroups(sb, structName, groups, BASE_INDENT);
                generateVarData(sb, structName, varData, BASE_INDENT);
                generateDisplay(sb, structName, fields, groups, varData, INDENT);
                generateMessageLength(sb, true, groups, varData, BASE_INDENT, structName);

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
            final StringBuilder sb = new StringBuilder();

            sb.append(
                "@inline function encode_le(::Type{T}, buffer, offset, value) where {T}\n" +
                "    @inbounds reinterpret(T, view(buffer, offset+1:offset+sizeof(T)))[] = htol(value)\n" +
                "end\n\n" +
                "@inline function encode_be(::Type{T}, buffer, offset, value) where {T}\n" +
                "    @inbounds reinterpret(T, view(buffer, offset+1:offset+sizeof(T)))[] = hton(value)\n" +
                "end\n\n" +
                "@inline function decode_le(::Type{T}, buffer, offset) where {T}\n" +
                "    @inbounds ltoh(reinterpret(T, view(buffer, offset+1:offset+sizeof(T)))[])\n" +
                "end\n\n" +
                "@inline function decode_be(::Type{T}, buffer, offset) where {T}\n" +
                "    @inbounds ntoh(reinterpret(T, view(buffer, offset+1:offset+sizeof(T)))[])\n" +
                "end\n\n" +
                "@inline function rstrip_nul(a::AbstractVector{UInt8})\n" +
                "    pos = findfirst(iszero, a)\n" +
                "    len = pos !== nothing ? pos - 1 : Base.length(a)\n" +
                "    return view(a, 1:len)\n" +
                "end\n\n");

            out.append(generateFileHeader());
            out.append(sb);
        }
    }

    private void generateGroups(
        final StringBuilder sb,
        final String outerStructName,
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

            generateGroupStruct(sb, groupStructName, indent);
            generateGroupMethods(sb, groupStructName, tokens, groupStart, indent);
            generateFields(sb, groupStructName, fields, indent);
            generateGroups(sb, groupStructName, groups, indent);
            generateVarData(sb, groupStructName, varData, indent);

            generateGroupDisplay(sb, groupStructName, fields, groups, varData, indent);
            generateMessageLength(sb, false, groups, varData, indent, groupStructName);

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
            final int id = token.id();

            generateFieldMetaAttributeMethod(sb, token, indent, structName);

            new Formatter(sb).format("\n" +
                indent + "%1$s_character_encoding(::%3$s) = \"%2$s\"\n",
                propertyName,
                characterEncoding,
                structName);

            new Formatter(sb).format(
                indent + "%1$s_in_acting_version(m::%4$s) = sbe_acting_version(m) >= %2$d\n" +
                indent + "%1$s_id(::%4$s) = %3$d\n",
                propertyName,
                version,
                id,
                structName);

            new Formatter(sb).format(
                indent + "%1$s_header_length(::%3$s) = %2$d\n",
                propertyName,
                lengthOfLengthField,
                structName);

            new Formatter(sb).format("\n" +
                indent + "@inline function %1$s_length(m::%4$s)\n" +
                "%2$s" +
                indent + "    return %3$s\n" +
                indent + "end\n",
                propertyName,
                generateArrayLengthNotPresentCondition(version, BASE_INDENT),
                generateGet(lengthToken, "m.buffer", "sbe_position(m)"),
                structName);

            new Formatter(sb).format("\n" +
                indent + "@inline function %1$s_length!(m::%5$sEncoder, n)\n" +
                "%2$s" +
                indent + "    if !checkbounds(Bool, m.buffer, sbe_position(m) + 1 + %4$d + n)\n" +
                indent + "        error(\"buffer too short for data length\")\n" +
                indent + "    elseif n > %6$d\n" +
                indent + "        error(\"data length too large for length type\")\n" +
                indent + "    end\n" +
                indent + "    return %3$s\n" +
                indent + "end\n",
                propertyName,
                generateArrayLengthNotPresentCondition(version, BASE_INDENT),
                generatePut(lengthToken, "m.buffer", "sbe_position(m)", "n"),
                lengthOfLengthField,
                structName,
                lengthToken.encoding().applicableMaxValue().longValue());

            new Formatter(sb).format("\n" +
                indent + "@inline function skip_%1$s!(m::%4$s)\n" +
                "%2$s" +
                indent + "    len = %1$s_length(m)\n" +
                indent + "    pos = sbe_position(m) + %3$d\n" +
                indent + "    sbe_position!(m, pos + len)\n" +
                indent + "    return len\n" +
                indent + "end\n",
                propertyName,
                generateArrayLengthNotPresentCondition(version, BASE_INDENT),
                lengthOfLengthField,
                structName);

            new Formatter(sb).format("\n" +
                indent + "@inline function %1$s(m::%6$sDecoder)\n" +
                "%2$s" +
                indent + "    len = %1$s_length(m)\n" +
                indent + "    pos = sbe_position(m) + %3$d\n" +
                indent + "    sbe_position!(m, pos + len)\n" +
                indent + "    return view(m.buffer, pos+1:pos+len)\n" +
                indent + "end\n",
                propertyName,
                generateTypeFieldNotPresentCondition(version, "T", indent),
                lengthOfLengthField,
                lengthJuliaType,
                varDataJuliaType,
                structName);

            if (null != characterEncoding)
            {
                addModuleToUsing("StringViews");
                new Formatter(sb).format("\n" +
                    indent + "%1$s(T::Type{<:AbstractString}, m::%2$s) = T(rstrip_nul(%1$s(m)))\n",
                    propertyName,
                    structName);
            }

            new Formatter(sb).format("\n" +
                indent + "@inline function %1$s!(m::%6$sEncoder, len::Int64)\n" +
                "%2$s" +
                indent + "    %1$s_length!(m, len)\n" +
                indent + "    pos = sbe_position(m) + %3$d\n" +
                indent + "    sbe_position!(m, pos + len)\n" +
                indent + "    return view(m.buffer, pos+1:pos+len)\n" +
                indent + "end\n",
                propertyName,
                generateTypeFieldNotPresentCondition(version, "T", indent),
                lengthOfLengthField,
                lengthJuliaType,
                varDataJuliaType,
                structName);

            new Formatter(sb).format("\n" +
                indent + "@inline function %1$s!(m::%6$sEncoder, src)\n" +
                "%2$s" +
                indent + "    len = Base.length(src)\n" +
                indent + "    %1$s_length!(m, len)\n" +
                indent + "    pos = sbe_position(m) + %3$d\n" +
                indent + "    sbe_position!(m, pos + len)\n" +
                indent + "    dest = view(m.buffer, pos+1:pos+len)\n" +
                indent + "    copyto!(dest, src)\n" +
                indent + "end\n",
                propertyName,
                generateTypeFieldNotPresentCondition(version, "T", indent),
                lengthOfLengthField,
                lengthJuliaType,
                varDataJuliaType,
                structName);

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

            generateFixedFlyweightStruct(out, choiceName, tokens);
            generateFixedFlyweightMethods(out, choiceName, tokens);

            final Encoding encoding = token.encoding();
            new Formatter(out).format("\n" +
                "@inline clear!(m::%1$sEncoder) = %2$s\n",
                choiceName,
                generatePut(token, "m.buffer", "m.offset", "0"));

            new Formatter(out).format(
                "@inline is_empty(m::%1$sDecoder) = %2$s == 0\n",
                choiceName,
                generateGet(token, "m.buffer", "m.offset"));

            new Formatter(out).format(
                "@inline raw_value(m::%1$sDecoder) = %2$s\n",
                choiceName,
                generateGet(token, "m.buffer", "m.offset"));

            generateChoices(out, choiceName, tokens.subList(1, tokens.size() - 1));
            generateChoicesDisplay(out, choiceName, tokens.subList(1, tokens.size() - 1));
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

            generateEnumDeclaration(out, enumName, enumToken);
            generateEnumValues(out, tokens.subList(1, tokens.size() - 1), enumToken);

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

            generateFixedFlyweightStruct(out, compositeName, tokens);
            generateFixedFlyweightMethods(out, compositeName, tokens);
            generateCompositePropertyElements(out, compositeName, tokens.subList(1, tokens.size() - 1), BASE_INDENT);
            generateCompositeDisplay(out, tokens.get(0).applicableTypeName(), tokens.subList(1, tokens.size() - 1));

            fileOut.append(generateFileHeader());
            fileOut.append(out);
        }
    }

    private void generateChoices(
        final StringBuilder sb,
        final String bitsetStructName,
        final List<Token> tokens)
    {
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
                    "@inline function %2$s(m::%1$sDecoder)\n" +
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

                new Formatter(sb).format("\n" +
                    "@inline function %2$s!(m::%1$sEncoder, value::Bool)\n" +
                    "    bits = %7$s\n" +
                    "    bits = value ? (bits | (%6$s << %5$s)) : (bits & ~(%6$s << %5$s))\n" +
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
    }

    private void generateEnumValues(
        final StringBuilder sb,
        final List<Token> tokens,
        final Token encodingToken)
    {
        final Encoding encoding = encodingToken.encoding();

        for (final Token token : tokens)
        {
            final CharSequence constVal = generateLiteral(
                token.encoding().primitiveType(), token.encoding().constValue().toString());

            new Formatter(sb).format(
                "    %1$s = %2$s\n",
                token.name(),
                constVal);
        }

        final CharSequence nullLiteral = generateLiteral(
            encoding.primitiveType(), encoding.applicableNullValue().toString());

        new Formatter(sb).format(
            "    NULL_VALUE = %1$s\n",
            nullLiteral);

        sb.append("end\n");
    }

    private CharSequence generateFieldNotPresentCondition(
        final int sinceVersion,
        final Encoding encoding,
        final String indent)
    {
        if (0 == sinceVersion)
        {
            return "";
        }

        return String.format(
            "    sbe_acting_version(m) < %1$d && return %2$s\n",
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

        new Formatter(sb).format(
            "module %s\n\n",
            moduleName);

        if (modules != null && !modules.isEmpty())
        {
            for (final String module : modules)
            {
                new Formatter(sb).format(
                    "using %s\n",
                    module);
            }
            sb.append("\n");
        }

        for (final String incName : includes)
        {
            new Formatter(sb).format(
                "include(\"%s.jl\")\n",
                toUpperFirstChar(incName));
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

    private void generateCompositePropertyElements(
        final StringBuilder sb,
        final String containingStructName,
        final List<Token> tokens,
        final String indent)
    {
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
        final String byteOrderSuffix = token.encoding().byteOrder() == ByteOrder.BIG_ENDIAN ? "be" : "le";
        final String juliaTypeName = juliaTypeName(primitiveType);
        final StringBuilder sb = new StringBuilder();

        new Formatter(sb).format(
            "encode_%1$s(%2$s, %3$s, %4$s, %5$s)",
            byteOrderSuffix,
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
        final String byteOrderSuffix = token.encoding().byteOrder() == ByteOrder.BIG_ENDIAN ? "be" : "le";
        final String juliaTypeName = juliaTypeName(primitiveType);
        final StringBuilder sb = new StringBuilder();

        new Formatter(sb).format(
            "decode_%1$s(%2$s, %3$s, %4$s)",
            byteOrderSuffix,
            juliaTypeName,
            buffer,
            index);

        return sb;
    }

    private CharSequence generateGetArrayReadWrite(
        final Token token,
        final String typeName,
        final String buffer,
        final String index,
        final String length)
    {
        final ByteOrder byteOrder = token.encoding().byteOrder();
        final StringBuilder sb = new StringBuilder();

        addModuleToUsing("MappedArrays");
        new Formatter(sb).format(
            "mappedarray(%1$s, %2$s, reinterpret(%3$s, view(%4$s, %5$s+1:%5$s+%6$s)))",
            byteOrder == ByteOrder.BIG_ENDIAN ? "ntoh" : "ltoh",
            byteOrder == ByteOrder.BIG_ENDIAN ? "hton" : "htol",
            typeName,
            buffer,
            index,
            length);

        return sb;
    }

    private CharSequence generateGetArrayReadOnly(
        final Token token,
        final String typeName,
        final String buffer,
        final String index,
        final String length)
    {
        final ByteOrder byteOrder = token.encoding().byteOrder();
        final StringBuilder sb = new StringBuilder();

        addModuleToUsing("MappedArrays");
        new Formatter(sb).format(
            "mappedarray(%1$s, reinterpret(%2$s, view(%3$s, %4$s+1:%4$s+%5$s)))",
            byteOrder == ByteOrder.BIG_ENDIAN ? "ntoh" : "ltoh",
            typeName,
            buffer,
            index,
            length);

        return sb;
    }

    private CharSequence generateGetArrayReadOnlyStatic(
        final Token token,
        final String typeName,
        final String buffer,
        final String index,
        final String length)
    {
        final ByteOrder byteOrder = token.encoding().byteOrder();
        final StringBuilder sb = new StringBuilder();

        addModuleToUsing("MappedArrays");
        new Formatter(sb).format(
            "mappedarray(%1$s, reinterpret(%2$s, view(%3$s, %4$s+1:%4$s+%5$s))[])",
            byteOrder == ByteOrder.BIG_ENDIAN ? "ntoh" : "ltoh",
            typeName,
            buffer,
            index,
            length);

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
            indent + "@inline function %2$s(m::%1$sDecoder)\n" +
            "%4$s" +
            indent + "    return %5$s\n" +
            indent + "end\n",
            containingStructName,
            propertyName,
            juliaTypeName,
            generateFieldNotPresentCondition(propertyToken.version(), encodingToken.encoding(), indent),
            generateGet(encodingToken, "m.buffer", "m.offset + " + Integer.toString(offset)));

        new Formatter(sb).format(
            indent + "@inline %2$s!(m::%1$sEncoder, value) = %3$s\n",
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

        new Formatter(sb).format(
            indent + "%1$s_eltype(::%3$s) = %2$s\n",
            propertyName,
            juliaTypeName,
            formatStructName(containingStructName));

        new Formatter(sb).format("\n" +
            indent + "@inline function %1$s(m::%2$sDecoder)\n" +
            indent + "%3$s" +
            indent + "    return %4$s\n" +
            indent + "end\n",
            propertyName,
            formatStructName(containingStructName),
            generateArrayFieldNotPresentCondition(propertyToken.version(), juliaTypeName, indent),
            generateGetArrayReadOnly(
            encodingToken,
            juliaTypeName,
            "m.buffer",
            "m.offset+" + Integer.toString(offset),
            "sizeof(" + juliaTypeName + ")*" + Integer.toString(arrayLength)));

        // Only use StaticArrays for arrays of less than 100 elements
        if (arrayLength < 100)
        {
            addModuleToUsing("StaticArrays");
            new Formatter(sb).format("\n" +
                indent + "@inline function %1$s(::Type{<:SVector},m::%2$sDecoder)\n" +
                indent + "%3$s" +
                indent + "    return %4$s\n" +
                indent + "end\n",
                propertyName,
                formatStructName(containingStructName),
                generateArrayFieldNotPresentCondition(propertyToken.version(), juliaTypeName, indent),
                generateGetArrayReadOnlyStatic(encodingToken,
                "SVector{" + Integer.toString(arrayLength) + "," + juliaTypeName + "}",
                "m.buffer",
                "m.offset+" + Integer.toString(offset),
                "sizeof(" + juliaTypeName + ")*" + Integer.toString(arrayLength)));
        }

        if (encodingToken.encoding().primitiveType() == PrimitiveType.CHAR)
        {
            addModuleToUsing("StringViews");
            new Formatter(sb).format("\n" +
                indent + "@inline function %2$s(::Type{<:AbstractString}, m::%6$sDecoder)\n" +
                indent + "%4$s" +
                indent + "    value = view(m.buffer, m.offset+1+%5$d:m.offset+%5$d+sizeof(%1$s)*%3$d)\n" +
                indent + "    return StringView(rstrip_nul(value))\n" +
                indent + "end\n",
                juliaTypeName,
                propertyName,
                arrayLength,
                generateStringNotPresentCondition(propertyToken.version(), indent),
                offset,
                formatStructName(containingStructName));
        }

        new Formatter(sb).format("\n" +
            indent + "@inline function %1$s!(m::%2$sEncoder)\n" +
            indent + "%3$s" +
            indent + "    return %4$s\n" +
            indent + "end\n",
            propertyName,
            formatStructName(containingStructName),
            generateArrayFieldNotPresentCondition(propertyToken.version(), juliaTypeName, indent),
            generateGetArrayReadWrite(
            encodingToken,
            juliaTypeName,
            "m.buffer",
            "m.offset+" + Integer.toString(offset),
            "sizeof(" + juliaTypeName + ")*" + Integer.toString(arrayLength)));

        new Formatter(sb).format("\n" +
            indent + "@inline function %1$s!(m::%2$sEncoder, value)\n" +
            indent + "%3$s" +
            indent + "    copyto!(%4$s, value)\n" +
            indent + "end\n",
            propertyName,
            formatStructName(containingStructName),
            generateArrayFieldNotPresentCondition(propertyToken.version(), juliaTypeName, indent),
            generateGetArrayReadWrite(encodingToken,
            juliaTypeName,
            "m.buffer",
            "m.offset+" + Integer.toString(offset),
            "sizeof(" + juliaTypeName + ")*" + Integer.toString(arrayLength)));
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

    private void generateFixedFlyweightStruct(
        final StringBuilder sb,
        final String structName,
        final List<Token> fields)
    {
        final int size = fields.get(0).encodedLength();

        new Formatter(sb).format(
            "struct %1$s{T<:AbstractArray{UInt8}}\n" +
            "    buffer::T\n" +
            "    offset::Int64\n" +
            "    acting_version::UInt16\n" +
            "    function %1$s(buffer::T, offset::Int64=0, acting_version::Integer=0) where {T}\n" +
            "        new{T}(buffer, offset, acting_version)\n" +
            "    end\n" +
            "end\n" +
            "const %1$sDecoder = %1$s\n" +
            "const %1$sEncoder = %1$s\n",
            structName,
            size);
    }

    private void generateFixedFlyweightMethods(
        final StringBuilder sb,
        final String structName,
        final List<Token> fields)
    {
        final int size = fields.get(0).encodedLength();
        CharSequence sizeValue = generateLiteral(ir.headerStructure().blockLengthType(), Integer.toString(size));
        if (size == -1)
        {
            sizeValue = "typemax(UInt16)";
        }

        new Formatter(sb).format("\n" +
            "sbe_buffer(m::%1$s) = m.buffer\n" +
            "sbe_offset(m::%1$s) = m.offset\n" +
            "sbe_acting_version(m::%1$s) = m.acting_version\n",
            structName,
            size);

        new Formatter(sb).format(
            "sbe_encoded_length(::%1$s) = %2$s\n" +
            "sbe_encoded_length(::Type{<:%1$s}) = %2$s\n" +
            "sbe_schema_id(::%1$s) = %3$s\n" +
            "sbe_schema_id(::Type{<:%1$s}) = %3$s\n" +
            "sbe_schema_version(::%1$s) = %4$s\n" +
            "sbe_schema_version(::Type{<:%1$s}) = %4$s\n",
            structName,
            sizeValue,
            generateLiteral(ir.headerStructure().schemaIdType(), Integer.toString(ir.id())),
            generateLiteral(ir.headerStructure().schemaVersionType(), Integer.toString(ir.version())));
    }

    private void generateMessageFlyweightStruct(
        final StringBuilder sb,
        final String structName,
        final List<Token> fields,
        final List<Token> groups,
        final Token token)
    {
        new Formatter(sb).format(
            "abstract type %1$s{T} end\n\n" +
            "struct %1$sDecoder{T<:AbstractArray{UInt8}} <: %1$s{T}\n" +
            "    buffer::T\n" +
            "    offset::Int64\n" +
            "    position_ptr::Base.RefValue{Int64}\n" +
            "    acting_block_length::UInt16\n" +
            "    acting_version::UInt16\n" +
            "    function %1$sDecoder(buffer::T, offset::Int64, position_ptr::Base.RefValue{Int64},\n" +
            "        acting_block_length::Integer, acting_version::Integer) where {T}\n" +
            "        position_ptr[] = offset + acting_block_length\n" +
            "        new{T}(buffer, offset, position_ptr, acting_block_length, acting_version)\n" +
            "    end\n" +
            "end\n\n" +
            "struct %1$sEncoder{T<:AbstractArray{UInt8}} <: %1$s{T}\n" +
            "    buffer::T\n" +
            "    offset::Int64\n" +
            "    position_ptr::Base.RefValue{Int64}\n" +
            "    function %1$sEncoder(buffer::T, offset::Int64, position_ptr::Base.RefValue{Int64}) where {T}\n" +
            "        position_ptr[] = offset + %2$d\n" +
            "        new{T}(buffer, offset, position_ptr)\n" +
            "    end\n" +
            "end\n",
            structName,
            token.encodedLength());
    }

    private void generateMessageFlyweightMethods(
        final StringBuilder sb,
        final String structName,
        final Token token)
    {
        final String semanticType = token.encoding().semanticType() == null ? "" : token.encoding().semanticType();
        final String semanticVersion = ir.semanticVersion() == null ? "" : ir.semanticVersion();

        new Formatter(sb).format("\n" +
            "function %1$sDecoder(buffer::AbstractArray, offset::Int64, position_ptr::Base.RefValue{Int64},\n" +
            "    hdr::MessageHeader)\n" +
            "    if templateId(hdr) != %3$s || schemaId(hdr) != %4$s\n" +
            "        error(\"Template id or schema id mismatch\")\n" +
            "    end\n" +
            "    %1$sDecoder(buffer, offset + sbe_encoded_length(hdr), position_ptr,\n" +
            "        blockLength(hdr), version(hdr))\n" +
            "end\n" +
            "function %1$sDecoder(buffer::AbstractArray, position_ptr::Base.RefValue{Int64},\n" +
            "    hdr::MessageHeader)\n" +
            "    %1$sDecoder(buffer, 0, position_ptr, hdr)\n" +
            "end\n" +
            "function %1$sDecoder(buffer::AbstractArray, offset::Int64,\n" +
            "    acting_block_length::Integer, acting_version::Integer)\n" +
            "    %1$sDecoder(buffer, offset, Ref(0), acting_block_length, acting_version)\n" +
            "end\n" +
            "function %1$sDecoder(buffer::AbstractArray, offset::Int64, hdr::MessageHeader)\n" +
            "    %1$sDecoder(buffer, offset, Ref(0), hdr)\n" +
            "end\n" +
            "%1$sDecoder(buffer::AbstractArray, hdr::MessageHeader) = %1$sDecoder(buffer, 0, Ref(0), hdr)\n" +

            "function %1$sEncoder(buffer::AbstractArray, position_ptr::Base.RefValue{Int64})\n" +
            "    %1$sEncoder(buffer, 0, position_ptr)\n" +
            "end\n" +
            "function %1$sEncoder(buffer::AbstractArray, offset::Int64, position_ptr::Base.RefValue{Int64},\n" +
            "    hdr::MessageHeader)\n" +
            "    blockLength!(hdr, %2$s)\n" +
            "    templateId!(hdr, %3$s)\n" +
            "    schemaId!(hdr, %4$s)\n" +
            "    version!(hdr, %5$s)\n" +
            "    %1$sEncoder(buffer, offset + sbe_encoded_length(hdr), position_ptr)\n" +
            "end\n" +
            "function %1$sEncoder(buffer::AbstractArray, position_ptr::Base.RefValue{Int64}, hdr::MessageHeader)\n" +
            "    %1$sEncoder(buffer, 0, position_ptr, hdr)\n" +
            "end\n" +
            "function %1$sEncoder(buffer::AbstractArray, offset::Int64, hdr::MessageHeader)\n" +
            "    %1$sEncoder(buffer, offset, Ref(0), hdr)\n" +
            "end\n" +
            "function %1$sEncoder(buffer::AbstractArray, hdr::MessageHeader)\n" +
            "    %1$sEncoder(buffer, 0, Ref(0), hdr)\n" +
            "end\n" +
            "%1$sEncoder(buffer::AbstractArray, offset::Int64=0) = %1$sEncoder(buffer, offset, Ref(0))\n" +

            "sbe_buffer(m::%1$s) = m.buffer\n" +
            "sbe_offset(m::%1$s) = m.offset\n" +
            "sbe_position_ptr(m::%1$s) = m.position_ptr\n" +
            "sbe_position(m::%1$s) = m.position_ptr[]\n" +
            "sbe_position!(m::%1$s, position) = m.position_ptr[] = position\n" +
            "sbe_block_length(::%1$s) = %2$s\n" +
            "sbe_block_length(::Type{<:%1$s}) = %2$s\n" +
            "sbe_template_id(::%1$s) = %3$s\n" +
            "sbe_template_id(::Type{<:%1$s})  = %3$s\n" +
            "sbe_schema_id(::%1$s) = %4$s\n" +
            "sbe_schema_id(::Type{<:%1$s})  = %4$s\n" +
            "sbe_schema_version(::%1$s) = %5$s\n" +
            "sbe_schema_version(::Type{<:%1$s})  = %5$s\n" +
            "sbe_semantic_type(::%1$s) = \"%6$s\"\n" +
            "sbe_semantic_version(::%1$s) = \"%7$s\"\n" +
            "sbe_acting_block_length(m::%1$sDecoder) = m.acting_block_length\n" +
            "sbe_acting_block_length(::%1$sEncoder) = %2$s\n" +
            "sbe_acting_version(m::%1$sDecoder) = m.acting_version\n" +
            "sbe_acting_version(::%1$sEncoder) = %5$s\n" +
            "sbe_rewind!(m::%1$s) = sbe_position!(m, m.offset + sbe_acting_block_length(m))\n" +
            "sbe_encoded_length(m::%1$s) = sbe_position(m) - m.offset\n" +

            "@inline function sbe_decoded_length(m::%1$s)\n" +
            "    skipper = %1$sDecoder(sbe_buffer(m), sbe_offset(m),\n" +
            "        sbe_acting_block_length(m), sbe_acting_version(m))\n" +
            "    sbe_skip!(skipper)\n" +
            "    sbe_encoded_length(skipper)\n" +
            "end\n",
            structName,
            generateLiteral(ir.headerStructure().blockLengthType(), Integer.toString(token.encodedLength())),
            generateLiteral(ir.headerStructure().templateIdType(), Integer.toString(token.id())),
            generateLiteral(ir.headerStructure().schemaIdType(), Integer.toString(ir.id())),
            generateLiteral(ir.headerStructure().schemaVersionType(), Integer.toString(ir.version())),
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
                        generateEnumProperty(
                            sb, containingStructName, signalToken, propertyName, encodingToken, indent);
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
            indent + "%1$s_id(::%3$s) = %2$s\n",
            propertyName,
            generateLiteral(ir.headerStructure().schemaIdType(), Integer.toString(fieldToken.id())),
            structName);

        new Formatter(sb).format(
            indent + "%1$s_since_version(::%3$s) = %2$s\n" +
            indent + "%1$s_in_acting_version(m::%3$s) = sbe_acting_version(m) >= %2$s\n",
            propertyName,
            generateLiteral(ir.headerStructure().schemaVersionType(), Integer.toString(fieldToken.version())),
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
                indent + "%2$s(::Type{Integer}, ::%3$sDecoder) = %1$s(%5$s.%4$s)\n",
                typeName,
                propertyName,
                containingStructName,
                constValue.substring(constValue.indexOf(".") + 1),
                enumName);

            new Formatter(sb).format(
                indent + "@inline function %2$s(m::%1$sDecoder)\n" +
                "%3$s" +
                indent + "    return %5$s.%4$s\n" +
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
                indent + "@inline function %1$s(::Type{Integer}, m::%4$sDecoder)\n" +
                "%2$s" +
                indent + "    return %3$s\n" +
                indent + "end\n",
                propertyName,
                generateFieldNotPresentCondition(fieldToken.version(), encodingToken.encoding(), indent),
                generateGet(encodingToken, "m.buffer", "m.offset + " + offsetStr),
                containingStructName);

            new Formatter(sb).format(
                indent + "@inline function %2$s(m::%1$sDecoder)\n" +
                "%3$s" +
                indent + "    return %5$s.SbeEnum(%4$s)\n" +
                indent + "end\n",
                containingStructName,
                propertyName,
                generateEnumFieldNotPresentCondition(fieldToken.version(), enumName, indent),
                generateGet(encodingToken, "m.buffer", "m.offset + " + offsetStr),
                enumName);

            new Formatter(sb).format(
                indent + "@inline %2$s!(m::%1$sEncoder, value::%3$s.SbeEnum) = %4$s\n",
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


    private String generateLiteral(final PrimitiveType type, final String value)
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
                literal = juliaTypeName + "(0x" + Long.toHexString(Long.parseLong(value)) + ")";
                break;
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
        final String name,
        final List<Token> fields,
        final List<Token> groups,
        final List<Token> varData,
        final String indent)
    {
        new Formatter(sb).format("\n" +
            "function show(io::IO, m::%1$s{T}) where {T}\n" +
            "    println(io, \"%1$s view over a type $T\")\n" +
            "    println(io, \"SbeBlockLength: \", sbe_block_length(m))\n" +
            "    println(io, \"SbeTemplateId:  \", sbe_template_id(m))\n" +
            "    println(io, \"SbeSchemaId:    \", sbe_schema_id(m))\n" +
            "    println(io, \"SbeSchemaVersion: \", sbe_schema_version(m))\n" +
            "\n" +
            "    writer = %1$sDecoder(sbe_buffer(m), sbe_offset(m), sbe_block_length(m), sbe_schema_version(m))\n" +
            "%2$s" +
            "    nothing\n" +
            "end\n",
            formatStructName(name),
            appendDisplay(fields, groups, varData, indent));
    }


    private void generateGroupDisplay(
        final StringBuilder sb,
        final String groupStructName,
        final List<Token> fields,
        final List<Token> groups,
        final List<Token> varData,
        final String indent)
    {
        new Formatter(sb).format("\n" +
            "function show(io::IO, writer::%1$s{T}) where {T}\n" +
            "    println(io, \"%1$s view over a type $T\")\n" +
            "%2$s" +
            "end\n",
            groupStructName,
            appendDisplay(fields, groups, varData, indent + INDENT));
    }


    private void generateCompositeDisplay(
        final StringBuilder sb,
        final String name,
        final List<Token> tokens)
    {
        new Formatter(sb).format("\n" +
            "function show(io::IO, writer::%1$s{T}) where {T}\n" +
            "    println(io, \"%1$s view over a type $T\")\n" +
            "%2$s" +
            "end\n",
            formatStructName(name),
            appendDisplay(tokens, new ArrayList<>(), new ArrayList<>(), INDENT));
    }


    private CharSequence appendDisplay(
        final List<Token> fields,
        final List<Token> groups,
        final List<Token> varData,
        final String indent)
    {
        final StringBuilder sb = new StringBuilder();
        final boolean[] atLeastOne = {false};

        for (int i = 0, size = fields.size(); i < size; )
        {
            final Token fieldToken = fields.get(i);
            final Token encodingToken = fields.get(fieldToken.signal() == Signal.BEGIN_FIELD ? i + 1 : i);

            writeTokenDisplay(sb, fieldToken.name(), encodingToken, atLeastOne, indent);
            i += fieldToken.componentTokenCount();
        }

        for (int i = 0, size = groups.size(); i < size; i++)
        {
            final Token groupToken = groups.get(i);
            if (groupToken.signal() != Signal.BEGIN_GROUP)
            {
                throw new IllegalStateException("tokens must begin with BEGIN_GROUP: token=" + groupToken);
            }

            if (atLeastOne[0])
            {
                new Formatter(sb).format(
                    indent + "println(io)\n");
            }
            atLeastOne[0] = true;

            new Formatter(sb).format(
                indent + "println(io, \"%1$s:\")\n" +
                indent + "for group in %2$s(writer)\n" +
                indent + "    show(io, group)\n" +
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

            if (atLeastOne[0])
            {
                new Formatter(sb).format(
                    indent + "println(io)\n");
            }
            atLeastOne[0] = true;

            final String characterEncoding = varData.get(i + 3).encoding().characterEncoding();
            new Formatter(sb).format(
                indent + "print(io, \"%s: \")\n",
                varDataToken.name());

            if (null == characterEncoding)
            {
                new Formatter(sb).format(
                    indent + "print(io, skip_%1$s!(writer))\n" +
                    indent + "print(io, \" bytes of raw data\")\n",
                    formatPropertyName(varDataToken.name()));
            }
            else
            {
                addModuleToUsing("StringViews");
                final String fieldName = formatPropertyName(varDataToken.name());

                new Formatter(sb).format(
                    indent + "println(io, %s(StringView, writer))\n",
                    fieldName);
            }

            sb.append("\n");

            i += varDataToken.componentTokenCount();
        }

        return sb;
    }

    private void writeTokenDisplay(
        final StringBuilder sb,
        final String fieldTokenName,
        final Token typeToken,
        final boolean[] atLeastOne,
        final String indent)
    {
        if (typeToken.encodedLength() <= 0 || typeToken.isConstantEncoding())
        {
            return;
        }

        if (atLeastOne[0])
        {
            new Formatter(sb).format(
                indent + "println(io)\n");
        }
        else
        {
            atLeastOne[0] = true;
        }

        new Formatter(sb).format(
            indent + "print(io, \"%s: \")\n",
            fieldTokenName);
        final String fieldName = formatPropertyName(fieldTokenName) + "(writer)";

        switch (typeToken.signal())
        {
            case ENCODING:
                if (typeToken.encoding().primitiveType() == PrimitiveType.CHAR)
                {
                    addModuleToUsing("StringViews");
                    new Formatter(sb).format(
                        indent + "print(io, \"\\\"\")\n" +
                        indent + "print(io, %1$s(StringView, writer))\n" +
                        indent + "print(io, \"\\\"\")\n",
                        formatPropertyName(fieldTokenName));
                }
                else
                {
                    new Formatter(sb).format(
                        indent + "print(io, %s)\n",
                        fieldName);
                }
                break;

            case BEGIN_ENUM:
                new Formatter(sb).format(
                    indent + "print(io, %1$s)\n",
                    fieldName);
                break;

            case BEGIN_SET:
            case BEGIN_COMPOSITE:
                if (0 == typeToken.version())
                {
                    new Formatter(sb).format(
                        indent + "show(io, %1$s)\n",
                        fieldName);
                }
                else
                {
                    new Formatter(sb).format(
                        indent + "%1$s_in_acting_version(writer) && show(io, %2$s)\n",
                        formatPropertyName(fieldTokenName),
                        fieldName);
                }
                break;

            default:
                break;
        }

        sb.append('\n');
    }


    private void generateChoicesDisplay(
        final StringBuilder sb,
        final String name,
        final List<Token> tokens)
    {
        final List<Token> choiceTokens = new ArrayList<>();

        collect(Signal.CHOICE, tokens, 0, choiceTokens);

        new Formatter(sb).format("\n" +
            "function show(io::IO, writer::%1$s)\n" +
            "    print(io, \"[\")\n",
            name);

        if (choiceTokens.size() > 1)
        {
            sb.append("    at_least_one = false\n");
        }

        for (int i = 0, size = choiceTokens.size(); i < size; i++)
        {
            final Token token = choiceTokens.get(i);
            final String propertyString = formatPropertyName(token.name());

            new Formatter(sb).format(
                "    if %1$s(writer)\n",
                propertyString);

            if (i > 0)
            {
                sb.append("        at_least_one && print(io, \", \")\n");
            }
            new Formatter(sb).format(
                "        print(io, \"\\\"%1$s\\\"\")\n",
                propertyString);

            if (i < (size - 1))
            {
                sb.append("        at_least_one = true\n");
            }
            sb.append("    end\n");
        }

        new Formatter(sb).format(
            "    print(io, \"]\")\n" +
            "end\n");
    }

    private void generateMessageLength(
        final StringBuilder sb,
        final boolean isParent,
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
                indent + "    for group in %1$s(m)\n" +
                indent + "        sbe_skip!(group)\n" +
                indent + "    end\n",
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

        new Formatter(sb).format("\n" +
            indent + "@inline function sbe_skip!(m::%1$s)\n" +
            indent + "    %2$s\n" +
            sbSkip +
            indent + "    return\n" +
            indent + "end\n",
            structName,
            isParent ? "sbe_rewind!(m)" : "");
    }
}
