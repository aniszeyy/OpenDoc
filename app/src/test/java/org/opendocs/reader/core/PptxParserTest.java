/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opendocs.reader.core.model.DocType;
import org.opendocs.reader.core.model.ParsedDocument;
import org.opendocs.reader.core.model.Shape;
import org.opendocs.reader.core.model.Slide;
import org.opendocs.reader.core.parse.DocumentParser;

/** Tests du parseur PPTX (samples/exemple.pptx). */
public class PptxParserTest {

    private static ParsedDocument doc;

    @BeforeClass
    public static void parseOnce() throws Exception {
        doc = DocumentParser.parse(TestFiles.sample("exemple.pptx"), "exemple.pptx", null);
    }

    @Test
    public void litLaTailleEtLeNombreDeDiapositives() {
        assertEquals(DocType.PPTX, doc.type);
        assertEquals(3, doc.slides.size());
        assertEquals(12192000L, doc.slideWidthEmu);
        assertEquals(6858000L, doc.slideHeightEmu);
        assertEquals(1, doc.slides.get(0).number);
        assertEquals(3, doc.slides.get(2).number);
    }

    @Test
    public void litLesTitres() {
        assertEquals("OpenDocs", doc.slides.get(0).title);
        assertEquals("Formats supportes", doc.slides.get(1).title);
        assertEquals("Tableau et notes", doc.slides.get(2).title);
    }

    @Test
    public void heriteDuFondDepuisLeMasque() {
        // Diapo 1 : fond explicite. Diapo 2 : fond herite du masque (blanc).
        assertEquals(0xFFF2F6FA, doc.slides.get(0).background);
        assertEquals(0xFFFFFFFF, doc.slides.get(1).background);
    }

    @Test
    public void litLesFormesEtLeurGeometrie() {
        Slide slide = doc.slides.get(1);
        assertEquals(4, slide.shapes.size());
        Shape box = null;
        Shape picture = null;
        for (Shape s : slide.shapes) {
            if ("Encadre".equals(s.name)) box = s;
            if (s.kind == Shape.Kind.PICTURE) picture = s;
        }
        assertNotNull(box);
        assertEquals("roundRect", box.geometry);
        assertEquals(0xFF2E74B5, box.fill);
        assertEquals("100 % hors ligne", box.text());
        assertEquals(8300000L, box.x);
        assertEquals(3000000L, box.cx);
        assertNotNull(picture);
        assertTrue(picture.image.length > 100);
        assertEquals(2000000L, picture.cy);
    }

    @Test
    public void litLesPuces() {
        Slide slide = doc.slides.get(1);
        Shape body = null;
        for (Shape s : slide.shapes) {
            if ("Contenu".equals(s.name)) body = s;
        }
        assertNotNull(body);
        assertEquals(4, body.paragraphs.size());
        assertTrue(body.paragraphs.get(0).bullet);
        assertEquals("\u2022", body.paragraphs.get(0).numberLabel);
        assertEquals("DOCX : texte, styles, tableaux", body.paragraphs.get(0).text());
    }

    @Test
    public void litLeTableau() {
        Shape table = null;
        for (Shape s : doc.slides.get(2).shapes) {
            if (s.kind == Shape.Kind.TABLE) table = s;
        }
        assertNotNull(table);
        assertEquals(3, table.table.rowCount());
        assertEquals(3, table.table.columnCount());
        assertEquals("Format", table.table.rows.get(0).get(0).text());
        assertTrue(table.table.firstRowIsHeader);
    }

    @Test
    public void litLesNotesDuPresentateur() {
        assertTrue(doc.slides.get(0).notes.isEmpty());
        String notes = doc.slides.get(2).notes;
        assertTrue(notes.contains("aucune permission"));
        assertTrue(notes.contains("Aucune donnee ne quitte l'appareil"));
        assertEquals("1", doc.metadata.stats.get("Diapos avec notes"));
    }

    @Test
    public void produitDuTextePourLaRecherche() {
        String text = doc.plainText();
        assertTrue(text.contains("Diapositive 2"));
        assertTrue(text.contains("CSV : separateurs multiples"));
        assertTrue(text.contains("[Notes]"));
    }
}
