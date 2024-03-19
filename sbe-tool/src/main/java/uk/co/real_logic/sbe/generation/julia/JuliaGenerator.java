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
<<<<<<< HEAD
import static uk.co.real_logic.sbe.generation.julia.JuliaUtil.formatStructName;
import static uk.co.real_logic.sbe.generation.julia.JuliaUtil.formatPropertyName;
=======
import static uk.co.real_logic.sbe.generation.julia.JuliaUtil.formatClassName;
import static uk.co.real_logic.sbe.generation.julia.JuliaUtil.formatPropertyName;
import static uk.co.real_logic.sbe.generation.julia.JuliaUtil.formatReadBytes;
import static uk.co.real_logic.sbe.generation.julia.JuliaUtil.formatWriteBytes;
>>>>>>> 7f108351 (Add Julia code generation support)
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

<<<<<<< HEAD
    private void generateGroupStruct(
        final StringBuilder sb,
        final String groupStructName,
=======
    private static void generateGroupStruct(
        final StringBuilder sb,
        final String groupClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
        final String indent,
        final List<Token> fields,
        final List<Token> groups)
    {
<<<<<<< HEAD
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
=======
        new Formatter(sb).format(
            indent + "mutable struct %1$s{T<:AbstractArray{UInt8}}\n" +
            indent + "    buffer::T\n" +
            indent + "    offset::Int64\n" +
            indent + "    position::Int64\n" +
            indent + "    block_length::Int64\n" +
            indent + "    acting_version::Int64\n",
            indent + "    initial_position::Int64\n" +
            indent + "    count::Int64\n" +
            indent + "    index::Int64\n" +
            groupClassName);

        for (int i = 0, size = fields.size(); i < size; i++)
        {
            final Token signalToken = fields.get(i);
            if (signalToken.signal() == Signal.BEGIN_FIELD)
            {
                final Token encodingToken = fields.get(i + 1);
                final String propertyName = formatPropertyName(signalToken.name());
                final String typeName = formatClassName(encodingToken.applicableTypeName());

                switch (encodingToken.signal())
                {
                    case BEGIN_SET:
                    case BEGIN_COMPOSITE:
                        new Formatter(sb).format(indent + "    _%1$s::%2$s{T}\n",
                            propertyName,
                            typeName);
                        break;

                    default:
                        break;
                }
            }
        }

        for (int i = 0, size = groups.size(); i < size; )
        {
            final Token token = groups.get(i);
            if (token.signal() == Signal.BEGIN_GROUP)
            {
                final String propertyName = formatPropertyName(token.name());
                final String groupName = formatClassName(token.name());

                new Formatter(sb).format(indent + "    _%1$s::%2$s{T}\n",
                    propertyName,
                    groupName);
                i += token.componentTokenCount();
            }
        }

        new Formatter(sb).format(
            indent + "    %1$s{T}() where {T} = new()\n" +
            indent + "    %1$s{T}(args...) where {T} = new(args...)\n" +
            indent + "end\n\n",
            groupClassName);
>>>>>>> 7f108351 (Add Julia code generation support)
    }

    private static void generateGroupMethods(
        final StringBuilder sb,
<<<<<<< HEAD
        final String groupStructName,
=======
        final String groupClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
        final List<Token> tokens,
        final int index,
        final String indent)
    {
<<<<<<< HEAD
        final String dimensionsStructName = formatStructName(tokens.get(index + 1).name());
=======
        final String dimensionsClassName = formatClassName(tokens.get(index + 1).name());
>>>>>>> 7f108351 (Add Julia code generation support)
        final int dimensionHeaderLength = tokens.get(index + 1).encodedLength();
        final int blockLength = tokens.get(index).encodedLength();
        final Token numInGroupToken = Generators.findFirst("numInGroup", tokens, index);

<<<<<<< HEAD
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
=======
        new Formatter(sb).format("\n" +
            indent + "@inline function wrap_for_decode!(g::%1$s, buffer, position, acting_version)\n" +
            indent + "    dimensions = %2$s(buffer, position, acting_version)\n" +
            indent + "    g.buffer = buffer\n" +
            indent + "    g.initial_position = position\n" +
            indent + "    g.position = position + %3$d\n" +
            indent + "    g.block_length = blockLength(dimensions)\n" +
            indent + "    g.count = numInGroup(dimensions)\n" +
            indent + "    g.index = 0\n" +
            indent + "    g.acting_version = acting_version\n" +
            indent + "    return g\n" +
            indent + "end\n",
            groupClassName,
            dimensionsClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
            dimensionHeaderLength);

        final long minCount = numInGroupToken.encoding().applicableMinValue().longValue();
        final String minCheck = minCount > 0 ? "count < " + minCount + " || " : "";

<<<<<<< HEAD
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
=======
        new Formatter(sb).format("\n" +
            indent + "@inline function wrap_for_encode!(g::%1$s, buffer, count, position, acting_version)\n" +
            indent + "    if %2$scount > %3$d\n" +
            indent + "        error(lazy\"count outside of allowed range [E110]\")\n" +
            indent + "    end\n" +
            indent + "    dimensions = %4$s(buffer, position, acting_version)\n" +
            indent + "    blockLength!(dimensions, %5$d)\n" +
            indent + "    numInGroup!(dimensions, count)\n" +
            indent + "    g.buffer = buffer\n" +
            indent + "    g.initial_position = position\n" +
            indent + "    g.position = position + %6$d\n" +
            indent + "    g.block_length = %5$d\n" +
            indent + "    g.count = count\n" +
            indent + "    g.index = 0\n" +
            indent + "    g.acting_version = acting_version\n" +
            indent + "    return g\n" +
            indent + "end\n",
            groupClassName,
            minCheck,
            numInGroupToken.encoding().applicableMaxValue().longValue(),
            dimensionsClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
            blockLength,
            dimensionHeaderLength);

        new Formatter(sb).format("\n" +
            indent + "sbe_header_size(::%3$s) = %1$d\n" +
            indent + "sbe_block_length(::%3$s) = %2$d\n" +
            indent + "sbe_acting_block_length(g::%3$s) = g.block_length\n" +
<<<<<<< HEAD
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
=======
            indent + "sbe_position(g::%3$s) = g.position\n" +
            indent + "sbe_position!(g::%3$s, position) = g.position = sbe_check_position(g, position)\n" +
            indent + "sbe_check_position(g::%3$s, position) = (checkbounds(g.buffer, position); position)\n" +
            indent + "function Base.iterate(g::%3$s, state=nothing)\n" +
            indent + "    if g.index < g.count\n" +
            indent + "        g.offset = g.position\n" +
            indent + "        if !checkbounds(Bool, g.buffer, g.offset + 1 + g.block_length)\n" +
            indent + "            error(lazy\"buffer too short for next group index [E108]\")\n" +
            indent + "        end\n" +
            indent + "        g.position = g.offset + g.block_length\n" +
            indent + "        g.index += 1\n" +
            indent + "        return g, g\n" +
>>>>>>> 7f108351 (Add Julia code generation support)
            indent + "    else\n" +
            indent + "        return nothing\n" +
            indent + "    end\n" +
            indent + "end\n" +
<<<<<<< HEAD
            indent + "Base.eltype(::Type{<:%3$s}) = %3$s\n" +
            indent + "Base.isdone(g::%3$s, state...) = g.index >= g.count\n" +
            indent + "Base.length(g::%3$s) = g.count\n",
            dimensionHeaderLength,
            blockLength,
            groupStructName);

        new Formatter(sb).format("\n" +
            indent + "function reset_count_to_index!(g::%1$s)\n" +
=======
            indent + "Base.eltype(::Type{%3$s}) = %3$s\n" +
            indent + "Base.isdone(g::%3$s, state) = g.index >= g.count\n" +
            indent + "Base.length(g::%3$s) = g.count\n",
            dimensionHeaderLength,
            blockLength,
            groupClassName);

        new Formatter(sb).format("\n" +
            indent + "function reset_count_to_index(g::%1$s)\n" +
>>>>>>> 7f108351 (Add Julia code generation support)
            indent + "    g.count = g.index\n" +
            indent + "    dimensions = %2$s(g.buffer, g.initial_position, g.acting_version)\n" +
            indent + "    numInGroup!(dimensions, g.count)\n" +
            indent + "    return g.count\n" +
            indent + "end\n",
<<<<<<< HEAD
            groupStructName,
            dimensionsStructName);
=======
            groupClassName,
            dimensionsClassName);
>>>>>>> 7f108351 (Add Julia code generation support)
    }

    private static void generateGroupProperty(
        final StringBuilder sb,
        final String groupName,
<<<<<<< HEAD
        final String outerStructName,
=======
        final String outerClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
        final Token token,
        final String indent)
    {
        final String propertyName = formatPropertyName(groupName);
<<<<<<< HEAD
        final String groupStructName = formatStructName(groupName);
=======
>>>>>>> 7f108351 (Add Julia code generation support)
        final int version = token.version();

        if (version > 0)
        {
            new Formatter(sb).format("\n" +
                indent + "function %2$s(m::%1$s)\n" +
                indent + "    if m.acting_version < %3$d\n" +
<<<<<<< HEAD
                indent + "        return %4$s(m.buffer, 0, sbe_position_ptr(m), 0, m.acting_version, 0, 0, 0)\n" +
                indent + "    end\n" +
                indent + "    return %4$sDecoder(m.buffer, sbe_position_ptr(m), m.acting_version)\n" +
                indent + "end\n",
                outerStructName,
                propertyName,
                version,
                groupStructName);
=======
                indent + "        m.block_length = 0\n" +
                indent + "        m.count = 0\n" +
                indent + "        m.index = 0\n" +
                indent + "        m.acting_version = %3$d\n" +
                indent + "        m.initial_position = 0\n" +
                indent + "        m.position = 0\n" +
                indent + "        return m._%2$s\n" +
                indent + "    end\n" +
                indent + "    return wrap_for_decode!(m._%2$s, m.buffer, sbe_position(m), m.acting_version)\n" +
                indent + "end\n",
                outerClassName,
                propertyName,
                version);
>>>>>>> 7f108351 (Add Julia code generation support)
        }
        else
        {
            new Formatter(sb).format("\n" +
                indent + "function %2$s(m::%1$s)\n" +
<<<<<<< HEAD
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
=======
                indent + "    return wrap_for_decode!(m._%2$s, m.buffer, sbe_position(m), m.acting_version)\n" +
                indent + "end\n",
                outerClassName,
                propertyName);
        }

        // FIXME: Terrible name, it sets the number of groups then you can use the iterator interface
        new Formatter(sb).format("\n" +
            indent + "function %2$s_count!(m::%1$s, count)\n" +
            indent + "    return wrap_for_encode!(m._%2$s, m.buffer, count, sbe_position(m), m.acting_version)\n" +
            indent + "end\n" +
            indent + "%2$s!(m::%1$s, count) = %2$s_count!(m, count)\n",
            outerClassName,
            propertyName);
