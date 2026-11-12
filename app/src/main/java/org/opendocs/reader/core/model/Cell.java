/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.model;

/** Cellule de feuille de calcul (XLSX ou CSV). */
public final class Cell {

    public static final int ALIGN_GENERAL = 0;
    public static final int ALIGN_LEFT = 1;
    public static final int ALIGN_CENTER = 2;
    public static final int ALIGN_RIGHT = 3;

    /** Valeur affichee (deja formatee selon le format numerique Excel). */
    public String text = "";
    /** Valeur numerique si la cellule en contient une, sinon null. */
    public Double number;
    /** Formule sans le "=" initial, ou null. */
    public String formula;
    public boolean bold;
    public boolean italic;
    public int align = ALIGN_GENERAL;
    public int color;
    public int background;
    public boolean header;
    /** true si la cellule fait partie d'une fusion et n'est pas l'ancre. */
    public boolean mergedAway;

    public Cell() {
    }

    public Cell(String text) {
        this.text = text == null ? "" : text;
    }

    public static Cell of(String text) {
        return new Cell(text);
    }

    public static Cell ofNumber(double value, String display) {
        Cell c = new Cell(display);
        c.number = value;
        c.align = ALIGN_RIGHT;
        return c;
    }

    public boolean isNumeric() {
        return number != null;
    }

    public boolean isEmpty() {
        return (text == null || text.isEmpty()) && formula == null;
    }
}
