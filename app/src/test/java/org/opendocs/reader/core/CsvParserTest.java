/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opendocs.reader.core.model.DocType;
import org.opendocs.reader.core.model.ParsedDocument;
import org.opendocs.reader.core.model.Sheet;
import org.opendocs.reader.core.parse.CsvParser;
import org.opendocs.reader.core.parse.DocumentParser;
import org.opendocs.reader.core.util.TextUtil;

import java.nio.charset.Charset;
import java.util.List;

/** Tests du parseur CSV : RFC 4180, encodages, detection du separateur. */
public class CsvParserTest {

    @Test
    public void parseLeCsvFrancais() throws Exception {
        ParsedDocument doc = DocumentParser.parse(TestFiles.sample("exemple.csv"), "exemple.csv", null);
        assertEquals(DocType.CSV, doc.type);
        Sheet sheet = doc.sheets.get(0);
        assertEquals(5, sheet.rowCount());
        assertEquals(5, sheet.columnCount());
        assertEquals("Produit", sheet.cellText(0, 0));
        assertEquals("Région", sheet.cellText(0, 1));
        assertTrue(sheet.cell(0, 0).header);
        assertEquals("point-virgule (;)", doc.metadata.stats.get("Separateur detecte"));
        assertEquals("UTF-8 (BOM)", doc.metadata.stats.get("Encodage"));
    }

    @Test
    public void gereLesGuillemetsEtLesSautsDeLigneInternes() throws Exception {
        ParsedDocument doc = DocumentParser.parse(TestFiles.sample("exemple.csv"), "exemple.csv", null);
        Sheet sheet = doc.sheets.get(0);
        assertEquals("Lot bio, certifié\ndeux lignes", sheet.cellText(2, 4));
        assertEquals("Prix \"promo\" ce mois", sheet.cellText(3, 4));
        assertEquals("", sheet.cellText(4, 4));
    }

    @Test
    public void detecteLesNombresAvecVirguleDecimale() throws Exception {
        ParsedDocument doc = DocumentParser.parse(TestFiles.sample("exemple.csv"), "exemple.csv", null);
        Sheet sheet = doc.sheets.get(0);
        assertNotNull(sheet.cell(1, 3).number);
        assertEquals(8.5, sheet.cell(1, 3).number, 0.0001);
        assertEquals(12.0, sheet.cell(1, 2).number, 0.0001);
    }

    @Test
    public void parseLeCsvAmericain() throws Exception {
        ParsedDocument doc = DocumentParser.parse(TestFiles.sample("exemple_us.csv"), "exemple_us.csv", null);
        Sheet sheet = doc.sheets.get(0);
        assertEquals(5, sheet.rowCount());
        assertEquals(4, sheet.columnCount());
        assertEquals("Doe, John", sheet.cellText(3, 1));
        assertEquals(93.5, sheet.cell(1, 2).number, 0.0001);
        assertEquals("virgule (,)", doc.metadata.stats.get("Separateur detecte"));
    }

    @Test
    public void repliLatin1SansBom() throws Exception {
        ParsedDocument doc = DocumentParser.parse(TestFiles.sample("exemple_latin1.csv"), "exemple_latin1.csv", null);
        assertEquals("ISO-8859-1", doc.metadata.stats.get("Encodage"));
        assertEquals("Température", doc.sheets.get(0).cellText(0, 1));
    }

    @Test
    public void detecteLeSeparateur() {
        assertEquals(';', CsvParser.sniffDelimiter("a;b;c\n1;2;3\n4;5;6\n"));
        assertEquals(',', CsvParser.sniffDelimiter("a,b,c\n1,2,3\n4,5,6\n"));
        assertEquals('\t', CsvParser.sniffDelimiter("a\tb\tc\n1\t2\t3\n"));
        assertEquals('|', CsvParser.sniffDelimiter("a|b|c\n1|2|3\n"));
        assertEquals(',', CsvParser.sniffDelimiter("solo\n"));
    }

    @Test
    public void tokenizeSelonRfc4180() {
        List<List<String>> rows = CsvParser.tokenize("a,b\r\n\"x,y\",\"He said \"\"hi\"\"\"\r\n1,2", ',');
        assertEquals(3, rows.size());
        assertEquals("x,y", rows.get(1).get(0));
        assertEquals("He said \"hi\"", rows.get(1).get(1));
        assertEquals("2", rows.get(2).get(1));
    }

    @Test
    public void ignoreLesLignesVides() {
        List<List<String>> rows = CsvParser.tokenize("a;b\n\n1;2\n\n\n", ';');
        assertEquals(2, rows.size());
    }

    @Test
    public void decodeLesEncodages() {
        byte[] utf8Bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'e', (byte) 0xC3, (byte) 0xA9};
        // Le BOM doit disparaitre et le reste etre decode en UTF-8 : "e" + "é".
        assertEquals("eé", TextUtil.decode(utf8Bom));
        byte[] latin1 = {'c', (byte) 0xE9};
        assertEquals("cé", TextUtil.decode(latin1));
        byte[] utf16 = {(byte) 0xFF, (byte) 0xFE, 'a', 0};
        assertEquals("a", TextUtil.decode(utf16));
        assertTrue(TextUtil.isValidUtf8("héllo".getBytes(Charset.forName("UTF-8"))));
    }

    @Test
    public void echappeCorrectementLeCsv() {
        assertEquals("\"a;b\"", TextUtil.escapeCsv("a;b", ';'));
        assertEquals("\"say \"\"hi\"\"\"", TextUtil.escapeCsv("say \"hi\"", ','));
        assertEquals("plain", TextUtil.escapeCsv("plain", ','));
    }
}
