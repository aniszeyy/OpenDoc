/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.util;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Conversion des numeros de serie Excel en date/heure.
 *
 * <p>Systeme 1900 : serie 1 = 01/01/1900, avec le bug historique du
 * 29/02/1900 (annee bissextile fictive). Systeme 1904 : serie 0 = 01/01/1904.</p>
 *
 * <p>On utilise GregorianCalendar (pas java.time, indisponible avant API 26).</p>
 */
public final class ExcelDate {

    /** Date/heure decomposee, prete pour un formatage manuel. */
    public static final class Parts {
        public int year;
        public int month;      // 1..12
        public int day;
        public int hour;
        public int minute;
        public int second;
        public int millis;
        public int dayOfWeek;  // 1 = dimanche ... 7 = samedi (comme Excel)
        public long totalDays;
    }

    private ExcelDate() {
    }

    public static Parts fromSerial(double serial, boolean date1904) {
        long whole = (long) Math.floor(serial);
        double fraction = serial - whole;
        if (fraction < 0) fraction = 0;

        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US);
        cal.clear();
        if (date1904) {
            cal.set(1904, Calendar.JANUARY, 1, 0, 0, 0);
            cal.add(Calendar.DAY_OF_MONTH, (int) whole);
        } else if (whole == 60) {
            // 29 fevrier 1900 n'existe pas : Excel l'affiche quand meme.
            cal.set(1900, Calendar.FEBRUARY, 28, 0, 0, 0);
        } else {
            if (whole < 60) {
                cal.set(1899, Calendar.DECEMBER, 31, 0, 0, 0);
            } else {
                cal.set(1899, Calendar.DECEMBER, 30, 0, 0, 0);
            }
            cal.add(Calendar.DAY_OF_MONTH, (int) whole);
        }

        long millisOfDay = Math.round(fraction * 86400000d);
        if (millisOfDay >= 86400000L) millisOfDay = 86399999L;
        cal.add(Calendar.MILLISECOND, (int) millisOfDay);

        Parts p = new Parts();
        p.year = cal.get(Calendar.YEAR);
        p.month = cal.get(Calendar.MONTH) + 1;
        p.day = cal.get(Calendar.DAY_OF_MONTH);
        p.hour = cal.get(Calendar.HOUR_OF_DAY);
        p.minute = cal.get(Calendar.MINUTE);
        p.second = cal.get(Calendar.SECOND);
        p.millis = cal.get(Calendar.MILLISECOND);
        p.dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        p.totalDays = whole;
        return p;
    }

    /** Une serie plausible pour une date (1 = 1900, ~2958465 = 9999). */
    public static boolean isPlausibleSerial(double serial) {
        return serial >= 0 && serial < 2958466d;
    }

    public static String isoDate(double serial, boolean date1904) {
        Parts p = fromSerial(serial, date1904);
        return String.format(Locale.US, "%04d-%02d-%02d", p.year, p.month, p.day);
    }
}
