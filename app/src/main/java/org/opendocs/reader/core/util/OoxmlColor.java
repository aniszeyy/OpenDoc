/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.util;

import org.opendocs.reader.core.xml.XmlNode;

import java.util.Locale;

/**
 * Couleurs OOXML : srgbClr, schemeClr (theme Office par defaut), modificateurs
 * (tint, shade, lumMod, lumOff, alpha) et surlignages Word.
 *
 * <p>Convention du projet : un int ARGB, 0 signifiant "non defini / heriter".</p>
 */
public final class OoxmlColor {

    public static final int NONE = 0;

    private OoxmlColor() {
    }

    /** "FF0000" -> 0xFFFF0000 ; "auto"/null -> NONE. */
    public static int parseHex(String value) {
        if (value == null) return NONE;
        String v = value.trim();
        if (v.isEmpty() || "auto".equalsIgnoreCase(v) || "none".equalsIgnoreCase(v)) return NONE;
        if (v.startsWith("#")) v = v.substring(1);
        if (v.length() != 6 && v.length() != 8) return NONE;
        try {
            long rgb = Long.parseLong(v, 16);
            if (v.length() == 6) return (int) (0xFF000000L | rgb);
            return (int) rgb;
        } catch (NumberFormatException e) {
            return NONE;
        }
    }

    /**
     * Couleur d'un noeud w:color / a:solidFill : lit srgbClr ou schemeClr,
     * puis applique les modificateurs enfants.
     */
    public static int fromContainer(XmlNode container) {
        if (container == null) return NONE;
        XmlNode srgb = container.first("srgbClr");
        if (srgb != null) return applyModifiers(srgb, parseHex(srgb.attr("val")));
        XmlNode scheme = container.first("schemeClr");
        if (scheme != null) {
            int base = themeColor(scheme.attr("val"));
            return applyModifiers(scheme, base);
        }
        XmlNode sys = container.first("sysClr");
        if (sys != null) return parseHex(sys.attr("lastClr", sys.attr("val")));
        XmlNode prst = container.first("prstClr");
        if (prst != null) return presetColor(prst.attr("val"));
        return NONE;
    }

    /** Palette du theme Office standard (approximation raisonnable, themes custom non lus). */
    public static int themeColor(String name) {
        if (name == null) return NONE;
        switch (name) {
            case "dk1":
            case "tx1":
            case "bg2":
                return 0xFF000000;
            case "lt1":
            case "bg1":
                return 0xFFFFFFFF;
            case "dk2":
            case "tx2":
                return 0xFF1F497D;
            case "lt2":
                return 0xFFEEECE1;
            case "accent1":
                return 0xFF4F81BD;
            case "accent2":
                return 0xFFC0504D;
            case "accent3":
                return 0xFF9BBB59;
            case "accent4":
                return 0xFF8064A2;
            case "accent5":
                return 0xFF4BACC6;
            case "accent6":
                return 0xFFF79646;
            case "hlink":
                return 0xFF0000FF;
            case "folHlink":
                return 0xFF800080;
            case "phClr":
                return NONE;
            default:
                return NONE;
        }
    }

    /** Applique tint / shade / lumMod / lumOff / satMod / alpha (valeurs en 1/100000). */
    public static int applyModifiers(XmlNode colorNode, int argb) {
        if (colorNode == null || argb == NONE) return argb;
        int a = (argb >>> 24) & 0xFF;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        XmlNode tint = colorNode.first("tint");
        if (tint != null) {
            float t = (float) (tint.doubleAttr("val", 0) / 100000.0);
            if (t > 0) {
                r = r * (1 - t) + t;
                g = g * (1 - t) + t;
                b = b * (1 - t) + t;
            } else {
                r *= (1 + t);
                g *= (1 + t);
                b *= (1 + t);
            }
        }
        XmlNode shade = colorNode.first("shade");
        if (shade != null) {
            float s = (float) (shade.doubleAttr("val", 100000) / 100000.0);
            r *= s;
            g *= s;
            b *= s;
        }
        XmlNode lumMod = colorNode.first("lumMod");
        XmlNode lumOff = colorNode.first("lumOff");
        if (lumMod != null || lumOff != null) {
            float mod = lumMod == null ? 1f : (float) (lumMod.doubleAttr("val", 100000) / 100000.0);
            float off = lumOff == null ? 0f : (float) (lumOff.doubleAttr("val", 0) / 100000.0);
            float[] hsl = rgbToHsl(r, g, b);
            hsl[2] = clamp(hsl[2] * mod + off);
            float[] rgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
            r = rgb[0];
            g = rgb[1];
            b = rgb[2];
        }
        XmlNode satMod = colorNode.first("satMod");
        if (satMod != null) {
            float mod = (float) (satMod.doubleAttr("val", 100000) / 100000.0);
            float[] hsl = rgbToHsl(r, g, b);
            hsl[1] = clamp(hsl[1] * mod);
            float[] rgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
            r = rgb[0];
            g = rgb[1];
            b = rgb[2];
        }
        XmlNode alpha = colorNode.first("alpha");
        if (alpha != null) {
            a = (int) Math.round(a * (alpha.doubleAttr("val", 100000) / 100000.0));
        }
        return (clampByte(a) << 24) | (toByte(r) << 16) | (toByte(g) << 8) | toByte(b);
    }

