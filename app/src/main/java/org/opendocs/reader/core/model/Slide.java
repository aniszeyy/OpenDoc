/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.model;

import java.util.ArrayList;
import java.util.List;

/** Diapositive PPTX : formes, commentaires du presentateur, fond. */
public final class Slide {

    public final List<Shape> shapes = new ArrayList<>();
    public String notes = "";
    public String title = "";
    /** Fond ARGB, 0 = fond par defaut du theme. */
    public int background;
    /** Numero affiche (1-based). */
    public int number;

    public String text() {
        StringBuilder sb = new StringBuilder();
        if (!title.isEmpty()) sb.append(title).append('\n');
        for (Shape s : shapes) {
            String t = s.text();
            if (!t.trim().isEmpty()) sb.append(t).append('\n');
        }
        return sb.toString();
    }

    public String fullText() {
        String body = text();
        if (notes == null || notes.trim().isEmpty()) return body;
        return body + "\n[Notes] " + notes + "\n";
    }
}
