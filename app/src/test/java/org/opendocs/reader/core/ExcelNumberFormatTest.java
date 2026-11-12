/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opendocs.reader.core.util.ExcelDate;
import org.opendocs.reader.core.util.ExcelNumberFormat;

/** Tests du moteur de formats numeriques Excel. */
public class ExcelNumberFormatTest {

    private static String f(double value, String code) {
        return ExcelNumberFormat.format(value, code, false);
    }

    @Test
    public void formatGeneral() {
        assertEquals("0", f(0, "General"));
        assertEquals("1234.5", f(1234.5, "General"));
        assertEquals("-42", f(-42, "General"));
        assertEquals("1000", f(1000, ""));
        assertEquals("0.000001", f(0.000001, "General"));
    }

    @Test
    public void entiersEtDecimales() {
        assertEquals("13", f(12.6, "0"));
        assertEquals("12.60", f(12.6, "0.00"));
        assertEquals("007", f(7, "000"));
        assertEquals(".5", f(0.5, "#.##"));
        assertEquals("1,234,567.89", f(1234567.891, "#,##0.00"));
        assertEquals("1,234", f(1234, "#,##0"));
    }

    @Test
    public void pourcentages() {
        assertEquals("12.3%", f(0.1234, "0.0%"));
        assertEquals("42%", f(0.42, "0%"));
        assertEquals("42.00%", f(0.42, "0.00%"));
    }

    @Test
    public void monnaiesEtLitteraux() {
        assertEquals("$1,234.50", f(1234.5, "$#,##0.00"));
        assertEquals("1,234.50 €", f(1234.5, "#,##0.00\\ \"€\""));
        // [$€-2] est un jeton de devise : le symbole reste a la position ou il est ecrit,
        // exactement comme le "$" prefixe du format precedent.
        assertEquals("€ 1,234.50", f(1234.5, "[$€-2] #,##0.00"));
        assertEquals("12.5 kg", f(12.5, "0.0\" kg\""));
    }

    @Test
    public void sectionsNegativesEtZero() {
        assertEquals("(1,234)", f(-1234, "#,##0;[Red](#,##0)"));
        assertEquals("1,234", f(1234, "#,##0;[Red](#,##0)"));
        assertEquals("-", f(0, "#,##0;(#,##0);-"));
        assertEquals("-12.50", f(-12.5, "0.00"));
    }

    @Test
    public void conditions() {
        assertEquals("haut", f(150, "[>100]\"haut\";\"bas\""));
        assertEquals("bas", f(50, "[>100]\"haut\";\"bas\""));
    }

    @Test
    public void echelleParMilliers() {
        assertEquals("1.23M", f(1234567, "0.00,,\"M\""));
        assertEquals("1,235", f(1234567, "#,##0,"));
    }

    @Test
    public void scientifique() {
        assertEquals("1.23E+04", f(12345, "0.00E+00"));
        assertEquals("1.23E-05", f(0.00001234, "0.00E+00"));
    }

    @Test
    public void dates() {
        assertEquals("01/01/2025", f(45658, "dd/mm/yyyy"));
        assertEquals("2025-01-01", f(45658, "yyyy-mm-dd"));
        assertEquals("Jan", f(45658, "mmm"));
        assertEquals("January", f(45658, "mmmm"));
        assertEquals("1/1/25", f(45658, "m/d/yy"));
        // 1 = 01/01/1900 ; 60 = 29/02/1900 (bug historique d'Excel)
        assertEquals("01/01/1900", f(1, "dd/mm/yyyy"));
        assertEquals("28/02/1900", f(59, "dd/mm/yyyy"));
        assertEquals("01/03/1900", f(61, "dd/mm/yyyy"));
    }

    @Test
    public void heures() {
        assertEquals("12:00:00", f(0.5, "hh:mm:ss"));
        assertEquals("06:30", f(0.2708333333, "hh:mm"));
        assertEquals("1:00 PM", f(0.541666666, "h:mm AM/PM"));
        // 'm' isole = mois, non zero-padde (coherent avec "m/d/yy" ci-dessus) : 'mm' donnerait "01".
        assertEquals("1", f(45658, "m"));
        assertEquals("26:00", f(1.083333333, "[h]:mm"));
    }

    @Test
    public void texteEtCasDegeneres() {
        assertEquals("12", f(12, "@"));
        assertEquals("abc", ExcelNumberFormat.format(1, "\"abc\"", false));
        assertEquals("NaN", f(Double.NaN, "0.00"));
    }

    @Test
    public void datesSysteme1904() {
        assertEquals("01/01/1904", ExcelNumberFormat.format(0, "dd/mm/yyyy", true));
        assertEquals("02/01/1904", ExcelNumberFormat.format(1, "dd/mm/yyyy", true));
        assertEquals(1900, ExcelDate.fromSerial(1, false).year);
        assertEquals(2025, ExcelDate.fromSerial(45658, false).year);
    }
}