    /** Surlignage Word (w:highlight val="yellow"). */
    public static int wordHighlight(String name) {
        if (name == null) return NONE;
        switch (name.toLowerCase(Locale.ROOT)) {
            case "yellow":
                return 0xFFFFFF00;
            case "green":
                return 0xFF00FF00;
            case "cyan":
                return 0xFF00FFFF;
            case "magenta":
                return 0xFFFF00FF;
            case "blue":
                return 0xFF0000FF;
            case "red":
                return 0xFFFF0000;
            case "darkBlue":
                return 0xFF000080;
            case "darkCyan":
                return 0xFF008080;
            case "darkGreen":
                return 0xFF008000;
            case "darkMagenta":
                return 0xFF800080;
            case "darkRed":
                return 0xFF800000;
            case "darkYellow":
                return 0xFF808000;
            case "darkGray":
                return 0xFF808080;
            case "lightGray":
                return 0xFFC0C0C0;
            case "black":
                return 0xFF000000;
            case "white":
                return 0xFFFFFFFF;
            default:
                return NONE;
        }
    }

    /** Couleurs nommees DrawingML (prstClr). */
    public static int presetColor(String name) {
        if (name == null) return NONE;
        switch (name.toLowerCase(Locale.ROOT)) {
            case "black":
                return 0xFF000000;
            case "white":
                return 0xFFFFFFFF;
            case "red":
                return 0xFFFF0000;
            case "green":
                return 0xFF008000;
            case "blue":
                return 0xFF0000FF;
            case "yellow":
                return 0xFFFFFF00;
            case "gray":
            case "grey":
                return 0xFF808080;
            case "orange":
                return 0xFFFFA500;
            case "cyan":
                return 0xFF00FFFF;
            case "magenta":
                return 0xFFFF00FF;
            default:
                return NONE;
        }
    }

    /** Melange lineaire (utilise pour les degrades et les fonds de tableau). */
    public static int blend(int c1, int c2, float ratio) {
        float r = clamp(ratio);
        int a = Math.round(((c1 >>> 24) & 0xFF) * (1 - r) + ((c2 >>> 24) & 0xFF) * r);
        int rr = Math.round(((c1 >> 16) & 0xFF) * (1 - r) + ((c2 >> 16) & 0xFF) * r);
        int gg = Math.round(((c1 >> 8) & 0xFF) * (1 - r) + ((c2 >> 8) & 0xFF) * r);
        int bb = Math.round((c1 & 0xFF) * (1 - r) + (c2 & 0xFF) * r);
        return (a << 24) | (rr << 16) | (gg << 8) | bb;
    }

    public static int withAlpha(int argb, int alpha) {
        return (clampByte(alpha) << 24) | (argb & 0x00FFFFFF);
    }

    public static String toHex(int argb) {
        return String.format(Locale.US, "#%02X%02X%02X", (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
    }

    /** Luminance relative (0..1) : sert a choisir une couleur de texte lisible. */
    public static float luminance(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static int clampByte(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static int toByte(float v) {
        return clampByte(Math.round(clamp(v) * 255));
    }

    private static float[] rgbToHsl(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float h;
        float s;
        float l = (max + min) / 2f;
        if (max == min) {
            h = 0;
            s = 0;
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6 : 0);
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h /= 6f;
        }
        return new float[]{h, s, l};
    }

    private static float[] hslToRgb(float h, float s, float l) {
        if (s == 0) return new float[]{l, l, l};
        float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
        float p = 2 * l - q;
        return new float[]{hueToRgb(p, q, h + 1f / 3f), hueToRgb(p, q, h), hueToRgb(p, q, h - 1f / 3f)};
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6f) return p + (q - p) * 6 * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6;
        return p;
    }
}
