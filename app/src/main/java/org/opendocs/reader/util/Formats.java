/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.util;

import org.opendocs.reader.core.model.DocType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Mises en forme partagees par l'interface (tailles, dates, durees). */
public final class Formats {

    private Formats() {
    }

    public static String size(long bytes) {
        if (bytes < 0) return "-";
        if (bytes < 1024) return bytes + " o";
        double value = bytes / 1024.0;
        if (value < 1024) return trim(value) + " Ko";
        value /= 1024.0;
        if (value < 1024) return trim(value) + " Mo";
        return trim(value / 1024.0) + " Go";
    }

    private static String trim(double value) {
        if (value >= 100) return String.format(Locale.getDefault(), "%.0f", value);
        if (value >= 10) return String.format(Locale.getDefault(), "%.1f", value);
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    public static String date(long millis) {
        if (millis <= 0) return "";
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault());
        return fmt.format(new Date(millis));
    }

    public static String duration(long millis) {
        if (millis < 1000) return millis + " ms";
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        if (seconds < 60) return trim(seconds + millis % 1000 / 1000.0) + " s";
        return (seconds / 60) + " min " + (seconds % 60) + " s";
    }

    public static String typeLabel(DocType type) {
        if (type == null || type == DocType.UNKNOWN) return "?";
        return type.extension().toUpperCase(Locale.ROOT);
    }
}