>>>>>>> 7f108351 (Add Julia code generation support)

        new Formatter(sb).format(
            indent + "%1$s_id(::%3$s) = %2$d\n",
            propertyName,
            token.id(),
<<<<<<< HEAD
            outerStructName);

        new Formatter(sb).format(
            indent + "%1$s_since_version(::%3$s) = %2$d\n" +
            indent + "%1$s_in_acting_version(m::%3$s) = m.acting_version >= %1$s_since_version(m)\n",
            propertyName,
            version,
            outerStructName);
=======
            outerClassName);

        final String versionCheck = 0 == version ?
            "true" : "m.acting_version >= %1$s_since_version(m)";
        new Formatter(sb).format(
            indent + "%1$s_since_version(::%3$s) = %2$d\n" +
            indent + "%1$s_in_acting_version(m::%3$s) = " + versionCheck + "\n",
            propertyName,
            version,
            outerClassName);
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
            indent + "        return @inbounds reinterpret(%1$s, view(m.buffer, 1:0))\n" +
=======
            indent + "        return missing\n" +
>>>>>>> 7f108351 (Add Julia code generation support)
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

<<<<<<< HEAD
=======
    private static CharSequence generateArrayFieldNotPresentConditionWithErr(
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

>>>>>>> 7f108351 (Add Julia code generation support)
    private static CharSequence generateStringNotPresentCondition(
        final int sinceVersion, final String indent)
    {
        if (0 == sinceVersion)
        {
            return "";
        }

        return String.format(
            indent + "    if m.acting_version < %1$d\n" +
<<<<<<< HEAD
            indent + "        return T(view(m.buffer, 1:0))\n" +
=======
            indent + "        return \"\"\n" +
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
            indent + "        return view(m.buffer, 1:0)\n" +
=======
            indent + "        return \"\"\n" +
>>>>>>> 7f108351 (Add Julia code generation support)
            indent + "    end\n\n",
            sinceVersion);
    }

    private static CharSequence generateEnumDeclaration(final String name, final Token encodingToken)
    {
        final Encoding encoding = encodingToken.encoding();
        return "@enum " + name + "::" + juliaTypeName(encoding.primitiveType()) + " begin\n";
    }

    private static void generateFieldMetaAttributeMethod(
<<<<<<< HEAD
        final StringBuilder sb, final Token token, final String indent, final String structName)
=======
        final StringBuilder sb, final Token token, final String indent, final String className)
>>>>>>> 7f108351 (Add Julia code generation support)
    {
        final Encoding encoding = token.encoding();
        final String propertyName = formatPropertyName(token.name());
        final String epoch = encoding.epoch() == null ? "" : encoding.epoch();
        final String timeUnit = encoding.timeUnit() == null ? "" : encoding.timeUnit();
        final String semanticType = encoding.semanticType() == null ? "" : encoding.semanticType();

        new Formatter(sb).format("\n" +
            "function %2$s_meta_attribute(::%1$s, meta_attribute)\n",
<<<<<<< HEAD
            structName,
=======
            className,
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
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
=======
        final String className)
    {
        final String bitsetName = formatClassName(token.applicableTypeName());
        final int offset = token.offset();

        new Formatter(sb).format(
            indent + "%2$s(m::%4$s) = wrap!(m._%2$s, m.buffer, m.offset + %3$d, m.acting_version)\n",
            bitsetName,
            propertyName,
            offset,
            className);

        new Formatter(sb).format(
            indent + "%1$s_encoding_length(::%3$s) = %2$d\n",
            propertyName,
            token.encoding().primitiveType().size(),
            className);
>>>>>>> 7f108351 (Add Julia code generation support)
    }

    private static void generateCompositeProperty(
        final StringBuilder sb,
        final String propertyName,
        final Token token,
        final String indent,
<<<<<<< HEAD
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
=======
        final String className)
    {
        final String compositeName = formatClassName(token.applicableTypeName());
        final int offset = token.offset();

        new Formatter(sb).format(
            indent + "%2$s(m::%4$s) = wrap!(m._%2$s, m.buffer, m.offset + %3$d, m.acting_version)\n",
            compositeName,
            propertyName,
            offset,
            className);
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
            fileOut.append(generateModule(moduleName, typesToInclude, messagesToInclude));
=======
            fileOut.append(generateModule(ir.namespaces(), typesToInclude, messagesToInclude));
>>>>>>> 7f108351 (Add Julia code generation support)
        }
    }

    private List<String> generateMessages() throws IOException
    {
        final List<String> messagesToInclude = new ArrayList<>();

        for (final List<Token> tokens : ir.messages())
        {
            final Token msgToken = tokens.get(0);
<<<<<<< HEAD
            final String structName = formatStructName(msgToken.name());

            try (Writer out = outputManager.createOutput(structName))
=======
            final String className = formatClassName(msgToken.name());

            try (Writer out = outputManager.createOutput(className))
>>>>>>> 7f108351 (Add Julia code generation support)
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

<<<<<<< HEAD
                sb.append(generateMessageFlyweightStruct(structName, fields, groups));
                sb.append(generateMessageFlyweightMethods(structName, msgToken));

                generateFields(sb, structName, fields, BASE_INDENT);
                generateGroups(sb, groups, BASE_INDENT, structName);
                generateVarData(sb, structName, varData, BASE_INDENT);
                generateDisplay(sb, msgToken.name(), structName, fields, groups, varData, INDENT);
                sb.append(generateMessageLength(groups, varData, BASE_INDENT, structName));
=======
                generateGroupStructs(sb, groups, BASE_INDENT);

                sb.append(generateMessageFlyweightStruct(className, fields, groups));
                sb.append(generateMessageFlyweightMethods(className, msgToken));

                generateFields(sb, className, fields, BASE_INDENT);
                generateGroups(sb, groups, BASE_INDENT, className);
                generateVarData(sb, className, varData, BASE_INDENT);
                generateDisplay(sb, msgToken.name(), className, fields, groups, varData, INDENT);
                sb.append(generateMessageLength(groups, varData, BASE_INDENT, className));
>>>>>>> 7f108351 (Add Julia code generation support)

                out.append(generateFileHeader());
                out.append(sb);
            }

<<<<<<< HEAD
            messagesToInclude.add(structName);
=======
            messagesToInclude.add(className);
>>>>>>> 7f108351 (Add Julia code generation support)
        }

        return messagesToInclude;
    }

    private void generateUtils() throws IOException
    {
        try (Writer out = outputManager.createOutput("Utils"))
        {
<<<<<<< HEAD
            out.append(generateFileHeader());
=======
            out.append("# Generated SBE (Simple Binary Encoding) message codec\n\n");
>>>>>>> 7f108351 (Add Julia code generation support)
            out.append(
                "@inline encode_le!(::Type{T}, buffer, offset, value) where {T} = @inbounds reinterpret(T, view(buffer, offset+1:offset+sizeof(T)))[] = htol(value)\n" +
                "@inline encode_be!(::Type{T}, buffer, offset, value) where {T} = @inbounds reinterpret(T, view(buffer, offset+1:offset+sizeof(T)))[] = hton(value)\n" +
                "@inline decode_le(::Type{T}, buffer, offset) where {T} = @inbounds ltoh(reinterpret(T, view(buffer, offset+1:offset+sizeof(T)))[])\n" +
                "@inline decode_be(::Type{T}, buffer, offset) where {T} = @inbounds ntoh(reinterpret(T, view(buffer, offset+1:offset+sizeof(T)))[])\n\n");
        }
    }

<<<<<<< HEAD
=======
    private String formatModules(final Set<String> modules)
    {
        final StringBuilder sb = new StringBuilder();

        for (final String s : modules)
        {
            sb.append("using ").append(s).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

>>>>>>> 7f108351 (Add Julia code generation support)
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

<<<<<<< HEAD
            final String groupStructName = formatStructName(groupToken.name());
=======
            final String groupStructName = formatClassName(groupToken.name());
>>>>>>> 7f108351 (Add Julia code generation support)

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
<<<<<<< HEAD
        final String outerStructName)
=======
        final String outerClassName)
