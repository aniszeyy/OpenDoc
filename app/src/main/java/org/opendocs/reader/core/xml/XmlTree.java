/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.xml;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Construit un {@link XmlNode} a partir d'un flux SAX, ou diffuse les elements
 * d'un type donne en streaming (indispensable pour les grosses feuilles XLSX).
 *
 * <p>SAX (javax.xml.parsers) fait partie de la plateforme Android et du JDK :
 * aucune dependance a ajouter, et le meme code tourne en test unitaire JVM.</p>
 *
 * <p>Protections : DTD desactivees (XXE), entites externes neutralisees,
 * limites de profondeur / nombre de noeuds / volume de texte (zip bomb).</p>
 */
public final class XmlTree {

    /** Nombre maximal de noeuds construits pour un document. */
    public static final int MAX_NODES = 2_000_000;
    /** Profondeur maximale (protection contre les documents malformes). */
    public static final int MAX_DEPTH = 256;
    /** Volume maximal de caracteres accumules. */
    public static final long MAX_CHARS = 64L * 1024 * 1024;

    private XmlTree() {
    }

    public static XmlNode parse(byte[] data) throws IOException {
        return parse(new ByteArrayInputStream(data));
    }

    public static XmlNode parse(InputStream in) throws IOException {
        Builder builder = new Builder(null, null);
        run(in, builder);
        return builder.root == null ? new XmlNode("empty") : builder.root;
    }

    /**
     * Parse en streaming : chaque element {@code elementName} est assemble puis
     * passe au consommateur, sans jamais garder le document entier en memoire.
     */
    public static void stream(InputStream in, String elementName, Consumer<XmlNode> consumer) throws IOException {
        Set<String> names = new HashSet<>(2);
        names.add(elementName);
        run(in, new Builder(names, (name, node) -> consumer.accept(node)));
    }

    /**
     * Streaming multi-elements : indispensable pour XLSX ou l'on veut lire
     * les lignes ("row"), les largeurs ("col") et les fusions ("mergeCell")
     * en une seule passe, sans charger la feuille complete.
     */
    public static void stream(InputStream in, Set<String> elementNames, BiConsumer<String, XmlNode> consumer)
            throws IOException {
        run(in, new Builder(elementNames, consumer));
    }

    private static void run(InputStream in, Builder builder) throws IOException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            configure(factory);
            SAXParser parser = factory.newSAXParser();
            InputSource source = new InputSource(in);
            parser.parse(source, builder);
        } catch (SAXException e) {
            throw new IOException("XML invalide : " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IOException("Lecture XML impossible : " + describe(e), e);
        }
    }

    /**
     * Configure le parseur en tolerant les capacites absentes : chaque appel
     * optionnel peut etre refuse par l'implementation de la plateforme.
     */
    static void configure(SAXParserFactory factory) {
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        setXIncludeQuietly(factory);
        setFeatureQuietly(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeatureQuietly(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureQuietly(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureQuietly(factory, "http://xml.org/sax/features/external-parameter-entities", false);
    }

    /**
     * Android (org.apache.harmony.xml.parsers.SAXParserFactoryImpl) n'implemente
     * pas setXIncludeAware : la classe de base JAXP leve alors
     * UnsupportedOperationException, avec un message trompeur du genre
     * "This parser does not support specification "Unknown" version "0.0"".
     * XInclude etant desactive par defaut, ignorer le refus est sans consequence ;
     * sans ce garde-fou, tout document OOXML echouait a l'ouverture sur Android
     * alors que les tests JVM (Xerces, qui l'implemente) passaient.
     */
    private static void setXIncludeQuietly(SAXParserFactory factory) {
        try {
            factory.setXIncludeAware(false);
        } catch (Exception ignored) {
            // deja desactive par defaut
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getName() : message;
    }

    private static void setFeatureQuietly(SAXParserFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // Toutes les implementations SAX (Android inclus) ne connaissent pas ces features.
        }
    }

    private static final class Builder extends DefaultHandler {

        private final Set<String> targets;
        private final BiConsumer<String, XmlNode> consumer;
        private final Deque<XmlNode> stack = new ArrayDeque<>();

        private XmlNode root;
        private int depth;
        private int targetDepth = -1;
        private boolean building;
        private int nodes;
        private long chars;

        Builder(Set<String> targets, BiConsumer<String, XmlNode> consumer) {
            this.targets = targets;
            this.consumer = consumer;
            this.building = targets == null;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            String name = localName == null || localName.isEmpty() ? XmlNode.stripPrefix(qName) : localName;
            int previousDepth = depth;
            depth++;
            if (depth > MAX_DEPTH) throw new SAXException("imbrication XML trop profonde (> " + MAX_DEPTH + ")");

            if (!building) {
                if (!targets.contains(name)) return;
                building = true;
                targetDepth = previousDepth;
            }

            if (++nodes > MAX_NODES) throw new SAXException("document XML trop volumineux (> " + MAX_NODES + " noeuds)");

            XmlNode node = new XmlNode(name);
            for (int i = 0; i < attributes.getLength(); i++) {
                String key = attributes.getQName(i);
                if (key == null || key.isEmpty()) key = attributes.getLocalName(i);
                node.putAttribute(key, attributes.getValue(i));
            }
            if (stack.isEmpty()) {
                if (root == null) root = node;
            } else {
                stack.peek().addChild(node);
            }
            stack.push(node);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (!building || stack.isEmpty() || length == 0) return;
            chars += length;
            if (chars > MAX_CHARS) throw new SAXException("trop de texte XML (> " + MAX_CHARS + " caracteres)");
            stack.peek().addText(new String(ch, start, length));
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            depth--;
            if (!building) return;
            XmlNode node = stack.isEmpty() ? null : stack.pop();
            if (targets != null && depth == targetDepth) {
                building = false;
                targetDepth = -1;
                if (node != null && consumer != null) consumer.accept(node.name(), node);
            }
        }

        @Override
        public InputSource resolveEntity(String publicId, String systemId) {
            // Aucune entite externe n'est jamais chargee (anti-XXE).
            return new InputSource(new StringReader(""));
        }
    }
}
