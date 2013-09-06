/* -*- mode: java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil -*- */
/*
 * Copyright 2013 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.sbe;

import org.w3c.dom.Node;

/**
 * SBE encodedDataType
 */
public class EncodedDataType extends Type
{
    /** The primitiveType this Type encodes as */
    private final Primitive primitive;

    /** Number of elements of the primitive type */
    private final int length;

    /** variable length or not */
    private final boolean varLen;

    /**
     * Construct a new encodedDataType from XML Schema.
     *
     * @param node from the XML Schema Parsing
     */
    public EncodedDataType(final Node node)
    {
        super(node); // set the common schema attributes

	/**
	 * Grab schema attributes
	 * - primitiveType (required)
	 * - length (default = 1)
	 * - variableLength (default = false)
	 *
	 * TODO:
	 * - nullValue (optional)
	 * - minValue (optional)
	 * - maxValue (optional)
	 */
	this.primitive = Primitive.lookup(XMLSchemaParser.getXMLAttributeValue(node, "primitiveType"));
	this.length = Integer.parseInt(XMLSchemaParser.getXMLAttributeValue(node, "length"));
	this.varLen = Boolean.parseBoolean(XMLSchemaParser.getXMLAttributeValue(node, "variableLength"));
	// TODO: handle nullValue (mutually exclusive with presence of required and optional), minValue, and maxValue
    }
}