>>>>>>> 7f108351 (Add Julia code generation support)
    {
        for (int i = 0, size = tokens.size(); i < size; i++)
        {
            final Token groupToken = tokens.get(i);
            if (groupToken.signal() != Signal.BEGIN_GROUP)
            {
                throw new IllegalStateException("tokens must begin with BEGIN_GROUP: token=" + groupToken);
            }

            final String groupName = groupToken.name();
<<<<<<< HEAD
            final String groupStructName = formatStructName(groupToken.name());
=======
            final String groupClassName = formatClassName(groupToken.name());
>>>>>>> 7f108351 (Add Julia code generation support)

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

<<<<<<< HEAD
            generateGroupStruct(sb, groupStructName, indent, fields, groups);
            generateGroupMethods(sb, groupStructName, tokens, groupStart, indent);
            generateFields(sb, groupStructName, fields, indent);
            generateGroups(sb, groups, indent, groupStructName);
            generateVarData(sb, groupStructName, varData, indent);

            sb.append(generateGroupDisplay(groupStructName, fields, groups, varData, indent));
            sb.append(generateMessageLength(groups, varData, indent, groupStructName));

            generateGroupProperty(sb, groupName, outerStructName, groupToken, indent);
=======
            generateGroups(sb, groups, indent, groupClassName);
            generateGroupMethods(sb, groupClassName, tokens, groupStart, indent);
            generateFields(sb, groupClassName, fields, indent);
            generateVarData(sb, groupClassName, varData, indent);

            sb.append(generateGroupDisplay(groupClassName, fields, groups, varData, indent));
            sb.append(generateMessageLength(groups, varData, indent, groupClassName));

            generateGroupProperty(sb, groupName, outerClassName, groupToken, indent);
>>>>>>> 7f108351 (Add Julia code generation support)
        }
    }

    private void generateVarData(
        final StringBuilder sb,
<<<<<<< HEAD
        final String structName,
=======
        final String className,
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
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
=======

            generateFieldMetaAttributeMethod(sb, token, indent, className);

            generateVarDataDescriptors(
                sb, token, propertyName, characterEncoding, lengthToken,
                lengthOfLengthField, lengthJuliaType, indent, className);

            new Formatter(sb).format("\n" +
                indent + "@inline function skip_%1$s(m::%4$s)\n" +
                "%2$s" +
                indent + "    len = %1$s_length(m)\n" +
                indent + "    sbe_position!(m, sbe_position(m) + %3$d + len)\n" +
>>>>>>> 7f108351 (Add Julia code generation support)
                indent + "    return len\n" +
                indent + "end\n",
                propertyName,
                generateArrayLengthNotPresentCondition(token.version(), indent),
                lengthOfLengthField,
<<<<<<< HEAD
                structName);

            new Formatter(sb).format("\n" +
                indent + "@inline function %1$s(m::%6$s)\n" +
=======
                className);

            new Formatter(sb).format("\n" +
                indent + "@inline function %1$s(m::%5$s)\n" +
>>>>>>> 7f108351 (Add Julia code generation support)
                "%2$s" +
                indent + "    len = %1$s_length(m)\n" +
                indent + "    pos = sbe_position(m) + %3$d\n" +
                indent + "    sbe_position!(m, pos + len)\n" +
<<<<<<< HEAD
                indent + "    return @inbounds reinterpret(%5$s, view(m.buffer, pos+1:pos+len))\n" +
=======
                indent + "    return view(m.buffer, pos+1:pos+len)\n" +
>>>>>>> 7f108351 (Add Julia code generation support)
                indent + "end\n",
                propertyName,
                generateTypeFieldNotPresentCondition(token.version(), indent),
                lengthOfLengthField,
                lengthJuliaType,
<<<<<<< HEAD
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
=======
                className);

            addModuleToUsing("StringViews");
            new Formatter(sb).format("\n" +
                indent + "%1$s(T::Type{<:AbstractString}, m::%2$s) = T(%1$s(m))\n" +
                indent + "%1$s_as_string(m::%2$s) = %1$s(StringView, m)\n",
                propertyName,
                className);

            new Formatter(sb).format("\n" +
                indent + "@inline function %1$s!(m::%3$s, src)\n" +
                indent + "    len = length(src)\n" +
                indent + "    %1$s_length!(m, len)\n" +
                indent + "    sbe_position!(m, sbe_position(m) + %2$d)\n" +
                indent + "    if len != 0\n" +
                indent + "        pos = sbe_position(m)\n" +
                indent + "        sbe_position!(m, pos + len)\n" +
                indent + "        copyto!(view(m.buffer, pos+1:pos+len), src)\n" +
                indent + "    end\n" +
                indent + "end\n",
                propertyName,
                lengthOfLengthField,
                className);
>>>>>>> 7f108351 (Add Julia code generation support)

            i += token.componentTokenCount();
        }
    }

