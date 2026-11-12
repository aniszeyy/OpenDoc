/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.model;

import java.util.ArrayList;
import java.util.List;

/** Paragraphe : suite de runs + attributs de mise en forme du bloc. */
public final class Paragraph {

    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_RIGHT = 2;
    public static final int ALIGN_JUSTIFY = 3;

    public final List<Run> runs = new ArrayList<>(2);

    /** 0 = corps de texte, 1..6 = niveau de titre. */
    public int headingLevel;
    public int align = ALIGN_LEFT;
    public int indentLevel;
    public boolean bullet;
    /** Puce ou numerotation deja resolue ("•", "1.", "a)"...). */
    public String numberLabel = "";
    public float spaceBeforePt;
    public float spaceAfterPt;
    public float lineSpacing = 1.0f;
    public String styleName = "";
    public int backgroundColor;

    public void add(Run run) {
        if (run != null && !run.isEmpty()) runs.add(run);
    }

    public String text() {
        StringBuilder sb = new StringBuilder();
        for (Run r : runs) sb.append(r.text);
        return sb.toString();
    }

    public boolean isEmpty() {
        return text().trim().isEmpty();
    }

    /** Taille de police dominante (0 si aucune taille explicite). */
    public float dominantSizePt() {
        float max = 0;
        for (Run r : runs) {
            if (r.sizePt > max) max = r.sizePt;
        }
        return max;
    }
}
