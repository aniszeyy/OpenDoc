/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.util;

/** Conversions d'unites OOXML (EMU, twips, points, pixels logiques 96 dpi). */
public final class Emu {

    public static final long PER_INCH = 914400L;
    public static final long PER_POINT = 12700L;
    public static final long PER_PIXEL = 9525L;   // 96 dpi
    public static final long PER_CM = 360000L;
    public static final int TWIPS_PER_POINT = 20;
    public static final int TWIPS_PER_INCH = 1440;

    private Emu() {
    }

    public static float toPixels(long emu) {
        return emu / (float) PER_PIXEL;
    }

    public static float toPoints(long emu) {
        return emu / (float) PER_POINT;
    }

    public static long fromPoints(float points) {
        return (long) (points * PER_POINT);
    }

    public static float twipsToPoints(long twips) {
        return twips / (float) TWIPS_PER_POINT;
    }

    public static float twipsToPixels(long twips) {
        return twips / (float) TWIPS_PER_INCH * 96f;
    }

    /** Demi-points (w:sz) -> points. */
    public static float halfPointsToPoints(long halfPoints) {
        return halfPoints / 2f;
    }

    /** Centiemes de point (a:rPr sz="1800") -> points. */
    public static float centiPointsToPoints(long centiPoints) {
        return centiPoints / 100f;
    }
}