<<<<<<< HEAD
    private void generateChoiceSet(final List<Token> tokens) throws IOException
    {
        final Token token = tokens.get(0);
        final String choiceName = formatStructName(token.applicableTypeName());

        try (Writer fileOut = outputManager.createOutput(choiceName))
        {
=======
    private void generateVarDataDescriptors(
        final StringBuilder sb,
        final Token token,
        final String propertyName,
        final String characterEncoding,
        final Token lengthToken,
        final Integer sizeOfLengthField,
        final String lengthJuliaType,
        final String indent,
        final String className)
    {
        new Formatter(sb).format("\n" +
            indent + "%1$s_character_encoding(::%3$s) = \"%2$s\"\n",
            propertyName,
            characterEncoding,
            className);

        final int version = token.version();
        final String versionCheck = 0 == version ?
        "true" : "m.acting_version >= %1$s_since_version(m)";
        new Formatter(sb).format(
            indent + "%1$s_since_version(::%4$s) = %2$d\n" +
            indent + "%1$s_in_acting_version(m::%4$s) = " + versionCheck + "\n" +
            indent + "%1$s_id(::%4$s) = %3$d\n",
            propertyName,
            version,
            token.id(),
            className);

        new Formatter(sb).format(
            indent + "%1$s_header_length(::%3$s) = %2$d\n",
            propertyName,
            sizeOfLengthField,
            className);

        new Formatter(sb).format(
            indent + "%1$s_length(m::%4$s) = %3$s\n",
            propertyName,
            generateArrayLengthNotPresentCondition(version, BASE_INDENT),
            generateGet(lengthToken, "m.buffer", "sbe_position(m)"),
            className);

        new Formatter(sb).format("\n" +
            indent + "function %1$s_length!(m::%4$s, n)\n" +
            indent + "    if !checkbounds(Bool, m.buffer, sbe_position(m) + n == 0 ? n : 4 + n)\n" +
            indent + "        error(lazy\"buffer too short for data length\")\n" +
            indent + "    end\n" +
            indent + "    return %3$s\n" +
            indent + "end\n",
            propertyName,
            generateArrayLengthNotPresentCondition(version, BASE_INDENT),
            generatePut(lengthToken, "m.buffer", "sbe_position(m)", "n"),
            className);
    }

    private void generateChoiceSet(final List<Token> tokens) throws IOException
    {
        final Token token = tokens.get(0);
        final String choiceName = formatClassName(token.applicableTypeName());

        try (Writer fileOut = outputManager.createOutput(choiceName))
        {
            modules.clear();
>>>>>>> 7f108351 (Add Julia code generation support)
            final StringBuilder out = new StringBuilder();

            out.append(generateFixedFlyweightStruct(choiceName, tokens));
            out.append(generateFixedFlyweightMethods(choiceName, tokens));

            final Encoding encoding = token.encoding();
            new Formatter(out).format("\n" +
<<<<<<< HEAD
                "@inline clear(m::%1$s)::%1$s = %2$s\n",
=======
                "clear(m::%1$s)::%1$s = %2$s\n",
>>>>>>> 7f108351 (Add Julia code generation support)
                choiceName,
                generatePut(token, "m.buffer", "m.offset", "0"));

            new Formatter(out).format(
<<<<<<< HEAD
                "@inline is_empty(m::%1$s) = %2$s == 0\n",
=======
                "is_empty(m::%1$s) = %2$s == 0\n",
>>>>>>> 7f108351 (Add Julia code generation support)
                choiceName,
                generateGet(token, "m.buffer", "m.offset"));

            new Formatter(out).format(
<<<<<<< HEAD
                "@inline raw_value(m::%1$s) = %2$s\n",
=======
                "raw_value(m::%1$s) = %2$s\n",
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
        final String enumName = formatStructName(tokens.get(0).applicableTypeName());

        try (Writer fileOut = outputManager.createOutput(enumName))
        {
=======
        final String enumName = formatClassName(tokens.get(0).applicableTypeName());

        try (Writer fileOut = outputManager.createOutput(enumName))
        {
            modules.clear();
>>>>>>> 7f108351 (Add Julia code generation support)
            final StringBuilder out = new StringBuilder();

            out.append(generateEnumDeclaration(enumName, enumToken));

            out.append(generateEnumValues(tokens.subList(1, tokens.size() - 1), enumName, enumToken));

<<<<<<< HEAD
=======
            // FIXME: Enum output generator
>>>>>>> 7f108351 (Add Julia code generation support)
            out.append(generateEnumDisplay(tokens.subList(1, tokens.size() - 1), enumToken));

            out.append("\n");

            fileOut.append(generateFileHeader());
            fileOut.append(out);
        }
    }

    private void generateComposite(final List<Token> tokens) throws IOException
    {
<<<<<<< HEAD
        final String compositeName = formatStructName(tokens.get(0).applicableTypeName());

        try (Writer fileOut = outputManager.createOutput(compositeName))
        {
=======
        final String compositeName = formatClassName(tokens.get(0).applicableTypeName());

        try (Writer fileOut = outputManager.createOutput(compositeName))
        {
            modules.clear();
>>>>>>> 7f108351 (Add Julia code generation support)
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

<<<<<<< HEAD
    private CharSequence generateChoices(final String bitsetStructName, final List<Token> tokens)
=======
    private CharSequence generateChoices(final String bitsetClassName, final List<Token> tokens)
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
                    "@inline function %2$s(m::%1$s)\n" +
                    "%6$s" +
                    "    return %7$s & (%5$s << %4$s) != 0\n" +
                    "end\n",
                    bitsetStructName,
=======
                    "function %2$s(m::%1$s)\n" +
                    "%6$s" +
                    "    return %7$s & (%5$s << %4$s) != 0\n" +
                    "end\n",
                    bitsetClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
                    bitsetStructName,
=======
                    bitsetClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
        final String moduleName,
=======
        final CharSequence[] namespaces,
>>>>>>> 7f108351 (Add Julia code generation support)
        final List<String> typesToInclude,
        final List<String> messagesToInclude)
    {
        final StringBuilder sb = new StringBuilder();
        final List<String> includes = new ArrayList<>();
        includes.add("Utils");
        includes.addAll(typesToInclude);
        includes.addAll(messagesToInclude);

<<<<<<< HEAD
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
=======
        sb.append("# Generated SBE (Simple Binary Encoding) message codec\n");

        sb.append(String.format(
            "# Code generated by SBE. DO NOT EDIT.\n\n" +

            "module %1$s\n\n" +

            "%2$s",
            namespacesToModuleName(namespaces),
            formatModules(modules)));

        if (includes != null && !includes.isEmpty())
        {
            for (final String incName : includes)
            {
                sb.append("include(\"").append(toUpperFirstChar(incName)).append(".jl\")\n");
            }
>>>>>>> 7f108351 (Add Julia code generation support)
        }

        sb.append("end\n");

        return sb;
    }

<<<<<<< HEAD
    private static CharSequence generateFileHeader()
=======
    private CharSequence generateFileHeader()
>>>>>>> 7f108351 (Add Julia code generation support)
    {
        final StringBuilder sb = new StringBuilder();

        sb.append("# Generated SBE (Simple Binary Encoding) message codec\n");
<<<<<<< HEAD
=======

>>>>>>> 7f108351 (Add Julia code generation support)
        sb.append("# Code generated by SBE. DO NOT EDIT.\n\n");

        return sb;
    }

    private CharSequence generateCompositePropertyElements(
<<<<<<< HEAD
        final String containingStructName, final List<Token> tokens, final String indent)
=======
        final String containingClassName, final List<Token> tokens, final String indent)
>>>>>>> 7f108351 (Add Julia code generation support)
    {
        final StringBuilder sb = new StringBuilder();

        for (int i = 0; i < tokens.size(); )
        {
            final Token fieldToken = tokens.get(i);
            final String propertyName = formatPropertyName(fieldToken.name());

<<<<<<< HEAD
            generateFieldMetaAttributeMethod(sb, fieldToken, indent, containingStructName);
            generateFieldCommonMethods(indent, sb, fieldToken, fieldToken, propertyName, containingStructName);
=======
            generateFieldMetaAttributeMethod(sb, fieldToken, indent, containingClassName);
            generateFieldCommonMethods(indent, sb, fieldToken, fieldToken, propertyName, containingClassName);
>>>>>>> 7f108351 (Add Julia code generation support)

            switch (fieldToken.signal())
            {
                case ENCODING:
<<<<<<< HEAD
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
=======
                    generatePrimitiveProperty(sb, containingClassName, propertyName, fieldToken, fieldToken, indent);
                    break;

                case BEGIN_ENUM:
                    generateEnumProperty(sb, containingClassName, fieldToken, propertyName, fieldToken, indent);
                    break;

                case BEGIN_SET:
                    generateBitsetProperty(sb, propertyName, fieldToken, indent, containingClassName);
                    break;

                case BEGIN_COMPOSITE:
                    generateCompositeProperty(sb, propertyName, fieldToken, indent, containingClassName);
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
        final String containingStructName,
=======
        final String containingClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
        final String propertyName,
        final Token propertyToken,
        final Token encodingToken,
        final String indent)
    {
<<<<<<< HEAD
        generatePrimitiveFieldMetaData(sb, propertyName, encodingToken, indent, containingStructName);

        if (encodingToken.isConstantEncoding())
        {
            generateConstPropertyMethods(sb, containingStructName, propertyName, encodingToken, indent);
=======
        generatePrimitiveFieldMetaData(sb, propertyName, encodingToken, indent, containingClassName);

        if (encodingToken.isConstantEncoding())
        {
            generateConstPropertyMethods(sb, containingClassName, propertyName, encodingToken, indent);
>>>>>>> 7f108351 (Add Julia code generation support)
        }
        else
        {
            generatePrimitivePropertyMethods(
<<<<<<< HEAD
                sb, containingStructName, propertyName, propertyToken, encodingToken, indent);
=======
                sb, containingClassName, propertyName, propertyToken, encodingToken, indent);
>>>>>>> 7f108351 (Add Julia code generation support)
        }
    }

    private void generatePrimitivePropertyMethods(
        final StringBuilder sb,
<<<<<<< HEAD
        final String containingStructName,
=======
        final String containingClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
        final String propertyName,
        final Token propertyToken,
        final Token encodingToken,
        final String indent)
    {
        final int arrayLength = encodingToken.arrayLength();
        if (arrayLength == 1)
        {
<<<<<<< HEAD
            generateSingleValueProperty(sb, containingStructName, propertyName, propertyToken, encodingToken, indent);
        }
        else if (arrayLength > 1)
        {
            generateArrayProperty(sb, containingStructName, propertyName, propertyToken, encodingToken, indent);
=======
            generateSingleValueProperty(sb, containingClassName, propertyName, propertyToken, encodingToken, indent);
        }
        else if (arrayLength > 1)
        {
            generateArrayProperty(sb, containingClassName, propertyName, propertyToken, encodingToken, indent);
>>>>>>> 7f108351 (Add Julia code generation support)
        }
    }

    private void generatePrimitiveFieldMetaData(
        final StringBuilder sb,
        final String propertyName,
        final Token token,
        final String indent,
<<<<<<< HEAD
        final String structName)
=======
        final String className)
>>>>>>> 7f108351 (Add Julia code generation support)
    {
        final Encoding encoding = token.encoding();
        final PrimitiveType primitiveType = encoding.primitiveType();
        final String juliaTypeName = juliaTypeName(primitiveType);
        final CharSequence nullValueString = generateNullValueLiteral(primitiveType, encoding);

<<<<<<< HEAD
        new Formatter(sb).format(
=======
        new Formatter(sb).format("\n" +
>>>>>>> 7f108351 (Add Julia code generation support)
            indent + "%1$s_null_value(::%4$s) = %3$s\n",
            propertyName,
            juliaTypeName,
            nullValueString,
<<<<<<< HEAD
            structName);
=======
            className);
>>>>>>> 7f108351 (Add Julia code generation support)

        new Formatter(sb).format(
            indent + "%1$s_min_value(::%4$s) = %3$s\n",
            propertyName,
            juliaTypeName,
            generateLiteral(primitiveType, token.encoding().applicableMinValue().toString()),
<<<<<<< HEAD
            structName);
=======
            className);
>>>>>>> 7f108351 (Add Julia code generation support)

        new Formatter(sb).format(
            indent + "%1$s_max_value(::%4$s) = %3$s\n",
            propertyName,
            juliaTypeName,
            generateLiteral(primitiveType, token.encoding().applicableMaxValue().toString()),
<<<<<<< HEAD
            structName);
=======
            className);
>>>>>>> 7f108351 (Add Julia code generation support)

        new Formatter(sb).format(
            indent + "%1$s_encoding_length(::%3$s) = %2$d\n",
            propertyName,
            token.encoding().primitiveType().size() * token.arrayLength(),
<<<<<<< HEAD
            structName);
=======
            className);
>>>>>>> 7f108351 (Add Julia code generation support)
    }

    private CharSequence generatePut(
        final Token token,
        final String buffer,
        final String index,
        final String value)
    {
        final PrimitiveType primitiveType = token.encoding().primitiveType();
<<<<<<< HEAD
        final String byteOrderSuffix = token.encoding().byteOrder() == ByteOrder.BIG_ENDIAN ? "be" : "le";
=======
        final ByteOrder byteOrder = token.encoding().byteOrder();
>>>>>>> 7f108351 (Add Julia code generation support)
        final String juliaTypeName = juliaTypeName(primitiveType);
        final StringBuilder sb = new StringBuilder();

        new Formatter(sb).format(
<<<<<<< HEAD
            "encode_%1$s(%2$s, %3$s, %4$s, %5$s)",
            byteOrderSuffix,
=======
            "%1$s(%2$s, %3$s, %4$s, %5$s)",
            formatWriteBytes(byteOrder, primitiveType),
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
        final String byteOrderSuffix = token.encoding().byteOrder() == ByteOrder.BIG_ENDIAN ? "be" : "le";
=======
        final ByteOrder byteOrder = token.encoding().byteOrder();
>>>>>>> 7f108351 (Add Julia code generation support)
        final String juliaTypeName = juliaTypeName(primitiveType);
        final StringBuilder sb = new StringBuilder();

        new Formatter(sb).format(
<<<<<<< HEAD
            "decode_%1$s(%2$s, %3$s, %4$s)",
            byteOrderSuffix,
=======
            "%1$s(%2$s, %3$s, %4$s)",
            formatReadBytes(byteOrder, primitiveType),
>>>>>>> 7f108351 (Add Julia code generation support)
            juliaTypeName,
            buffer,
            index);

        return sb;
    }

<<<<<<< HEAD
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
=======
    private void generateSingleValueProperty(
        final StringBuilder sb,
        final String containingClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
            containingStructName,
=======
            containingClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
            propertyName,
            juliaTypeName,
            generateFieldNotPresentCondition(propertyToken.version(), encodingToken.encoding(), indent),
            generateGet(encodingToken, "m.buffer", "m.offset + " + Integer.toString(offset)));

        new Formatter(sb).format(
<<<<<<< HEAD
            indent + "@inline %2$s!(m::%1$s, value) = %3$s\n",
            containingStructName,
=======
            indent + "%2$s!(m::%1$s, value) = %3$s\n",
            containingClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
            propertyName,
            generatePut(encodingToken, "m.buffer", "m.offset + " + Integer.toString(offset), "value"));
    }

    private void generateArrayProperty(
        final StringBuilder sb,
<<<<<<< HEAD
        final String containingStructName,
=======
        final String containingClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
        final String propertyName,
        final Token propertyToken,
        final Token encodingToken,
        final String indent)
    {
        final PrimitiveType primitiveType = encodingToken.encoding().primitiveType();
        final String juliaTypeName = juliaTypeName(primitiveType);
        final int offset = encodingToken.offset();

        final int arrayLength = encodingToken.arrayLength();
<<<<<<< HEAD
        new Formatter(sb).format(
            indent + "%1$s_length(::%3$s) = %2$d\n",
            propertyName,
            arrayLength,
            formatStructName(containingStructName));

        new Formatter(sb).format("\n" +
            indent + "@inline function %1$s(m::%2$s)\n" +
            indent + "%3$s" +
            indent + "    return @inbounds %4$s\n" +
            indent + "end\n",
            propertyName,
            formatStructName(containingStructName),
            generateArrayFieldNotPresentCondition(propertyToken.version(), indent),
            generateGetArrayReadWrite(encodingToken,
                juliaTypeName,
                "m.buffer",
                "m.offset+" + Integer.toString(offset),
                "sizeof(" + juliaTypeName + ")*" + Integer.toString(arrayLength)));

        // Only use StaticArrays for arrays of less than 100 elements
        if (arrayLength < 100)
        {
            addModuleToUsing("StaticArrays");
            new Formatter(sb).format("\n" +
                indent + "@inline function %1$s_static(m::%2$s)\n" +
                indent + "%3$s" +
                indent + "    return @inbounds %4$s\n" +
                indent + "end\n",
                propertyName,
                formatStructName(containingStructName),
                generateArrayFieldNotPresentCondition(propertyToken.version(), indent),
                generateGetArrayReadOnly(encodingToken,
                    "SVector{" + Integer.toString(arrayLength) + "," + juliaTypeName + "}",
                    "m.buffer",
                    "m.offset+" + Integer.toString(offset),
                    "sizeof(" + juliaTypeName + ")*" + Integer.toString(arrayLength)));
        }
=======
        new Formatter(sb).format("\n" +
            indent + "%1$s_length(::%3$s) = %2$d\n",
            propertyName,
            arrayLength,
            formatClassName(containingClassName));

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
            formatClassName(containingClassName));

            addModuleToUsing("StaticArrays");
            new Formatter(sb).format("\n" +
            indent + "@inline function %2$s_static(m::%6$s)\n" +
            indent + "%4$s" +
            indent + "    return @inbounds reinterpret(SVector{%3$d, %1$s}, view(m.buffer, m.offset+1+%5$d:m.offset+%5$d+sizeof(%1$s)*%3$d))[]\n" +
            indent + "end\n",
            juliaTypeName,
            propertyName,
            arrayLength,
            generateArrayFieldNotPresentCondition(propertyToken.version(), indent),
            offset,
            formatClassName(containingClassName));
>>>>>>> 7f108351 (Add Julia code generation support)

        if (encodingToken.encoding().primitiveType() == PrimitiveType.CHAR)
        {
            new Formatter(sb).format("\n" +
<<<<<<< HEAD
                indent + "@inline function %2$s(T::Type{<:AbstractString}, m::%6$s)\n" +
=======
                indent + "@inline function %2$s(m::%6$s, T::Type{<:AbstractString})\n" +
>>>>>>> 7f108351 (Add Julia code generation support)
                indent + "%4$s" +
                indent + "    return @inbounds T(view(m.buffer, m.offset+1+%5$d:m.offset+%5$d+sizeof(%1$s)*%3$d))\n" +
                indent + "end\n",
                juliaTypeName,
                propertyName,
                arrayLength,
                generateStringNotPresentCondition(propertyToken.version(), indent),
                offset,
<<<<<<< HEAD
                formatStructName(containingStructName));

            addModuleToUsing("StringViews");
            new Formatter(sb).format("\n" +
                indent + "%1$s_as_string(m::%2$s) = %1$s(StringView, m)\n",
                propertyName,
                formatStructName(containingStructName));
        }

        new Formatter(sb).format("\n" +
            indent + "@inline function %1$s!(m::%2$s, value)\n" +
            indent + "%3$s" +
            indent + "    copyto!(%4$s, value)\n" +
            indent + "end\n",
            propertyName,
            formatStructName(containingStructName),
            generateArrayFieldNotPresentCondition(propertyToken.version(), indent),
            generateGetArrayReadWrite(encodingToken,
                juliaTypeName,
                "m.buffer",
                "m.offset+" + Integer.toString(offset),
                "sizeof(" + juliaTypeName + ")*" + Integer.toString(arrayLength)));
=======
                formatClassName(containingClassName));

            new Formatter(sb).format("\n" +
                indent + "%1$s_as_string(m::%2$s) = %1$s(StringView, m)\n",
                propertyName,
                formatClassName(containingClassName));
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
            formatClassName(containingClassName));
>>>>>>> 7f108351 (Add Julia code generation support)
    }

    private void generateConstPropertyMethods(
        final StringBuilder sb,
<<<<<<< HEAD
        final String structName,
=======
        final String className,
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
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
=======
                className);

            return;
        }

        new Formatter(sb).format(
            indent + "%1$s(::%3$s) = \"%2$s\"\n",
            propertyName,
            token.encoding().constValue().toString(),
            className);
    }

    private CharSequence generateFixedFlyweightStruct(
        final String className,
        final List<Token> fields)
    {
        final StringBuilder sb = new StringBuilder();
        for (int i = 1, fieldSize = fields.size(); i < fieldSize; )
        {
            final Token signalToken = fields.get(i);
            final String propertyName = formatPropertyName(signalToken.name());
            final String typeName = formatClassName(signalToken.applicableTypeName());

            switch (signalToken.signal())
            {
                case BEGIN_SET:
                case BEGIN_COMPOSITE:
                    new Formatter(sb).format("    _%1$s::%2$s{T}\n",
                        propertyName,
                        typeName);
                    break;

                default:
                    break;
            }

            i += fields.get(i).componentTokenCount();
        }

        return String.format(
            "mutable struct %1$s{T<:AbstractArray{UInt8}}\n" +
                "    buffer::T\n" +
                "    offset::Int64\n" +
                "    acting_version::Int64\n" +
                "%2$s" +
                "    %1$s{T}() where {T} = new()\n" +
                "    %1$s{T}(args...) where {T} = new(args...)\n" +
                "end\n\n",
            className,
            sb);
    }

    private CharSequence generateFixedFlyweightMethods(
        final String className,
>>>>>>> 7f108351 (Add Julia code generation support)
        final List<Token> fields)
    {
        int size = fields.get(0).encodedLength();
        String sizeValue = String.format("%1$d", size);
        if (size == -1)
        {
            sizeValue = "typemax(UInt64)";
        }

        return String.format(
<<<<<<< HEAD
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
=======
                "function %1$s(buffer::T, offset, acting_version) where {T<:AbstractArray{UInt8}}\n" +
                "    return wrap!(%1$s{T}(), buffer, offset, acting_version)\n" +
                "end\n\n" +
                "@inline function wrap!(m::%1$s, buffer, offset, acting_version)\n" +
                "    m.buffer = buffer\n" +
                "    m.offset = offset\n" +
                "    m.acting_version = acting_version\n" +
                "    @boundscheck checkbounds(m.buffer, m.offset + %2$s)\n" +
                "    return m\n" +
                "end\n\n" +

                "encoded_length(::%1$s) = %2$s\n" +
                "buffer(m::%1$s) = m.buffer\n" +
                "offset(m::%1$s) = m.offset\n" +
                "acting_version(m::%1$s) = m.acting_version\n" +
                "sbe_schema_id(::%1$s) = %3$s\n" +
                "sbe_schema_version(::%1$s) = %4$s\n",
            className,
            sizeValue,
            generateLiteral(ir.headerStructure().schemaIdType(), Integer.toString(ir.id())),
            generateLiteral(ir.headerStructure().schemaVersionType(), Integer.toString(ir.version())));
    }

    private CharSequence generateMessageFlyweightStruct(
        final String className,
        final List<Token> fields,
        final List<Token> groups)
    {
        final StringBuilder sb = new StringBuilder();

        for (int i = 0, size = fields.size(); i < size; i++)
        {
            final Token signalToken = fields.get(i);
            if (signalToken.signal() == Signal.BEGIN_FIELD)
            {
                final Token encodingToken = fields.get(i + 1);
                final String propertyName = formatPropertyName(signalToken.name());
                final String typeName = formatClassName(encodingToken.applicableTypeName());

                switch (encodingToken.signal())
                {
                    case BEGIN_SET:
                    case BEGIN_COMPOSITE:
                        new Formatter(sb).format("    _%1$s::%2$s{T}\n",
                            propertyName,
                            typeName);
                        break;

                    default:
                        break;
                }
            }
        }

        for (int i = 0, size = groups.size(); i < size; )
        {
            final Token groupToken = groups.get(i);
            if (groupToken.signal() == Signal.BEGIN_GROUP)
            {
                final String propertyName = formatPropertyName(groupToken.name());
                final String groupName = formatClassName(groupToken.name());

                new Formatter(sb).format("    _%1$s::%2$s{T}\n",
                    propertyName,
                    groupName);

                i += groupToken.componentTokenCount();
            }
        }

        return String.format(
            "mutable struct %1$s{T<:AbstractArray{UInt8}}\n" +
            "    buffer::T\n" +
            "    offset::Int64\n" +
            "    position::Int64\n" +
            "    acting_block_length::Int64\n" +
            "    acting_version::Int64\n" +
            "%2$s" +
            "    %1$s{T}() where {T} = new()\n" +
            "    %1$s{T}(args...) where {T} = new(args...)\n" +
            "end\n\n",
            className,
            sb);
    }

    private CharSequence generateMessageFlyweightMethods(
        final String className,
>>>>>>> 7f108351 (Add Julia code generation support)
        final Token token)
    {
        final String semanticType = token.encoding().semanticType() == null ? "" : token.encoding().semanticType();
        final String semanticVersion = ir.semanticVersion() == null ? "" : ir.semanticVersion();

        return String.format(
<<<<<<< HEAD
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
=======
            "function %1$s(buffer::T, offset, acting_block_length, acting_version) where {T<:AbstractArray{UInt8}}\n" +
            "    return wrap!(%1$s{T}(), buffer, offset, acting_block_length, acting_version)\n" +
            "end\n\n" +
            "function %1$s(buffer::T, offset = 0) where {T<:AbstractArray{UInt8}}\n" +
            "    m = %1$s{T}()\n" +
            "    return wrap!(m, buffer, offset, sbe_block_length(m), sbe_schema_version(m))\n" +
            "end\n\n" +

            "buffer(m::%1$s) = m.buffer\n" +
            "offset(m::%1$s) = m.offset\n" +
            "acting_version(m::%1$s) = m.acting_version\n" +
            "sbe_position(m::%1$s) = m.position\n" +
            "sbe_position!(m::%1$s, position) = m.position = sbe_check_position(m, position)\n" +
            "sbe_check_position(m::%1$s, position) = (checkbounds(m.buffer, position); position)\n" +

            "sbe_block_length(::%1$s) = %2$s\n" +
            "sbe_block_and_header_length(m::%1$s{T}) where {T} = encoded_length(MessageHeader{T}()) + sbe_block_length(m)\n" +
>>>>>>> 7f108351 (Add Julia code generation support)
            "sbe_template_id(::%1$s) = %3$s\n" +
            "sbe_schema_id(::%1$s) = %4$s\n" +
            "sbe_schema_version(::%1$s) = %5$s\n" +
            "sbe_semantic_type(::%1$s) = \"%6$s\"\n" +
            "sbe_semantic_version(::%1$s) = \"%7$s\"\n" +
<<<<<<< HEAD
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
=======
            "sbe_rewind!(m::%1$s) = wrap_for_decode!(m, m.buffer, m.offset, m.acting_block_length, m.acting_version)\n\n" +

            "@inline function wrap!(m::%1$s, buffer, offset, acting_block_length, acting_version)\n" +
            "    m.buffer = buffer\n" +
            "    m.offset = offset\n" +
            "    m.position = sbe_check_position(m, offset + acting_block_length)\n" +
            "    m.acting_block_length = acting_block_length\n" +
            "    m.acting_version = acting_version\n" +
            "    return m\n" +
            "end\n\n" +

            "@inline function wrap_for_encode!(m::%1$s, buffer, offset = 0)\n" +
            "    return wrap!(m, buffer, offset, sbe_block_length(m), sbe_schema_version(m))\n" +
            "end\n\n" +

            "@inline function wrap_for_decode!(m::%1$s, buffer, offset, acting_block_length, acting_version)\n" +
            "    return wrap!(m, buffer, offset, acting_block_length, acting_version)\n" +
            "end\n\n" +

            "@inline function wrap_and_apply_header!(m::%1$s, buffer, offset = 0)\n" +
            "    hdr = MessageHeader(buffer, offset, sbe_schema_version(m))\n\n" +

            "    blockLength!(hdr, sbe_block_length(m))\n" +
            "    templateId!(hdr, sbe_template_id(m))\n" +
            "    schemaId!(hdr, sbe_schema_id(m))\n" +
            "    version!(hdr, sbe_schema_version(m))\n" +

            "    return wrap!(m,\n" +
            "        buffer,\n" +
            "        offset + encoded_length(hdr),\n" +
            "        sbe_block_length(m),\n" +
            "        sbe_schema_version(m))\n" +
            "end\n\n" +

            "encoded_length(m::%1$s) = sbe_position(m) - m.offset\n" +
            "function decoded_length(m::%1$s)\n" +
            "    skipper = %1$s(m.buffer, m.offset, sbe_block_length(m), m.acting_version)\n" +
            "    skip!(skipper)\n" +
            "    return encoded_length(skipper)\n" +
            "end\n",
            className,
            generateLiteral(ir.headerStructure().blockLengthType(), Integer.toString(token.encodedLength())),
            generateLiteral(ir.headerStructure().templateIdType(), Integer.toString(token.id())),
            generateLiteral(ir.headerStructure().schemaIdType(), Integer.toString(ir.id())),
            generateLiteral(ir.headerStructure().schemaVersionType(), Integer.toString(ir.version())),
>>>>>>> 7f108351 (Add Julia code generation support)
            semanticType,
            semanticVersion);
    }

    private void generateFields(
        final StringBuilder sb,
<<<<<<< HEAD
        final String containingStructName,
=======
        final String containingClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
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

<<<<<<< HEAD
                generateFieldMetaAttributeMethod(sb, signalToken, indent, containingStructName);
                generateFieldCommonMethods(indent, sb, signalToken, encodingToken, propertyName, containingStructName);
=======
                generateFieldMetaAttributeMethod(sb, signalToken, indent, containingClassName);
                generateFieldCommonMethods(indent, sb, signalToken, encodingToken, propertyName, containingClassName);
>>>>>>> 7f108351 (Add Julia code generation support)

                switch (encodingToken.signal())
                {
                    case ENCODING:
                        generatePrimitiveProperty(
<<<<<<< HEAD
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
=======
                            sb, containingClassName, propertyName, signalToken, encodingToken, indent);
                        break;

                    case BEGIN_ENUM:
                        generateEnumProperty(sb, containingClassName, signalToken, propertyName, encodingToken, indent);
                        break;

                    case BEGIN_SET:
                        generateBitsetProperty(sb, propertyName, encodingToken, indent, containingClassName);
                        break;

                    case BEGIN_COMPOSITE:
                        generateCompositeProperty(sb, propertyName, encodingToken, indent, containingClassName);
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
        final String structName)
=======
        final String className)
