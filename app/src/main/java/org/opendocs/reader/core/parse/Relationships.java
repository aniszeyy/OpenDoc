/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.parse;

import org.opendocs.reader.core.xml.XmlNode;
import org.opendocs.reader.core.xml.XmlTree;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Relations OPC (_rels/*.rels) : identifiant rId -> cible + type.
 * Sert a retrouver les images, les feuilles, les diapositives, les hyperliens.
 */
public final class Relationships {

    public static final class Rel {
        public final String id;
        public final String type;
        public final String target;
        public final boolean external;

        Rel(String id, String type, String target, boolean external) {
            this.id = id;
            this.type = type;
            this.target = target;
            this.external = external;
        }

        /** Dernier segment du type ("image", "hyperlink", "worksheet"...). */
        public String shortType() {
            int slash = type.lastIndexOf('/');
            return slash < 0 ? type : type.substring(slash + 1);
        }
    }

    private static final Relationships EMPTY = new Relationships();

    private final Map<String, Rel> byId = new HashMap<>();

    public static Relationships empty() {
        return EMPTY;
    }

    public static Relationships parse(byte[] data) throws IOException {
        Relationships out = new Relationships();
        if (data == null) return out;
        XmlNode root = XmlTree.parse(data);
        for (XmlNode rel : root.children("Relationship")) {
            String id = rel.attr("Id");
            if (id == null) continue;
            String mode = rel.attr("TargetMode", "Internal");
            out.byId.put(id, new Rel(id, rel.attr("Type", ""), rel.attr("Target", ""),
                    "External".equalsIgnoreCase(mode)));
        }
        return out;
    }

    /** Charge le fichier .rels associe a une partie ("word/document.xml"). */
    public static Relationships forPart(OoxmlPackage pkg, String partPath) throws IOException {
        return parse(pkg.read(relsPath(partPath)));
    }

    public static String relsPath(String partPath) {
        int slash = partPath.lastIndexOf('/');
        String dir = slash < 0 ? "" : partPath.substring(0, slash + 1);
        String file = slash < 0 ? partPath : partPath.substring(slash + 1);
        return dir + "_rels/" + file + ".rels";
    }

    public Rel get(String id) {
        return id == null ? null : byId.get(id);
    }

    public String target(String id) {
        Rel rel = get(id);
        return rel == null ? null : rel.target;
    }

    public boolean isExternal(String id) {
        Rel rel = get(id);
        return rel != null && rel.external;
    }

    public Map<String, Rel> all() {
        return byId;
    }

    /** Cible resolue en chemin d'entree ZIP, relative a la partie donnante. */
    public String resolve(String partPath, String id) {
        Rel rel = get(id);
        if (rel == null) return null;
        if (rel.external) return rel.target;
        return resolvePath(partPath, rel.target);
    }

    /**
     * Normalise une cible OPC : "media/img.png" depuis "word/document.xml"
     * donne "word/media/img.png" ; "../docProps/core.xml" donne "docProps/core.xml".
     */
    public static String resolvePath(String partPath, String target) {
        if (target == null || target.isEmpty()) return null;
        if (target.startsWith("http://") || target.startsWith("https://")
                || target.startsWith("mailto:") || target.startsWith("file:")) {
            return target;
        }
        if (target.startsWith("/")) return target.substring(1);
        String base = "";
        int slash = partPath.lastIndexOf('/');
        if (slash >= 0) base = partPath.substring(0, slash + 1);
        String combined = base + target;
        String[] segments = combined.split("/");
        java.util.ArrayDeque<String> stack = new java.util.ArrayDeque<>();
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment)) continue;
            if ("..".equals(segment)) {
                if (!stack.isEmpty()) stack.removeLast();
            } else {
                stack.addLast(segment);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String segment : stack) {
            if (sb.length() > 0) sb.append('/');
            sb.append(segment);
        }
        return sb.toString();
    }
}
