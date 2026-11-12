/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.model;

import java.util.ArrayList;
import java.util.List;

/** Tableau (w:tbl en DOCX, a:tbl en PPTX). */
public final class Table {

    /** Cellule de tableau : contient des paragraphes (mise en forme riche). */
    public static final class Cell {
        public final List<Paragraph> paragraphs = new ArrayList<>(1);
        public int colSpan = 1;
        public int rowSpan = 1;
        public boolean header;
        public int background;
        public int align = -1;

        public String text() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < paragraphs.size(); i++) {
                if (i > 0) sb.append('\n');
                sb.append(paragraphs.get(i).text());
            }
            return sb.toString();
        }
    }

    public final List<List<Cell>> rows = new ArrayList<>();
    /** Largeurs relatives des colonnes (somme quelconque), ou null si inconnu. */
    public float[] columnWeights;
    public boolean firstRowIsHeader;

    public int rowCount() {
        return rows.size();
    }

    public int columnCount() {
        int max = 0;
        for (List<Cell> row : rows) {
            int count = 0;
            for (Cell c : row) count += Math.max(1, c.colSpan);
            if (count > max) max = count;
        }
        return max;
    }

    public String text() {
        StringBuilder sb = new StringBuilder();
        for (List<Cell> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) sb.append('\t');
                sb.append(row.get(i).text().replace('\n', ' '));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