>>>>>>> 7f108351 (Add Julia code generation support)
    {
        new Formatter(sb).format(
            indent + "%1$s_id(::%3$s) = %2$d\n",
            propertyName,
            fieldToken.id(),
<<<<<<< HEAD
            structName);

        new Formatter(sb).format(
            indent + "%1$s_since_version(::%3$s) = %2$d\n" +
            indent + "%1$s_in_acting_version(m::%3$s) = m.acting_version >= %1$s_since_version(m)\n",
            propertyName,
            fieldToken.version(),
            structName);
=======
            className);

        final int version = fieldToken.version();
        final String versionCheck = 0 == version ?
            "true" : "m.acting_version >= %1$s_since_version(m)";
        new Formatter(sb).format(
            indent + "%1$s_since_version(::%3$s) = %2$d\n" +
            indent + "%1$s_in_acting_version(m::%3$s) = " + versionCheck + "\n",
            propertyName,
            version,
            className);
>>>>>>> 7f108351 (Add Julia code generation support)

        new Formatter(sb).format(
            indent + "%1$s_encoding_offset(::%3$s) = %2$d\n",
            propertyName,
            encodingToken.offset(),
<<<<<<< HEAD
            structName);
=======
            className);
>>>>>>> 7f108351 (Add Julia code generation support)
    }

    private void generateEnumProperty(
        final StringBuilder sb,
<<<<<<< HEAD
        final String containingStructName,
=======
        final String containingClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
        final Token fieldToken,
        final String propertyName,
        final Token encodingToken,
        final String indent)
    {
<<<<<<< HEAD
        final String enumName = formatStructName(encodingToken.applicableTypeName());
=======
        final String enumName = formatClassName(encodingToken.applicableTypeName());
>>>>>>> 7f108351 (Add Julia code generation support)
        final PrimitiveType primitiveType = encodingToken.encoding().primitiveType();
        final String typeName = juliaTypeName(primitiveType);
        final int offset = encodingToken.offset();

        new Formatter(sb).format(
            indent + "%2$s_encoding_length(::%1$s) = %3$d\n",
<<<<<<< HEAD
            containingStructName,
=======
            containingClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
            propertyName,
            fieldToken.encodedLength());

        if (fieldToken.isConstantEncoding())
        {
            final String constValue = fieldToken.encoding().constValue().toString();
<<<<<<< HEAD

=======
>>>>>>> 7f108351 (Add Julia code generation support)
            new Formatter(sb).format(
                indent + "%2$s_raw(::%3$s) = %1$s(%5$s_%4$s)\n",
                typeName,
                propertyName,
<<<<<<< HEAD
                containingStructName,
=======
                containingClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
                constValue.substring(constValue.indexOf(".") + 1),
                enumName);

            new Formatter(sb).format(
<<<<<<< HEAD
                indent + "@inline function %2$s(m::%1$s)\n" +
                "%3$s" +
                indent + "    return %5$s_%4$s\n" +
                indent + "end\n",
                containingStructName,
=======
                indent + "%2$s_const_value(::%1$s) = %4$s_%3$s\n",
                containingClassName,
                propertyName,
                constValue.substring(constValue.indexOf(".") + 1),
                enumName);

            new Formatter(sb).format(
                indent + "function %2$s(m::%1$s)\n" +
                "%3$s" +
                indent + "    return %5$s_%4$s\n" +
                indent + "end\n",
                containingClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
                propertyName,
                generateEnumFieldNotPresentCondition(fieldToken.version(), enumName, indent),
                constValue.substring(constValue.indexOf(".") + 1),
                enumName);
        }
        else
        {
            final String offsetStr = Integer.toString(offset);
            new Formatter(sb).format(
<<<<<<< HEAD
                indent + "@inline function %1$s_raw(m::%4$s)\n" +
=======
                indent + "function %1$s_raw(m::%4$s)\n" +
>>>>>>> 7f108351 (Add Julia code generation support)
                "%2$s" +
                indent + "    return %3$s\n" +
                indent + "end\n",
                propertyName,
                generateFieldNotPresentCondition(fieldToken.version(), encodingToken.encoding(), indent),
                generateGet(encodingToken, "m.buffer", "m.offset + " + offsetStr),
<<<<<<< HEAD
                containingStructName);

            new Formatter(sb).format(
                indent + "@inline function %2$s(m::%1$s)\n" +
                "%3$s" +
                indent + "    return %5$s(%4$s)\n" +
                indent + "end\n",
                containingStructName,
=======
                containingClassName);

            new Formatter(sb).format(
                indent + "function %2$s(m::%1$s)\n" +
                "%3$s" +
                indent + "    return %5$s(%4$s)\n" +
                indent + "end\n",
                containingClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
                propertyName,
                generateEnumFieldNotPresentCondition(fieldToken.version(), enumName, indent),
                generateGet(encodingToken, "m.buffer", "m.offset + " + offsetStr),
                enumName);

            new Formatter(sb).format(
<<<<<<< HEAD
                indent + "@inline %2$s!(m::%1$s, value::%3$s) = %4$s\n",
                formatStructName(containingStructName),
                propertyName,
                enumName,
                generatePut(encodingToken, "m.buffer", "m.offset + " + offsetStr, typeName + "(value)"));
=======
                indent + "%2$s!(m::%1$s, value) = %3$s\n",
                formatClassName(containingClassName),
                propertyName,
                generatePut(encodingToken, "m.buffer", "m.offset + " + offsetStr, "value"));
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
        final String containingStructName,
=======
        final String containingClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
        final String name,
        final List<Token> fields,
        final List<Token> groups,
        final List<Token> varData,
        final String indent)
    {
        new Formatter(sb).format("\n" +
<<<<<<< HEAD
            "function print_fields(io::IO, writer::%1$s{T}) where {T}\n" +
=======
            "function Base.show(io::IO, mime::MIME\"text/plain\", writer::%1$s{T}) where {T}\n" +
>>>>>>> 7f108351 (Add Julia code generation support)
            "    println(io, \"%1$s view over a type $T\")\n" +
            "    println(io, \"SbeBlockLength: \", sbe_block_length(writer))\n" +
            "    println(io, \"SbeTemplateId:  \", sbe_template_id(writer))\n" +
            "    println(io, \"SbeSchemaId:    \", sbe_schema_id(writer))\n" +
<<<<<<< HEAD
            "    println(io, \"SbeSchemaVersion: \", sbe_schema_version(writer))\n" +
            "    sbe_rewind!(writer)\n" +
            "%2$s" +
            "    sbe_rewind!(writer)\n" +
            "end\n",
            formatStructName(name),
            appendDisplay(containingStructName, fields, groups, varData, indent));
=======
            "%2$s" +
            "end\n",
            formatClassName(name),
            appendDisplay(containingClassName, fields, groups, varData, indent + INDENT));
