/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Petits utilitaires texte partages (encodage, HTML, jointures). */
public final class TextUtil {

    private TextUtil() {
    }

    public static String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Decode un fichier texte : BOM UTF-8/UTF-16, sinon UTF-8 valide,
     * sinon repli ISO-8859-1 (les CSV Excel francais sont souvent en Latin-1).
     */
    public static String decode(byte[] data) {
        if (data == null || data.length == 0) return "";
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            return new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16LE);
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16BE);
        }
        if (isValidUtf8(data)) return new String(data, StandardCharsets.UTF_8);
        return new String(data, Charset.forName("ISO-8859-1"));
    }

    /** Validation UTF-8 stricte (sans dependance a CharsetDecoder). */
    public static boolean isValidUtf8(byte[] data) {
        int i = 0;
        while (i < data.length) {
            int b = data[i] & 0xFF;
            int expected;
            if (b <= 0x7F) {
                i++;
                continue;
            } else if (b >= 0xC2 && b <= 0xDF) {
                expected = 1;
            } else if (b >= 0xE0 && b <= 0xEF) {
                expected = 2;
            } else if (b >= 0xF0 && b <= 0xF4) {
                expected = 3;
            } else {
                return false;
            }
            if (i + expected >= data.length) return false;
            for (int k = 1; k <= expected; k++) {
                int next = data[i + k] & 0xFF;
                if (next < 0x80 || next > 0xBF) return false;
            }
            i += expected + 1;
        }
        return true;
    }

    /** String.join n'existe qu'a partir d'API 26 : jointure manuelle. */
    public static String join(List<String> parts, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    public static String escapeHtml(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#39;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Echappe une valeur CSV (RFC 4180). */
    public static String escapeCsv(String value, char delimiter) {
        String v = safe(value);
        boolean mustQuote = v.indexOf(delimiter) >= 0 || v.indexOf('"') >= 0
                || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        if (!mustQuote) return v;
        StringBuilder sb = new StringBuilder(v.length() + 8).append('"');
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '"') sb.append('"');
            sb.append(c);
        }
        return sb.append('"').toString();
    }

    /** Nom de fichier sur pour le stockage (pas de separateurs ni caracteres de controle). */
    public static String sanitizeFileName(String name) {
        String base = safe(name).trim();
        if (base.isEmpty()) base = "document";
        StringBuilder sb = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (c < 32 || c == '/' || c == '\\' || c == ':' || c == '*' || c == '?'
                    || c == '"' || c == '<' || c == '>' || c == '|') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        String out = sb.toString();
        return out.length() > 120 ? out.substring(0, 120) : out;
    }

    /** Retire l'extension d'un nom de fichier. */
    public static String withoutExtension(String name) {
        String base = safe(name);
        int dot = base.lastIndexOf('.');
        return dot <= 0 ? base : base.substring(0, dot);
    }

    public static String ellipsize(String text, int max) {
        String t = safe(text);
        if (t.length() <= max) return t;
        return t.substring(0, Math.max(0, max - 1)) + "\u2026";
    }
}
