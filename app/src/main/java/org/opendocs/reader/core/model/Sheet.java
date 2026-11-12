/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.model;

import java.util.ArrayList;
import java.util.List;

/** Feuille de calcul : grille dense de cellules + largeurs + fusions. */
public final class Sheet {

    public String name = "";
    public final List<List<Cell>> rows = new ArrayList<>();
    /** Largeur de chaque colonne en "nombre de caracteres" (unite Excel). */
    public final List<Float> columnWidths = new ArrayList<>();
    /** Fusions : {row1, col1, row2, col2} inclusifs, 0-based. */
    public final List<int[]> merges = new ArrayList<>();
    /** Index de ligne figee (entete), -1 si aucune. */
    public int frozenRow = -1;
    public boolean truncated;

    public Sheet() {
    }

    public Sheet(String name) {
        this.name = name == null ? "" : name;
    }

    public int rowCount() {
        return rows.size();
    }

    public int columnCount() {
        int max = 0;
        for (List<Cell> row : rows) {
            if (row.size() > max) max = row.size();
        }
        return max;
    }

    public Cell cell(int row, int col) {
        if (row < 0 || row >= rows.size()) return null;
        List<Cell> r = rows.get(row);
        if (col < 0 || col >= r.size()) return null;
        return r.get(col);
    }

    public String cellText(int row, int col) {
        Cell c = cell(row, col);
        return c == null ? "" : c.text;
    }

    public float columnWidth(int col) {
        if (col >= 0 && col < columnWidths.size()) {
            Float w = columnWidths.get(col);
            if (w != null && w > 0) return w;
        }
        return 8.43f; // largeur Excel par defaut
    }

    /** Normalise la grille : toutes les lignes ont la meme longueur. */
    public void densify() {
        int cols = columnCount();
        for (List<Cell> row : rows) {
            while (row.size() < cols) row.add(new Cell());
        }
        while (columnWidths.size() < cols) columnWidths.add(8.43f);
    }

    public String text() {
        StringBuilder sb = new StringBuilder();
        for (List<Cell> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) sb.append('\t');
                sb.append(row.get(i).text);
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