>>>>>>> 7f108351 (Add Julia code generation support)
    }


    private CharSequence generateGroupDisplay(
<<<<<<< HEAD
        final String groupStructName,
=======
        final String groupClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
        final List<Token> fields,
        final List<Token> groups,
        final List<Token> varData,
        final String indent)
    {
        return String.format("\n" +
<<<<<<< HEAD
            "function print_fields(io::IO, writer::%1$s{T}) where {T}\n" +
            "    println(io, \"%1$s view over a type $T\")\n" +
            "%2$s" +
            "end\n",
            groupStructName,
            appendDisplay(groupStructName, fields, groups, varData, indent + INDENT));
=======
            "function Base.show(io::IO, mime::MIME\"text/plain\", writer::%1$s{T}) where {T}\n" +
            "    println(io, \"%1$s view over a type $T\")\n" +
            "%2$s" +
            "end\n",
            groupClassName,
            appendDisplay(groupClassName, fields, groups, varData, indent + INDENT));
>>>>>>> 7f108351 (Add Julia code generation support)
    }


    private CharSequence generateCompositeDisplay(final String name, final List<Token> tokens)
    {
        return String.format("\n" +
<<<<<<< HEAD
            "function print_fields(io::IO, writer::%1$s{T}) where {T}\n" +
            "    println(io, \"%1$s view over a type $T\")\n" +
            "%2$s" +
            "end\n\n",
            formatStructName(name),
            appendDisplay(formatStructName(name), tokens, new ArrayList<>(), new ArrayList<>(), INDENT));
=======
            "function Base.show(io::IO, mime::MIME\"text/plain\", writer::%1$s{T}) where {T}\n" +
            "    println(io, \"%1$s view over a type $T\")\n" +
            "%2$s" +
            "end\n\n",
            formatClassName(name),
            appendDisplay(formatClassName(name), tokens, new ArrayList<>(), new ArrayList<>(), INDENT));
