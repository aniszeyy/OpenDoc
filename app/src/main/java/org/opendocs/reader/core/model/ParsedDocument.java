/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultat du parsing, tous formats confondus. Un seul champ de contenu est
 * rempli selon {@link #type} : blocks (DOCX), slides (PPTX) ou sheets (XLSX/CSV).
 */
public final class ParsedDocument {

    public DocType type = DocType.UNKNOWN;
    public String fileName = "";
    public long fileSize;
    public long parseTimeMs;

    public final Metadata metadata = new Metadata();
    public final List<String> warnings = new ArrayList<>(0);

    /** DOCX */
    public final List<Block> blocks = new ArrayList<>();

    /** PPTX */
    public final List<Slide> slides = new ArrayList<>();
    public long slideWidthEmu = 9144000L;   // 10 pouces (4:3 par defaut)
    public long slideHeightEmu = 6858000L;  // 7,5 pouces

    /** XLSX / CSV */
    public final List<Sheet> sheets = new ArrayList<>();

    private String cachedText;

    public void warn(String message) {
        if (message != null && !warnings.contains(message)) warnings.add(message);
    }

    public String title() {
        return metadata.displayTitle(fileName);
    }

    /** Texte integral : recherche plein texte, export TXT, impression. */
    public String plainText() {
        if (cachedText != null) return cachedText;
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case DOCX:
                sb.append(Block.joinText(blocks));
                break;
            case PPTX:
                for (Slide s : slides) {
                    sb.append("--- Diapositive ").append(s.number).append(" ---\n");
                    sb.append(s.fullText()).append('\n');
                }
                break;
            case XLSX:
            case CSV:
                for (Sheet sheet : sheets) {
                    if (sheets.size() > 1) sb.append("### ").append(sheet.name).append('\n');
                    sb.append(sheet.text());
                }
                break;
            default:
                break;
        }
        cachedText = sb.toString();
        return cachedText;
    }

    public void invalidateTextCache() {
        cachedText = null;
    }

    public int slideCount() {
        return slides.size();
    }

    public int sheetCount() {
        return sheets.size();
    }

    public boolean isEmptyContent() {
        switch (type) {
            case DOCX:
                return blocks.isEmpty();
            case PPTX:
                return slides.isEmpty();
            case XLSX:
            case CSV:
                return sheets.isEmpty();
            default:
                return true;
        }
    }

    /** Remplit les statistiques affichees dans "Proprietes". */
    public void computeStats() {
        String text = plainText();
        metadata.putStat("Caracteres", text.length());
        metadata.putStat("Mots", countWords(text));
        switch (type) {
            case DOCX:
                int tables = 0;
                int images = 0;
                int paragraphs = 0;
                for (Block b : blocks) {
                    if (b.kind() == Block.Kind.TABLE) tables++;
                    else if (b.kind() == Block.Kind.IMAGE) images++;
                    else if (b.kind() == Block.Kind.PARAGRAPH) paragraphs++;
                }
                metadata.putStat("Paragraphes", paragraphs);
                metadata.putStat("Tableaux", tables);
                metadata.putStat("Images", images);
                break;
            case PPTX:
                metadata.putStat("Diapositives", slides.size());
                int pictures = 0;
                int notes = 0;
                for (Slide s : slides) {
                    if (s.notes != null && !s.notes.trim().isEmpty()) notes++;
                    for (Shape shape : s.shapes) {
                        if (shape.kind == Shape.Kind.PICTURE) pictures++;
                    }
                }
                metadata.putStat("Images", pictures);
                metadata.putStat("Diapos avec notes", notes);
                break;
            case XLSX:
            case CSV:
                metadata.putStat("Feuilles", sheets.size());
                long rows = 0;
                long cells = 0;
                int cols = 0;
                for (Sheet s : sheets) {
                    rows += s.rowCount();
                    cols = Math.max(cols, s.columnCount());
                    for (List<Cell> row : s.rows) {
                        for (Cell c : row) {
                            if (!c.isEmpty()) cells++;
                        }
                    }
                }
                metadata.putStat("Lignes", rows);
                metadata.putStat("Colonnes (max)", cols);
                metadata.putStat("Cellules non vides", cells);
                break;
            default:
                break;
        }
    }

    public static int countWords(String text) {
        if (text == null || text.isEmpty()) return 0;
        int words = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean letter = Character.isLetterOrDigit(c);
            if (letter && !inWord) {
                words++;
                inWord = true;
            } else if (!letter) {
                inWord = false;
            }
        }
        return words;
    }
}
