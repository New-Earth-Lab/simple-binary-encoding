/*
 * Copyright 2013-2023 Real Logic Limited.
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

package uk.co.real_logic.sbe.properties;

import org.agrona.collections.MutableInteger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import static java.util.Objects.requireNonNull;
import static uk.co.real_logic.sbe.generation.Generators.toLowerFirstChar;

final class XmlSchemaWriter
{
    private XmlSchemaWriter()
    {
    }

    public static String writeString(final SchemaDomain.MessageSchema schema)
    {
        final StringWriter writer = new StringWriter();
        final StreamResult result = new StreamResult(writer);
        writeTo(schema, result);
        return writer.toString();
    }

    public static void writeFile(
        final SchemaDomain.MessageSchema schema,
        final File destination)
    {
        final StreamResult result = new StreamResult(destination);
        writeTo(schema, result);
    }

    private static void writeTo(
        final SchemaDomain.MessageSchema schema,
        final StreamResult destination)
    {
        try
        {
            final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

            final Element root = document.createElementNS("http://fixprotocol.io/2016/sbe", "sbe:messageSchema");
            root.setAttribute("id", "42");
            root.setAttribute("package", "uk.co.real_logic.sbe.properties");
            document.appendChild(root);

            final Element topLevelTypes = createTypesElement(document);
            root.appendChild(topLevelTypes);

            final HashMap<SchemaDomain.TypeSchema, String> typeToName = new HashMap<>();

            final TypeSchemaConverter typeSchemaConverter = new TypeSchemaConverter(
                document,
                topLevelTypes,
                typeToName
            );

            appendTypes(
                topLevelTypes,
                typeSchemaConverter,
                schema.blockFields(),
                schema.groups());

            final Element message = document.createElement("sbe:message");
            message.setAttribute("name", "TestMessage");
            message.setAttribute("id", "1");
            root.appendChild(message);
            final MutableInteger nextMemberId = new MutableInteger(1);
            appendMembers(
                document,
                typeToName,
                schema.blockFields(),
                schema.groups(),
                schema.varData(),
                nextMemberId,
                message);

            try
            {
                final Transformer transformer = TransformerFactory.newInstance().newTransformer();

                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

                final DOMSource source = new DOMSource(document);

                transformer.transform(source, destination);
            }
            catch (final Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        catch (final ParserConfigurationException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static void appendMembers(
        final Document document,
        final HashMap<SchemaDomain.TypeSchema, String> typeToName,
        final List<SchemaDomain.TypeSchema> blockFields,
        final List<SchemaDomain.GroupSchema> groups,
        final List<SchemaDomain.VarDataSchema> varData,
        final MutableInteger nextMemberId,
        final Element parent)
    {
        for (final SchemaDomain.TypeSchema field : blockFields)
        {
            final int id = nextMemberId.getAndIncrement();

            final boolean usePrimitiveName = field.isEmbedded() && field instanceof SchemaDomain.EncodedDataTypeSchema;
            final String typeName = usePrimitiveName ?
                ((SchemaDomain.EncodedDataTypeSchema)field).primitiveType().primitiveName() :
                requireNonNull(typeToName.get(field));

            final Element element = document.createElement("field");
            element.setAttribute("id", Integer.toString(id));
            element.setAttribute("name", "member" + id);
            element.setAttribute("type", typeName);
            parent.appendChild(element);
        }

        for (final SchemaDomain.GroupSchema group : groups)
        {
            final int id = nextMemberId.getAndIncrement();

            final Element element = document.createElement("group");
            element.setAttribute("id", Integer.toString(id));
            element.setAttribute("name", "member" + id);
            appendMembers(
                document,
                typeToName,
                group.blockFields(),
                group.groups(),
                group.varData(),
                nextMemberId,
                element);
            parent.appendChild(element);
        }

        for (final SchemaDomain.VarDataSchema data : varData)
        {
            final int id = nextMemberId.getAndIncrement();

            final Element element = document.createElement("data");
            element.setAttribute("id", Integer.toString(id));
            element.setAttribute("name", "member" + id);
            switch (data.encoding())
            {
                case ASCII:
                    element.setAttribute("type", "varStringEncoding");
                    break;
                case BYTES:
                    element.setAttribute("type", "varDataEncoding");
                    break;
                default:
                    throw new IllegalStateException("Unknown encoding: " + data.encoding());
            }
            parent.appendChild(element);
        }
    }

    private static Element createTypesElement(final Document document)
    {
        final Element types = document.createElement("types");

        types.appendChild(createCompositeElement(
            document,
            "messageHeader",
            createTypeElement(document, "blockLength", "uint16"),
            createTypeElement(document, "templateId", "uint16"),
            createTypeElement(document, "schemaId", "uint16"),
            createTypeElement(document, "version", "uint16")
        ));

        types.appendChild(createCompositeElement(
            document,
            "groupSizeEncoding",
            createTypeElement(document, "blockLength", "uint16"),
            createTypeElement(document, "numInGroup", "uint16")
        ));

        final Element varString = createTypeElement(document, "varData", "uint8");
        varString.setAttribute("length", "0");
        varString.setAttribute("characterEncoding", "US-ASCII");
        types.appendChild(createCompositeElement(
            document,
            "varStringEncoding",
            createTypeElement(document, "length", "uint16"),
            varString
        ));

        final Element varData = createTypeElement(document, "varData", "uint8");
        final Element varDataLength = createTypeElement(document, "length", "uint32");
        varDataLength.setAttribute("maxValue", "1000000");
        varData.setAttribute("length", "0");
        types.appendChild(createCompositeElement(
            document,
            "varDataEncoding",
            varDataLength,
            varData
        ));

        return types;
    }

    private static Element createSetElement(
        final Document document,
        final String name,
        final String encodingType,
        final int choiceCount)
    {
        final Element enumElement = document.createElement("set");
        enumElement.setAttribute("name", name);
        enumElement.setAttribute("encodingType", encodingType);

        int caseId = 0;
        for (int i = 0; i < choiceCount; i++)
        {
            final Element choice = document.createElement("choice");
            choice.setAttribute("name", "option" + caseId++);
            choice.setTextContent(Integer.toString(i));
            enumElement.appendChild(choice);
        }

        return enumElement;
    }

    private static Element createEnumElement(
        final Document document,
        final String name,
        final String encodingType,
        final List<String> validValues)
    {
        final Element enumElement = document.createElement("enum");
        enumElement.setAttribute("name", name);
        enumElement.setAttribute("encodingType", encodingType);

        int caseId = 0;
        for (final String value : validValues)
        {
            final Element validValue = document.createElement("validValue");
            validValue.setAttribute("name", "Case" + caseId++);
            validValue.setTextContent(value);
            enumElement.appendChild(validValue);
        }

        return enumElement;
    }

    private static Element createCompositeElement(
        final Document document,
        final String name,
        final Element... types
    )
    {
        final Element composite = document.createElement("composite");
        composite.setAttribute("name", name);

        for (final Element type : types)
        {
            composite.appendChild(type);
        }

        return composite;
    }

    private static Element createTypeElement(
        final Document document,
        final String name,
        final String primitiveType)
    {
        final Element blockLength = document.createElement("type");
        blockLength.setAttribute("name", name);
        blockLength.setAttribute("primitiveType", primitiveType);
        return blockLength;
    }

    private static Element createRefElement(
        final Document document,
        final String name,
        final String type)
    {
        final Element blockLength = document.createElement("ref");
        blockLength.setAttribute("name", name);
        blockLength.setAttribute("type", type);
        return blockLength;
    }

    private static void appendTypes(
        final Element topLevelTypes,
        final TypeSchemaConverter typeSchemaConverter,
        final List<SchemaDomain.TypeSchema> blockFields,
        final List<SchemaDomain.GroupSchema> groups)
    {
        for (final SchemaDomain.TypeSchema field : blockFields)
        {
            if (!field.isEmbedded())
            {
                topLevelTypes.appendChild(typeSchemaConverter.convert(field));
            }
        }

        for (final SchemaDomain.GroupSchema group : groups)
        {
            appendTypes(topLevelTypes, typeSchemaConverter, group.blockFields(), group.groups());
        }
    }

    private static final class TypeSchemaConverter implements SchemaDomain.TypeSchemaVisitor
    {
        private final Document document;
        private final Element topLevelTypes;
        private final Map<SchemaDomain.TypeSchema, String> typeToName;
        private final Function<SchemaDomain.TypeSchema, String> nextName;
        private Element result;

        private TypeSchemaConverter(
            final Document document,
            final Element topLevelTypes,
            final Map<SchemaDomain.TypeSchema, String> typeToName)
        {
            this.document = document;
            this.topLevelTypes = topLevelTypes;
            this.typeToName = typeToName;
            nextName = ignored -> "Type" + typeToName.size();
        }

        @Override
        public void onEncoded(final SchemaDomain.EncodedDataTypeSchema type)
        {
            result = createTypeElement(
                document,
                typeToName.computeIfAbsent(type, nextName),
                type.primitiveType().primitiveName()
            );
        }

        @Override
        public void onComposite(final SchemaDomain.CompositeTypeSchema type)
        {
            final Element[] parts = type.fields().stream()
                .map(this::embedOrReference)
                .toArray(Element[]::new);
            result = createCompositeElement(
                document,
                typeToName.computeIfAbsent(type, nextName),
                parts
            );
        }

        @Override
        public void onEnum(final SchemaDomain.EnumTypeSchema type)
        {
            result = createEnumElement(
                document,
                typeToName.computeIfAbsent(type, nextName),
                type.encodingType(),
                type.validValues()
            );
        }

        @Override
        public void onSet(final SchemaDomain.SetSchema type)
        {
            result = createSetElement(
                document,
                typeToName.computeIfAbsent(type, nextName),
                type.encodingType(),
                type.choiceCount()
            );
        }

        private Element embedOrReference(final SchemaDomain.TypeSchema type)
        {
            if (type.isEmbedded())
            {
                return convert(type);
            }
            else
            {
                final boolean hasWritten = typeToName.containsKey(type);
                if (!hasWritten)
                {
                    topLevelTypes.appendChild(convert(type));
                }

                final String typeName = requireNonNull(typeToName.get(type));
                return createRefElement(
                    document,
                    toLowerFirstChar(typeName),
                    typeName
                );
            }
        }

        public Element convert(final SchemaDomain.TypeSchema type)
        {
            result = null;
            type.accept(this);
            return requireNonNull(result);
        }
    }
}