>>>>>>> 7f108351 (Add Julia code generation support)
    }


    private CharSequence appendDisplay(
<<<<<<< HEAD
        final String containingStructName,
=======
        final String containingClassName,
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD

=======
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
                indent + "println(io, \"%1$s:\")\n" +
                indent + "for group in %2$s(writer)\n" +
                indent + "    print_fields(io, group)\n" +
                indent + "    println(io)\n" +
                indent + "end\n",
                formatStructName(groupToken.name()),
=======
                indent + "for group in %2$s(writer)\n" +
                indent + "    show(io, mime, group)\n" +
                indent + "end\n",
                containingClassName + formatClassName(groupToken.name()),
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
                final String skipFunction = "skip_" + formatPropertyName(varDataToken.name()) + "!(writer)";

                sb.append(indent).append("print(io, ").append(skipFunction).append(")\n")
=======
                final String skipFunction = "skip!(writer)" + toUpperFirstChar(varDataToken.name()) + "()";

                sb.append(indent).append("print(io, \")\n")
                    .append(indent).append(INDENT).append(skipFunction).append("\n")
>>>>>>> 7f108351 (Add Julia code generation support)
                    .append(INDENT).append("print(io, \" bytes of raw data\")\n");
            }
            else
            {
                addModuleToUsing("StringViews");
                final String fieldName = formatPropertyName(varDataToken.name()) + "(StringView, writer)";

<<<<<<< HEAD
                sb.append(indent).append("println(io, ").append(fieldName).append(")").append("\n\n");
=======
                sb.append(indent).append("show(io, mime, ").append(fieldName).append(")").append("\n\n");
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
                    addModuleToUsing("StringViews");
                    sb.append(indent).append("print(io, \"\\\"\")\n");
                    sb.append(indent).append("print(io, " + formatPropertyName(fieldTokenName) + "(StringView, writer))\n");
                    sb.append(indent).append("print(io, \"\\\"\")\n");
=======
                    sb.append(indent).append("show(io, mime, " + fieldName + ")").append("\n");
>>>>>>> 7f108351 (Add Julia code generation support)
                }
                else
                {
                    sb.append(indent).append("print(io, " + fieldName + ")").append("\n");
                }
                break;

            case BEGIN_ENUM:
<<<<<<< HEAD
                sb.append(indent).append("print_fields(io, ").append(fieldName).append(")\n");
=======
                sb.append(indent).append("show(io, mime, ").append(fieldName).append(")\n");
>>>>>>> 7f108351 (Add Julia code generation support)
                break;

            case BEGIN_SET:
            case BEGIN_COMPOSITE:
                if (0 == typeToken.version())
                {
<<<<<<< HEAD
                    sb.append(indent).append("print_fields(io, ").append(fieldName).append(")\n");
=======
                    sb.append(indent).append("show(io, mime, ").append(fieldName).append(")\n");
>>>>>>> 7f108351 (Add Julia code generation support)
                }
                else
                {
                    new Formatter(sb).format(
<<<<<<< HEAD
                        indent + "if %1$s_in_acting_version(writer)\n" +
                        indent + "    print_fields(io, %2$s)\n" +
                        indent + "end\n",
                        formatPropertyName(fieldTokenName),
                        fieldName);
=======
                        indent + "if %1$s_in_acting_version()\n" +
                        indent + "    show(io, mime, %1$s)\n" +
                        indent + "else\n" +
                        indent + "    print(io, %2$s)\n" +
                        indent + "end\n",
                        fieldName,
                        typeToken.signal() == Signal.BEGIN_SET ? "\"[]\"" : "\"{}\"");
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
            "function print_fields(io::IO, writer::%1$s)\n",
=======
            "function Base.show(io::IO, mime::MIME\"text/plain\", writer::%1$s)\n",
