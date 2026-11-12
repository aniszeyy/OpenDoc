/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.UnderlineSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.View;

import org.opendocs.reader.core.model.Paragraph;
import org.opendocs.reader.core.model.Run;
import org.opendocs.reader.core.model.Table;

/**
 * Conversion du modele riche (runs OOXML) vers des spans Android, partagee par
 * la vue texte du DOCX et le rendu Canvas des diapositives.
 */
public final class TextFormat {

    public static final float DEFAULT_BODY_PT = 11f;

    private TextFormat() {
    }

    /** Points typographiques -> pixels, en tenant compte de la taille de texte choisie. */
    public static float sizePx(Context context, float points) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                points * 4f / 3f, context.getResources().getDisplayMetrics());
    }

    public static float baseSizePt(Paragraph paragraph) {
        if (paragraph.headingLevel > 0) return headingPt(paragraph.headingLevel);
        for (Run run : paragraph.runs) {
            if (run.sizePt > 0) return run.sizePt;
        }
        return DEFAULT_BODY_PT;
    }

    public static float headingPt(int level) {
        switch (level) {
            case 1: return 20f;
            case 2: return 17f;
            case 3: return 15f;
            case 4: return 13f;
            case 5: return 12f;
            default: return 11.5f;
        }
    }

    public static int alignment(int align) {
        switch (align) {
            case Paragraph.ALIGN_CENTER:
                return View.TEXT_ALIGNMENT_CENTER;
            case Paragraph.ALIGN_RIGHT:
                return View.TEXT_ALIGNMENT_VIEW_END;
            default:
                return View.TEXT_ALIGNMENT_VIEW_START;
        }
    }

    public static CharSequence paragraph(Context context, Paragraph paragraph) {
        return build(context, paragraph);
    }

    public static CharSequence tableCell(Context context, Table.Cell cell) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < cell.paragraphs.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(build(context, cell.paragraphs.get(i)));
        }
        return sb;
    }

    private static SpannableStringBuilder build(Context context, Paragraph paragraph) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (paragraph.bullet) {
            sb.append(paragraph.numberLabel != null && !paragraph.numberLabel.isEmpty()
                    ? paragraph.numberLabel : "\u2022");
            sb.append(' ');
        }
        float basePx = sizePx(context, baseSizePt(paragraph));
        for (Run run : paragraph.runs) {
            int start = sb.length();
            sb.append(run.text);
            int end = sb.length();
            if (end == start) continue;
            if (run.bold) sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (run.italic) sb.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (run.underline) sb.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (run.strike) sb.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (run.superscript) sb.setSpan(new SuperscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (run.subscript) sb.setSpan(new SubscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (run.color != 0) sb.setSpan(new ForegroundColorSpan(run.color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (run.highlight != 0) sb.setSpan(new BackgroundColorSpan(run.highlight), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (run.link != null && !run.link.isEmpty()) sb.setSpan(new URLSpan(run.link), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            float px = run.sizePt > 0 ? sizePx(context, run.sizePt) : basePx;
            sb.setSpan(new PixelSizeSpan(px), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    /**
     * Taille absolue en pixels. AbsoluteSizeSpan depend de la densite de l'ecran :
     * fixer directement la taille du Paint garantit le meme rendu dans un TextView
     * (DOCX) et dans un StaticLayout (diapositives PPTX).
     */
    public static final class PixelSizeSpan extends MetricAffectingSpan {

        private final float px;

        public PixelSizeSpan(float px) {
            this.px = px;
        }

        @Override
        public void updateMeasureState(TextPaint paint) {
            paint.setTextSize(px);
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            paint.setTextSize(px);
        }
    }
}
