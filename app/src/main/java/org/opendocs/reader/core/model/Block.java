/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Bloc de contenu d'un document texte (DOCX).
 *
 * <p>Hierarchie fermee et volatile : un bloc est soit un paragraphe, soit un
 * tableau, soit une image, soit un saut de page, soit un filet horizontal.
 * L'adaptateur RecyclerView dispatche sur {@link #kind()}.</p>
 */
public abstract class Block {

    public enum Kind { PARAGRAPH, TABLE, IMAGE, PAGE_BREAK, RULE }

    public abstract Kind kind();

    /** Representation texte brute (recherche plein texte, export TXT). */
    public abstract String text();

    public static final class ParagraphBlock extends Block {
        public final Paragraph paragraph;

        public ParagraphBlock(Paragraph paragraph) {
            this.paragraph = paragraph;
        }

        @Override
        public Kind kind() {
            return Kind.PARAGRAPH;
        }

        @Override
        public String text() {
            return paragraph.text();
        }
    }

    public static final class TableBlock extends Block {
        public final Table table;

        public TableBlock(Table table) {
            this.table = table;
        }

        @Override
        public Kind kind() {
            return Kind.TABLE;
        }

        @Override
        public String text() {
            return table.text();
        }
    }

    public static final class ImageBlock extends Block {
        public final byte[] data;
        public final String name;
        public final String alt;
        public int widthPx;
        public int heightPx;

        public ImageBlock(byte[] data, String name, String alt, int widthPx, int heightPx) {
            this.data = data;
            this.name = name == null ? "" : name;
            this.alt = alt == null ? "" : alt;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
        }

        @Override
        public Kind kind() {
            return Kind.IMAGE;
        }

        @Override
        public String text() {
            return alt.isEmpty() ? "[" + name + "]" : "[" + alt + "]";
        }
    }

    public static final class PageBreakBlock extends Block {
        @Override
        public Kind kind() {
            return Kind.PAGE_BREAK;
        }

        @Override
        public String text() {
            return "\n";
        }
    }

    public static final class RuleBlock extends Block {
        @Override
        public Kind kind() {
            return Kind.RULE;
        }

        @Override
        public String text() {
            return "----\n";
        }
    }

    /** Concatene le texte de tous les blocs (separateur : saut de ligne). */
    public static String joinText(List<Block> blocks) {
        StringBuilder sb = new StringBuilder();
        for (Block b : blocks) {
            String t = b.text();
            if (t == null || t.isEmpty()) continue;
            sb.append(t);
            if (!t.endsWith("\n")) sb.append('\n');
        }
        return sb.toString();
    }

    /** Liste vide mutable (helper pour les parsers). */
    public static List<Block> newList() {
        return new ArrayList<>();
    }
}
