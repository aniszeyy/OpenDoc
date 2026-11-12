/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opendocs.reader.core.model.Cell;
import org.opendocs.reader.core.model.DocType;
import org.opendocs.reader.core.model.ParsedDocument;
import org.opendocs.reader.core.model.Sheet;
import org.opendocs.reader.core.parse.DocumentParser;
import org.opendocs.reader.core.parse.XlsxParser;

/** Tests du parseur XLSX (samples/exemple.xlsx). */
public class XlsxParserTest {

    private static ParsedDocument doc;

    @BeforeClass
    public static void parseOnce() throws Exception {
        doc = DocumentParser.parse(TestFiles.sample("exemple.xlsx"), "exemple.xlsx", null);
    }

    @Test
    public void litLesFeuillesDansLOrdre() {
        assertEquals(DocType.XLSX, doc.type);
        assertEquals(2, doc.sheets.size());
        assertEquals("Ventes", doc.sheets.get(0).name);
        assertEquals("Budget", doc.sheets.get(1).name);
    }

    @Test
    public void resoutLesChainesPartagees() {
        Sheet sheet = doc.sheets.get(0);
        assertEquals("Produit", sheet.cellText(0, 0));
        assertEquals("Café arabica", sheet.cellText(1, 0));
        assertEquals("Région", sheet.cellText(0, 1));
        // Chaine contenant une esperluette echappee en XML.
        assertEquals("R&D", doc.sheets.get(1).cellText(2, 0));
    }

    @Test
    public void appliqueLesFormatsNumeriques() {
        Sheet sheet = doc.sheets.get(0);
        assertEquals("12", sheet.cellText(1, 2));            // #,##0
        assertEquals("8.50 €", sheet.cellText(1, 3));        // monnaie personnalisee
        assertEquals("01/01/2025", sheet.cellText(1, 5));    // date serie 45658
        assertEquals("42.00%", sheet.cellText(1, 6));        // 0.00%
        assertEquals("1,234,567.89 €", doc.sheets.get(1).cellText(3, 1));
        assertEquals("(12,500.50)", doc.sheets.get(1).cellText(1, 1)); // section negative
        assertEquals("1.23E-05", doc.sheets.get(1).cellText(2, 1));    // scientifique
    }

    @Test
    public void conserveLesFormulesEtLesValeurs() {
        Cell cell = doc.sheets.get(0).cell(1, 4);
        assertNotNull(cell);
        assertEquals("C2*D2", cell.formula);
        assertEquals("102.00 €", cell.text);
        assertEquals(102.0, cell.number, 0.0001);
        assertEquals("SUM(E2:E5)", doc.sheets.get(0).cell(6, 4).formula);
    }

    @Test
    public void litLesStyles() {
        Sheet sheet = doc.sheets.get(0);
        Cell header = sheet.cell(0, 0);
        assertTrue(header.bold);
        assertEquals(0xFFFFFFFF, header.color);
        assertEquals(0xFF2E74B5, header.background);
        assertEquals(Cell.ALIGN_RIGHT, sheet.cell(1, 2).align);
    }

    @Test
    public void litLesFusionsEtLesLignesFigees() {
        Sheet sheet = doc.sheets.get(0);
        assertEquals(1, sheet.frozenRow);
        assertEquals(1, sheet.merges.size());
        int[] merge = sheet.merges.get(0);
        assertEquals(6, merge[0]);
        assertEquals(0, merge[1]);
        assertEquals(6, merge[2]);
        assertEquals(3, merge[3]);
        assertTrue(sheet.cell(6, 1).mergedAway);
        assertEquals("Total général", sheet.cellText(6, 0));
    }

    @Test
    public void litLesValeursEnLigne() {
        assertEquals("Total général", doc.sheets.get(0).cellText(6, 0));
    }

    @Test
    public void normaliseLaGrille() {
        Sheet sheet = doc.sheets.get(0);
        assertEquals(7, sheet.columnCount());
        for (java.util.List<Cell> row : sheet.rows) {
            assertEquals(7, row.size());
        }
        assertTrue(sheet.columnWidth(0) >= 18f);
    }

    @Test
    public void convertitLesReferencesA1() {
        assertEquals(1, XlsxParser.columnIndex("A1"));
        assertEquals(27, XlsxParser.columnIndex("AA12"));
        assertEquals(703, XlsxParser.columnIndex("AAA1"));
        assertEquals("A", XlsxParser.columnName(0));
        assertEquals("Z", XlsxParser.columnName(25));
        assertEquals("AA", XlsxParser.columnName(26));
        assertEquals("BC", XlsxParser.columnName(54));
        int[] address = XlsxParser.parseAddress("$B$7");
        assertEquals(6, address[0]);
        assertEquals(1, address[1]);
        int[] range = XlsxParser.parseRange("C3:A1");
        assertEquals(0, range[0]);
        assertEquals(0, range[1]);
        assertEquals(2, range[2]);
        assertEquals(2, range[3]);
        assertNull(XlsxParser.parseRange(""));
    }
}
