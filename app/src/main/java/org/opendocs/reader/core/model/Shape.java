/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Forme d'une diapositive PPTX. Toutes les coordonnees sont en EMU
 * (English Metric Units : 914400 EMU = 1 pouce = 96 px logiques).
 *
 * <p>Une seule classe avec un discriminant {@link Kind} plutot qu'une
 * hierarchie : le rendu Canvas et l'export PDF restent simples.</p>
 */
public final class Shape {

    public enum Kind { TEXT, PICTURE, GEOMETRY, TABLE, GROUP }

    public Kind kind = Kind.TEXT;
    public String name = "";
    /** Type de placeholder OOXML ("title", "body", "subTitle", "ftr"...), ou "". */
    public String placeholder = "";

    public long x;
    public long y;
    public long cx;
    public long cy;
    public float rotationDegrees;
    public boolean flipH;
    public boolean flipV;

    /** Preset de geometrie : rect, roundRect, ellipse, triangle, line, diamond... */
    public String geometry = "rect";
    /** Remplissage ARGB, 0 = transparent / aucune. */
    public int fill;
    public int lineColor;
    public float lineWidthPt;

    /** Contenu texte (TEXT, ou texte superpose a une GEOMETRY). */
    public final List<Paragraph> paragraphs = new ArrayList<>(0);
    /** 0 = haut, 1 = milieu, 2 = bas. */
    public int verticalAnchor;
    public float defaultSizePt;
    public int defaultColor;

    /** Image (PICTURE). */
    public byte[] image;
    public String imageName = "";

    /** Tableau (TABLE). */
    public Table table;

    /** Enfants (GROUP), coordonnees deja converties dans le repere de la diapositive. */
    public final List<Shape> children = new ArrayList<>(0);

    public boolean hasText() {
        for (Paragraph p : paragraphs) {
            if (!p.isEmpty()) return true;
        }
        return false;
    }

    public String text() {
        StringBuilder sb = new StringBuilder();
        for (Paragraph p : paragraphs) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(p.text());
        }
        if (table != null) sb.append(table.text());
        for (Shape child : children) {
            String t = child.text();
            if (!t.isEmpty()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(t);
            }
        }
        return sb.toString();
    }
}
