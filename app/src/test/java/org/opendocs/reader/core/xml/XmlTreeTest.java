/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Le parseur SAX d'Android ne connait ni setXIncludeAware ni les features Xerces.
 * La JVM de test utilise Xerces, qui les implemente toutes : sans cette
 * implementation factice, le bug ne se voit que sur un appareil.
 */
public class XmlTreeTest {

    /** Reproduit le SAXParserFactory de la plateforme Android. */
    private static final class AndroidLikeSaxParserFactory extends SAXParserFactory {

        boolean xIncludeRefused;
        boolean featureRefused;

        @Override
        public void setXIncludeAware(boolean state) {
            xIncludeRefused = true;
            throw new UnsupportedOperationException(
                    "This parser does not support specification \"Unknown\" version \"0.0\"");
        }

        @Override
        public void setFeature(String name, boolean value)
                throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
            featureRefused = true;
            throw new SAXNotRecognizedException(name);
        }

        @Override
        public boolean getFeature(String name)
                throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
            throw new SAXNotRecognizedException(name);
        }

        @Override
        public SAXParser newSAXParser() throws ParserConfigurationException {
            throw new ParserConfigurationException("non utilise dans ce test");
        }
    }

    @Test
    public void configureTolereUnParseurSansXInclude() {
        AndroidLikeSaxParserFactory factory = new AndroidLikeSaxParserFactory();

        XmlTree.configure(factory);

        assertTrue("setXIncludeAware doit avoir ete tente", factory.xIncludeRefused);
        assertTrue("les features optionnelles doivent avoir ete tentees", factory.featureRefused);
        assertTrue("le mode namespace reste indispensable", factory.isNamespaceAware());
    }

    @Test
    public void parseLitUnDocumentSimple() throws IOException {
        XmlNode root = XmlTree.parse("<a x=\"1\"><b>texte</b></a>".getBytes("UTF-8"));

        assertEquals("a", root.name());
        assertEquals("1", root.attr("x"));
        assertEquals("texte", root.first("b").text());
    }
}
