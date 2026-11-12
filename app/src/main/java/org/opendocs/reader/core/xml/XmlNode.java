/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.xml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Noeud XML minimal : nom local, attributs, enfants, texte direct.
 *
 * <p>On n'utilise ni org.w3c.dom ni android.util.Xml : cette structure est
 * volontairement minimale pour rester rapide, portable (JVM + Android) et
 * sans aucune dependance externe. Les prefixes de namespace sont ignores
 * ("w:p" -> "p"), ce qui suffit pour OOXML ou les noms locaux sont uniques
 * par contexte.</p>
 */
public final class XmlNode {

    private final String name;
    private final Map<String, String> attributes = new LinkedHashMap<>(4);
    private final List<XmlNode> children = new ArrayList<>(0);
    private StringBuilder text;

    public XmlNode(String name) {
        this.name = name == null ? "" : name;
    }

    /* ------------------------------------------------------------------ */
    /* Construction (package prive : utilise par XmlTree)                  */
    /* ------------------------------------------------------------------ */

    void putAttribute(String qualifiedName, String value) {
        if (qualifiedName == null) return;
        attributes.put(qualifiedName, value);
        String local = stripPrefix(qualifiedName);
        if (!local.equals(qualifiedName) && !attributes.containsKey(local)) {
            attributes.put(local, value);
        }
    }

    void addText(String chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        if (text == null) text = new StringBuilder(chunk.length());
        text.append(chunk);
    }

    void addChild(XmlNode child) {
        children.add(child);
    }

    /* ------------------------------------------------------------------ */
    /* Acces                                                               */
    /* ------------------------------------------------------------------ */

    public String name() {
        return name;
    }

    public Map<String, String> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /** Attribut par nom local ("id" trouve aussi "r:id"). */
    public String attr(String localName) {
        String v = attributes.get(localName);
        if (v != null) return v;
        for (Map.Entry<String, String> e : attributes.entrySet()) {
            if (stripPrefix(e.getKey()).equals(localName)) return e.getValue();
        }
        return null;
    }

    public String attr(String localName, String fallback) {
        String v = attr(localName);
        return v == null ? fallback : v;
    }

    public int intAttr(String localName, int fallback) {
        String v = attr(localName);
        if (v == null) return fallback;
        try {
            return (int) Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public long longAttr(String localName, long fallback) {
        String v = attr(localName);
        if (v == null) return fallback;
        try {
            return (long) Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public double doubleAttr(String localName, double fallback) {
        String v = attr(localName);
        if (v == null) return fallback;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Booleens OOXML : absent = defaut, "1"/"true"/"on" = vrai, "0"/"false"/"off" = faux. */
    public boolean boolAttr(String localName, boolean fallback) {
        String v = attr(localName);
        if (v == null) return fallback;
        v = v.trim();
        if (v.isEmpty()) return true;
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v);
    }

    public List<XmlNode> children() {
        return Collections.unmodifiableList(children);
    }

    public List<XmlNode> children(String childName) {
        List<XmlNode> out = new ArrayList<>(2);
        for (XmlNode c : children) {
            if (c.name.equals(childName)) out.add(c);
        }
        return out;
    }

    public XmlNode first(String childName) {
        for (XmlNode c : children) {
            if (c.name.equals(childName)) return c;
        }
        return null;
    }

    /** Chemin relatif : {@code find("pPr/rPr/b")}. Renvoie null si un segment manque. */
    public XmlNode find(String path) {
        XmlNode current = this;
        int from = 0;
        while (current != null && from <= path.length()) {
            int slash = path.indexOf('/', from);
            String segment = slash < 0 ? path.substring(from) : path.substring(from, slash);
            current = current.first(segment);
            if (slash < 0) break;
            from = slash + 1;
        }
        return current;
    }

    /** Tous les descendants portant ce nom (parcours en profondeur, ordre document). */
    public List<XmlNode> all(String descendantName) {
        List<XmlNode> out = new ArrayList<>(2);
        collect(this, descendantName, out);
        return out;
    }

    private static void collect(XmlNode node, String wanted, List<XmlNode> out) {
        for (XmlNode c : node.children) {
            if (c.name.equals(wanted)) out.add(c);
            collect(c, wanted, out);
        }
    }

    /** Texte direct du noeud (sans les descendants). */
    public String text() {
        return text == null ? "" : text.toString();
    }

    /** Texte du noeud et de tous ses descendants. */
    public String deepText() {
        StringBuilder sb = new StringBuilder();
        appendDeep(this, sb);
        return sb.toString();
    }

    private static void appendDeep(XmlNode node, StringBuilder sb) {
        if (node.text != null) sb.append(node.text);
        for (XmlNode c : node.children) appendDeep(c, sb);
    }

    public boolean isEmpty() {
        return children.isEmpty() && (text == null || text.length() == 0);
    }

    public static String stripPrefix(String qualifiedName) {
        int colon = qualifiedName.indexOf(':');
        return colon < 0 ? qualifiedName : qualifiedName.substring(colon + 1);
    }

    @Override
    public String toString() {
        return "<" + name + " attrs=" + attributes.size() + " children=" + children.size() + ">";
    }
}
