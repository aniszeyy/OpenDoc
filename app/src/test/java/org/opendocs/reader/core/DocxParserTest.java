/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opendocs.reader.core.model.Block;
import org.opendocs.reader.core.model.DocType;
import org.opendocs.reader.core.model.Paragraph;
import org.opendocs.reader.core.model.ParsedDocument;
import org.opendocs.reader.core.model.Run;
import org.opendocs.reader.core.model.Table;
import org.opendocs.reader.core.parse.DocumentParser;

import java.util.ArrayList;
import java.util.List;

/** Tests du parseur DOCX sur un vrai fichier OOXML (samples/exemple.docx). */
public class DocxParserTest {

    private static ParsedDocument doc;

    @BeforeClass
    public static void parseOnce() throws Exception {
        doc = DocumentParser.parse(TestFiles.sample("exemple.docx"), "exemple.docx", null);
    }

    @Test
    public void detecteLeType() {
        assertEquals(DocType.DOCX, doc.type);
        assertTrue(doc.warnings.isEmpty());
    }

    @Test
    public void litLesMetadonnees() {
        assertEquals("Rapport de demonstration", doc.metadata.title);
        assertEquals("OpenDocs", doc.metadata.creator);
        assertTrue(doc.metadata.created.startsWith("2026-01-15"));
        assertEquals("OpenDocs sample generator", doc.metadata.application);
    }

    @Test
    public void detecteLesTitres() {
        List<Paragraph> headings = new ArrayList<>();
        for (Block b : doc.blocks) {
            if (b.kind() == Block.Kind.PARAGRAPH) {
                Paragraph p = ((Block.ParagraphBlock) b).paragraph;
                if (p.headingLevel > 0) headings.add(p);
            }
        }
        assertEquals(5, headings.size());
        assertEquals(1, headings.get(0).headingLevel);
        assertEquals("Rapport de demonstration OpenDocs", headings.get(0).text());
        assertEquals(2, headings.get(1).headingLevel);
        // La taille du titre vient du style (w:sz 40 = 20 pt), pas des docDefaults (11 pt).
        assertEquals(20f, headings.get(0).runs.get(0).sizePt, 0.01f);
        assertEquals(0xFF1F497D, headings.get(0).runs.get(0).color);
    }

    @Test
    public void appliqueLesStylesDeRuns() {
        Paragraph body = null;
        for (Block b : doc.blocks) {
            if (b.kind() == Block.Kind.PARAGRAPH
                    && b.text().startsWith("Ce document exerce")) {
                body = ((Block.ParagraphBlock) b).paragraph;
                break;
            }
        }
        assertNotNull(body);
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean red = false;
        boolean highlighted = false;
        for (Run r : body.runs) {
            bold |= r.bold;
            italic |= r.italic;
            underline |= r.underline;
            red |= r.color == 0xFFC00000;
            highlighted |= r.highlight == 0xFFFFFF00;
        }
        assertTrue("gras", bold);
        assertTrue("italique", italic);
        assertTrue("souligne", underline);
        assertTrue("couleur rouge", red);
        assertTrue("surlignage jaune", highlighted);
    }

    @Test
    public void resoutLesListes() {
        List<String> labels = new ArrayList<>();
        for (Block b : doc.blocks) {
            if (b.kind() == Block.Kind.PARAGRAPH) {
                Paragraph p = ((Block.ParagraphBlock) b).paragraph;
                if (!p.numberLabel.isEmpty()) labels.add(p.numberLabel + "|" + p.text());
            }
        }
        assertEquals(6, labels.size());
        assertEquals("\u2022|Premier element", labels.get(0));
        assertEquals("\u2022|Deuxieme element", labels.get(1));
        assertEquals("1.|Etape une", labels.get(3));
        assertEquals("2.|Etape deux", labels.get(4));
        assertEquals("3.|Etape trois", labels.get(5));
    }

    @Test
    public void litLeTableau() {
        Table table = null;
        for (Block b : doc.blocks) {
            if (b.kind() == Block.Kind.TABLE) {
                table = ((Block.TableBlock) b).table;
                break;
            }
        }
        assertNotNull(table);
        assertEquals(3, table.rowCount());
        assertEquals(3, table.columnCount());
        assertTrue(table.firstRowIsHeader);
        assertEquals("Produit", table.rows.get(0).get(0).text());
        assertEquals("Café arabica", table.rows.get(1).get(0).text());
        assertEquals("8,50 €", table.rows.get(1).get(2).text());
        assertNotNull(table.columnWeights);
        assertEquals(3, table.columnWeights.length);
    }

    @Test
    public void extraitLImage() {
        Block.ImageBlock image = null;
        for (Block b : doc.blocks) {
            if (b.kind() == Block.Kind.IMAGE) {
                image = (Block.ImageBlock) b;
                break;
            }
        }
        assertNotNull(image);
        assertTrue(image.data.length > 100);
        // Signature PNG
        assertEquals((byte) 0x89, image.data[0]);
        assertEquals('P', image.data[1]);
        assertEquals(192, image.widthPx);
        assertEquals(128, image.heightPx);
        assertEquals("Degrade de demonstration", image.alt);
    }

    @Test
    public void resoutLesHyperliens() {
        String url = null;
        for (Block b : doc.blocks) {
            if (b.kind() != Block.Kind.PARAGRAPH) continue;
            for (Run r : ((Block.ParagraphBlock) b).paragraph.runs) {
                if (r.link != null) url = r.link;
            }
        }
        assertEquals("https://example.org/opendocs", url);
    }

    @Test
    public void calculeLesStatistiques() {
        assertFalse(doc.plainText().isEmpty());
        assertTrue(doc.metadata.stats.containsKey("Mots"));
        assertEquals("1", doc.metadata.stats.get("Tableaux"));
        assertEquals("1", doc.metadata.stats.get("Images"));
    }
}