>>>>>>> 7f108351 (Add Julia code generation support)
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
<<<<<<< HEAD
        final String enumName = formatStructName(encodingToken.applicableTypeName());
        final StringBuilder sb = new StringBuilder();

        new Formatter(sb).format("\n" +
            "function print_fields(io::IO, value::%1$s)\n",
=======
        final String enumName = formatClassName(encodingToken.applicableTypeName());
        final StringBuilder sb = new StringBuilder();

        new Formatter(sb).format("\n" +
            "function Base.show(io::IO, mime::MIME\"text/plain\", value::%1$s)\n",
>>>>>>> 7f108351 (Add Julia code generation support)
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

<<<<<<< HEAD
=======
    private Object[] generateMessageLengthArgs(
        final List<Token> groups,
        final List<Token> varData,
        final String indent,
        final boolean inStruct)
    {
        final StringBuilder sb = new StringBuilder();
        int count = 0;

        for (int i = 0, size = groups.size(); i < size; i++)
        {
            final Token groupToken = groups.get(i);
            if (groupToken.signal() != Signal.BEGIN_GROUP)
            {
                throw new IllegalStateException("tokens must begin with BEGIN_GROUP: token=" + groupToken);
            }

            final int endSignal = findEndSignal(groups, i, Signal.END_GROUP, groupToken.name());
            final String groupName = formatPropertyName(groupToken.name());

            if (count > 0)
            {
                if (inStruct)
                {
                    sb.append("; ").append(indent);
                }
                else
                {
                    sb.append(",\n").append(indent);
                }
            }

            final List<Token> thisGroup = groups.subList(i, endSignal + 1);

            if (isMessageConstLength(thisGroup))
            {
                sb.append(" ").append(groupName).append("_length");
            }
            else
            {
                sb.append(groupName).append("_items_length::");
                sb.append(generateMessageLengthArgs(thisGroup, indent + INDENT, true)[0]);
            }

            count += 1;

            i = endSignal;
        }

        for (int i = 0, size = varData.size(); i < size; )
        {
            final Token varDataToken = varData.get(i);
            if (varDataToken.signal() != Signal.BEGIN_VAR_DATA)
            {
                throw new IllegalStateException("tokens must begin with BEGIN_VAR_DATA: token=" + varDataToken);
            }

            if (count > 0)
            {
                if (inStruct)
                {
                    sb.append("; ").append(indent);
                }
                else
                {
                    sb.append(",\n").append(indent);
                }
            }

            sb.append(" ").append(formatPropertyName(varDataToken.name())).append("Length").append(" int");

            count += 1;

            i += varDataToken.componentTokenCount();
        }

        CharSequence result = sb;
        if (count > 1)
        {
            result = "\n" + indent + result;
        }

        return new Object[] {result, count};
    }


    private Object[] generateMessageLengthArgs(final List<Token> tokens, final String indent, final boolean inStruct)
    {
        int i = 0;

        final Token groupToken = tokens.get(i);
        if (groupToken.signal() != Signal.BEGIN_GROUP)
        {
            throw new IllegalStateException("tokens must begin with BEGIN_GROUP: token=" + groupToken);
        }

        ++i;
        final int groupHeaderTokenCount = tokens.get(i).componentTokenCount();
        i += groupHeaderTokenCount;

        final List<Token> fields = new ArrayList<>();
        i = collectFields(tokens, i, fields);

        final List<Token> groups = new ArrayList<>();
        i = collectGroups(tokens, i, groups);

        final List<Token> varData = new ArrayList<>();
        collectVarData(tokens, i, varData);

        return generateMessageLengthArgs(groups, varData, indent, inStruct);
    }

    private boolean isMessageConstLength(final List<Token> tokens)
    {
        final Integer count = (Integer)generateMessageLengthArgs(tokens, BASE_INDENT, false)[1];

        return count == 0;
    }

    private String generateMessageLengthCallHelper(
        final List<Token> groups,
        final List<Token> varData)
    {
        final StringBuilder sb = new StringBuilder();
        int count = 0;

        for (int i = 0, size = groups.size(); i < size; i++)
        {
            final Token groupToken = groups.get(i);
            if (groupToken.signal() != Signal.BEGIN_GROUP)
            {
                throw new IllegalStateException("tokens must begin with BEGIN_GROUP: token=" + groupToken);
            }

            final int endSignal = findEndSignal(groups, i, Signal.END_GROUP, groupToken.name());
            final String groupName = formatPropertyName(groupToken.name());

            if (count > 0)
            {
                sb.append(", ");
            }

            final List<Token> thisGroup = groups.subList(i, endSignal + 1);

            if (isMessageConstLength(thisGroup))
            {
                sb.append("e.").append(groupName).append("Length");
            }
            else
            {
                sb.append("e.").append(groupName).append("ItemLengths");
            }

            count += 1;

            i = endSignal;
        }

        for (int i = 0, size = varData.size(); i < size; )
        {
            final Token varDataToken = varData.get(i);
            if (varDataToken.signal() != Signal.BEGIN_VAR_DATA)
            {
                throw new IllegalStateException("tokens must begin with BEGIN_VAR_DATA: token=" + varDataToken);
            }

            if (count > 0)
            {
                sb.append(", ");
            }

            sb.append("e.").append(formatPropertyName(varDataToken.name())).append("Length");

            count += 1;

            i += varDataToken.componentTokenCount();
        }

        return sb.toString();
    }

    private CharSequence generateMessageLengthCallHelper(final List<Token> tokens)
    {
        int i = 0;

        final Token groupToken = tokens.get(i);
        if (groupToken.signal() != Signal.BEGIN_GROUP)
        {
            throw new IllegalStateException("tokens must begin with BEGIN_GROUP: token=" + groupToken);
        }

        ++i;
        final int groupHeaderTokenCount = tokens.get(i).componentTokenCount();
        i += groupHeaderTokenCount;

        final List<Token> fields = new ArrayList<>();
        i = collectFields(tokens, i, fields);

        final List<Token> groups = new ArrayList<>();
        i = collectGroups(tokens, i, groups);

        final List<Token> varData = new ArrayList<>();
        collectVarData(tokens, i, varData);

        return generateMessageLengthCallHelper(groups, varData);
    }

>>>>>>> 7f108351 (Add Julia code generation support)
    private CharSequence generateMessageLength(
        final List<Token> groups,
        final List<Token> varData,
        final String indent,
<<<<<<< HEAD
        final String structName)
    {
=======
        final String className)
    {
        final StringBuilder sbEncode = new StringBuilder();
>>>>>>> 7f108351 (Add Julia code generation support)
        final StringBuilder sbSkip = new StringBuilder();

        for (int i = 0, size = groups.size(); i < size; i++)
        {
            final Token groupToken = groups.get(i);

            if (groupToken.signal() != Signal.BEGIN_GROUP)
            {
                throw new IllegalStateException("tokens must begin with BEGIN_GROUP: token=" + groupToken);
            }

            final int endSignal = findEndSignal(groups, i, Signal.END_GROUP, groupToken.name());
<<<<<<< HEAD
=======
            final List<Token> thisGroup = groups.subList(i, endSignal + 1);

            final Token numInGroupToken = Generators.findFirst("numInGroup", groups, i);
            final long minCount = numInGroupToken.encoding().applicableMinValue().longValue();
            final long maxCount = numInGroupToken.encoding().applicableMaxValue().longValue();

            final String countName = isMessageConstLength(thisGroup) ?
                formatPropertyName(groupToken.name()) + "Length" :
                "length(" + formatPropertyName(groupToken.name()) + "ItemLengths)";

            final String minCheck = minCount > 0 ? countName + " < " + minCount + " || " : "";
            final String maxCheck = countName + " > " + maxCount;

            new Formatter(sbEncode).format("\n" +
                indent + "    length += sbe_header_size(m._%1$s)\n",
                formatPropertyName(groupToken.name()));

            if (isMessageConstLength(thisGroup))
            {
                new Formatter(sbEncode).format(
                    indent + "    if %3$s%4$s\n" +
                    indent + "        error(lazy\"%5$s outside of allowed range [E110]\")\n" +
                    indent + "    end\n" +
                    indent + "    length += Int64(%1$s_length) * sbe_block_length(m._%1$s)\n",
                    formatPropertyName(groupToken.name()),
                    formatClassName(groupToken.name()),
                    minCheck,
                    maxCheck,
                    countName);
            }
            else
            {
                new Formatter(sbEncode).format(
                    indent + "    if %3$s%4$s\n" +
                    indent + "        error(lazy\"%5$s outside of allowed range [E110]\")\n" +
                    indent + "    end\n\n" +
                    indent + "    for _, e := range %1$sItemLengths {\n" +
                    indent + "        l, err := m.%1$s().compute_length(%6$s)\n" +
                    indent + "        if err != nil {\n" +
                    indent + "            return 0, err\n" +
                    indent + "        }\n" +
                    indent + "        length += uint64(l)\n" +
                    indent + "    end\n",
                    formatPropertyName(groupToken.name()),
                    formatClassName(groupToken.name()),
                    minCheck,
                    maxCheck,
                    countName,
                    generateMessageLengthCallHelper(thisGroup));
            }
>>>>>>> 7f108351 (Add Julia code generation support)

            new Formatter(sbSkip).format(
                indent + ("    for group in %1$s(m)\n") +
                indent + ("        skip!(group)\n") +
                indent + ("    end\n"),
                formatPropertyName(groupToken.name()));

            i = endSignal;
        }
<<<<<<< HEAD

=======
>>>>>>> 7f108351 (Add Julia code generation support)
        for (int i = 0, size = varData.size(); i < size; )
        {
            final Token varDataToken = varData.get(i);

            if (varDataToken.signal() != Signal.BEGIN_VAR_DATA)
            {
                throw new IllegalStateException("tokens must begin with BEGIN_VAR_DATA: token=" + varDataToken);
            }

<<<<<<< HEAD
            new Formatter(sbSkip).format(
                indent + "    skip_%1$s!(m)\n",
=======
            final Token lengthToken = Generators.findFirst("length", varData, i);

            new Formatter(sbEncode).format("\n" +
                indent + "    length += %1$s_header_length(m)\n" +
                indent + "    if %1$s_length(m) > %2$d\n" +
                indent + "        error(lazy\"%1$sLength too long for length type [E109]\")\n" +
                indent + "    end\n" +
                indent + "    length += Int64(%1$s_length(m))\n",
                formatPropertyName(varDataToken.name()),
                lengthToken.encoding().applicableMaxValue().longValue());

            new Formatter(sbSkip).format(
                indent + "    skip_%1$s(m)\n",
>>>>>>> 7f108351 (Add Julia code generation support)
                formatPropertyName(varDataToken.name()));

            i += varDataToken.componentTokenCount();
        }

        final StringBuilder sb = new StringBuilder();

        new Formatter(sb).format("\n" +
<<<<<<< HEAD
            indent + "@inline function skip!(m::%1$s)\n" +
            sbSkip +
            indent + "    return\n" +
            indent + "end\n\n",
            structName);
=======
            indent + "function skip!(m::%2$s)\n" +
            sbSkip +
            indent + "end\n\n" +

            indent + "is_const_length(::%2$s) = " + (groups.isEmpty() && varData.isEmpty()) + "\n" +

            indent + "function compute_length(m::%2$s %1$s)\n" +
            indent + "    length = Int64(sbe_block_length(m))\n" +
            sbEncode + "\n" +
            indent + "    return length\n" +
            indent + "end\n",
            generateMessageLengthArgs(groups, varData, indent + INDENT, false)[0],
            className);
>>>>>>> 7f108351 (Add Julia code generation support)

        return sb;
    }
}
