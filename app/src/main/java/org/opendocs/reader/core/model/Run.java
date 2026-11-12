/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.model;

/** Fragment de texte homogene (equivalent d'un w:r / a:r OOXML). */
public final class Run {

    public String text = "";
    public boolean bold;
    public boolean italic;
    public boolean underline;
    public boolean strike;
    public boolean superscript;
    public boolean subscript;
    /** Taille en points typographiques ; 0 = heriter du style parent. */
    public float sizePt;
    /** Couleur ARGB ; 0 = couleur par defaut du theme. */
    public int color;
    /** Surlignage ARGB ; 0 = aucun. */
    public int highlight;
    public String font;
    /** Cible d'un lien hypertexte, ou null. */
    public String link;

    public Run() {
    }

    public Run(String text) {
        this.text = text == null ? "" : text;
    }

    public static Run of(String text) {
        return new Run(text);
    }

    public Run bold(boolean value) {
        this.bold = value;
        return this;
    }

    public Run italic(boolean value) {
        this.italic = value;
        return this;
    }

    public Run underline(boolean value) {
        this.underline = value;
        return this;
    }

    public Run size(float points) {
        this.sizePt = points;
        return this;
    }

    public Run color(int argb) {
        this.color = argb;
        return this;
    }

    public Run copyStyleFrom(Run other) {
        if (other == null) return this;
        bold = other.bold;
        italic = other.italic;
        underline = other.underline;
        strike = other.strike;
        superscript = other.superscript;
        subscript = other.subscript;
        sizePt = other.sizePt;
        color = other.color;
        highlight = other.highlight;
        font = other.font;
        link = other.link;
        return this;
    }

    public boolean isEmpty() {
        return text == null || text.isEmpty();
    }
}
