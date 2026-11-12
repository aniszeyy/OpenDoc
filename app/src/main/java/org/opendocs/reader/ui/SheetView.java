/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.Scroller;

import com.google.android.material.color.MaterialColors;

import org.opendocs.reader.core.model.Cell;
import org.opendocs.reader.core.model.Sheet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Grille de feuille de calcul : en-tete de colonnes et numeros de ligne figes,
 * defilement interne, et rendu limite a la zone visible (les feuilles peuvent
 * contenir des dizaines de milliers de lignes).
 */
public class SheetView extends View {

    private static final int MAX_RENDER_COLS = 256;
    private static final float EXCEL_CHAR_PX = 7f;

    private Sheet sheet;

    private int[] columnWidths = new int[0];
    private int[] columnLeft = new int[0];
    private int[] columnRight = new int[0];
    private int contentWidth;
    private int contentHeight;
    private final int rowHeight;
    private final int headerHeight;
    private final int gutterWidth;

    private float scrollX;
    private float scrollY;

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerBand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final Map<Long, int[]> mergeAnchors = new HashMap<>();
    private final Set<Long> mergedAway = new HashSet<>();

    private final Scroller scroller;
    private final VelocityTracker velocityTracker;
    private float lastTouchX;
    private float lastTouchY;
    private boolean dragging;

    private final int colorSurface;
    private final int colorOnSurface;
    private final int colorOnSurfaceVariant;
    private final int colorSurfaceVariant;
    private final int colorOutlineVariant;

    public SheetView(Context context) {
        super(context);
        rowHeight = dp(28);
        headerHeight = dp(32);
        gutterWidth = dp(46);

        colorSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface);
        colorOnSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
        colorOnSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        colorSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant);
        colorOutlineVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant);

        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density * 0.5f));
        grid.setColor(colorOutlineVariant);
        band.setStyle(Paint.Style.FILL);
        band.setColor(colorSurfaceVariant);

        headerBand.setStyle(Paint.Style.FILL);
        headerBand.setColor(colorSurfaceVariant);
        headerText.setTextSize(sp(11f));
        headerText.setColor(colorOnSurfaceVariant);
        headerText.setTextAlign(Paint.Align.CENTER);

        cellText.setTextSize(sp(12f));
        cellText.setColor(colorOnSurface);

        scroller = new Scroller(context);
        velocityTracker = VelocityTracker.obtain();
    }

    public void setSheet(Sheet sheet) {
        this.sheet = sheet;
        mergeAnchors.clear();
        mergedAway.clear();
        scrollX = 0f;
        scrollY = 0f;
        if (sheet != null) {
            sheet.densify();
            computeColumnWidths();
            indexMerges();
            int rows = Math.min(sheet.rowCount(), Integer.MAX_VALUE);
            contentHeight = rows * rowHeight;
            contentWidth = 0;
            for (int width : columnWidths) contentWidth += width;
        } else {
            contentHeight = 0;
            contentWidth = 0;
        }
        if (scroller != null) scroller.abortAnimation();
        invalidate();
    }

    public Sheet getSheet() {
        return sheet;
    }

    private void computeColumnWidths() {
        int columns = Math.min(sheet.columnCount(), MAX_RENDER_COLS);
        float density = getResources().getDisplayMetrics().density;
        int minimum = dp(48);
        int maximum = dp(320);

        columnWidths = new int[columns];
        columnLeft = new int[columns];
        columnRight = new int[columns];
        int x = 0;
        for (int c = 0; c < columns; c++) {
            int width = Math.round(sheet.columnWidth(c) * EXCEL_CHAR_PX * density);
            width = Math.max(minimum, Math.min(maximum, width));
            columnWidths[c] = width;
            columnLeft[c] = x;
            columnRight[c] = x + width;
            x += width;
        }
    }

    private void indexMerges() {
        for (int i = 0; i < sheet.merges.size(); i++) {
            int[] merge = sheet.merges.get(i);
            if (merge == null || merge.length < 4) continue;
            int r1 = Math.min(merge[0], merge[2]);
            int c1 = Math.min(merge[1], merge[3]);
            int r2 = Math.max(merge[0], merge[2]);
            int c2 = Math.max(merge[1], merge[3]);
            mergeAnchors.put(key(r1, c1), new int[]{r1, c1, r2, c2});
            for (int r = r1; r <= r2; r++) {
                for (int c = c1; c <= c2; c++) {
                    if (r != r1 || c != c1) mergedAway.add(key(r, c));
                }
            }
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        clampScroll();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(colorSurface);
        if (sheet == null || columnWidths.length == 0) return;

        int width = getWidth();
        int height = getHeight();
        clampScroll();

        int save = canvas.save();
        canvas.clipRect(gutterWidth, headerHeight, width, height);
        canvas.translate(-scrollX, -scrollY);
        drawCells(canvas, width, height);
        canvas.restoreToCount(save);

        save = canvas.save();
        canvas.clipRect(gutterWidth, 0, width, headerHeight);
        canvas.translate(-scrollX, 0f);
        drawColumnHeaders(canvas, width);
        canvas.restoreToCount(save);

        save = canvas.save();
        canvas.clipRect(0, headerHeight, gutterWidth, height);
        canvas.translate(0f, -scrollY);
        drawRowHeaders(canvas, height);
        canvas.restoreToCount(save);

        canvas.drawRect(0f, 0f, gutterWidth, headerHeight, headerBand);
        canvas.drawRect(0f, 0f, gutterWidth, headerHeight, grid);

        // Ligne du pavé figé (entete) si le classeur en definit une.
        if (sheet.frozenRow >= 0 && sheet.frozenRow < sheet.rowCount()) {
            float y = headerHeight + (sheet.frozenRow + 1) * rowHeight - scrollY;
            if (y > headerHeight && y < height) {
                band.setColor(colorOnSurfaceVariant);
                canvas.drawRect(gutterWidth, y, width, y + 1f, band);
            }
        }
    }

    private void drawCells(Canvas canvas, int viewWidth, int viewHeight) {
        int firstRow = Math.max(0, (int) Math.floor(scrollY / (float) rowHeight));
        int lastRow = Math.min(sheet.rowCount(), firstRow + viewHeight / rowHeight + 2);

        int firstColumn = 0;
        while (firstColumn < columnWidths.length && columnRight[firstColumn] <= scrollX) firstColumn++;
        int lastColumn = firstColumn;
        int limit = (int) (scrollX + viewWidth + gutterWidth);
        while (lastColumn < columnWidths.length && columnLeft[lastColumn] < limit) lastColumn++;

        for (int r = firstRow; r < lastRow; r++) {
            float top = headerHeight + r * (float) rowHeight;
            for (int c = firstColumn; c < lastColumn; c++) {
                long id = key(r, c);
                if (mergedAway.contains(id)) continue;

                int[] merge = mergeAnchors.get(id);
                float left = columnLeft[c];
                float right = columnRight[c];
                float bottom = top + rowHeight;
                if (merge != null) {
                    int endColumn = Math.min(merge[3], columnWidths.length - 1);
                    right = columnRight[endColumn];
                    bottom = headerHeight + (merge[2] + 1) * (float) rowHeight;
                }
                rect.set(left, top, right, bottom);

                Cell cell = sheet.cell(r, c);
                if (cell != null && cell.background != 0) {
                    cellBackground.setColor(cell.background);
                    canvas.drawRect(rect, cellBackground);
                }
                canvas.drawRect(rect, grid);
                if (cell != null && cell.text != null && !cell.text.isEmpty()) {
                    drawCellText(canvas, cell, rect);
                }
            }
        }
    }

    private void drawCellText(Canvas canvas, Cell cell, RectF bounds) {
        cellText.setColor(cell.color != 0 ? cell.color : colorOnSurface);
        cellText.setFakeBoldText(cell.bold);
        cellText.setTextSkewX(cell.italic ? -0.25f : 0f);

        int align = cell.align;
        if (align == Cell.ALIGN_GENERAL) {
            align = cell.isNumeric() ? Cell.ALIGN_RIGHT : Cell.ALIGN_LEFT;
        }
        float padding = dp(4);
        float x;
        if (align == Cell.ALIGN_RIGHT) {
            cellText.setTextAlign(Paint.Align.RIGHT);
            x = bounds.right - padding;
        } else if (align == Cell.ALIGN_CENTER) {
            cellText.setTextAlign(Paint.Align.CENTER);
            x = bounds.centerX();
        } else {
            cellText.setTextAlign(Paint.Align.LEFT);
            x = bounds.left + padding;
        }
        float baseline = bounds.top + bounds.height() / 2f
                - (cellText.descent() + cellText.ascent()) / 2f;

        int save = canvas.save();
        canvas.clipRect(bounds);
        canvas.drawText(cell.text, x, baseline, cellText);
        canvas.restoreToCount(save);
    }

    private void drawColumnHeaders(Canvas canvas, int viewWidth) {
        int firstColumn = 0;
        while (firstColumn < columnWidths.length && columnRight[firstColumn] <= scrollX) firstColumn++;
        int lastColumn = firstColumn;
        int limit = (int) (scrollX + viewWidth + gutterWidth);
        while (lastColumn < columnWidths.length && columnLeft[lastColumn] < limit) lastColumn++;

        for (int c = firstColumn; c < lastColumn; c++) {
            float left = columnLeft[c];
            float right = columnRight[c];
            canvas.drawRect(left, 0f, right, headerHeight, headerBand);
            canvas.drawRect(left, 0f, right, headerHeight, grid);
            float baseline = headerHeight / 2f - (headerText.descent() + headerText.ascent()) / 2f;
            canvas.drawText(columnName(c), (left + right) / 2f, baseline, headerText);
        }
    }

    private void drawRowHeaders(Canvas canvas, int viewHeight) {
        int firstRow = Math.max(0, (int) Math.floor(scrollY / (float) rowHeight));
        int lastRow = Math.min(sheet.rowCount(), firstRow + viewHeight / rowHeight + 2);
        for (int r = firstRow; r < lastRow; r++) {
            float top = headerHeight + r * (float) rowHeight;
            canvas.drawRect(0f, top, gutterWidth, top + rowHeight, headerBand);
            canvas.drawRect(0f, top, gutterWidth, top + rowHeight, grid);
            float baseline = top + rowHeight / 2f - (headerText.descent() + headerText.ascent()) / 2f;
            canvas.drawText(String.valueOf(r + 1), gutterWidth / 2f, baseline, headerText);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (sheet == null) return false;
        velocityTracker.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!scroller.isFinished()) scroller.abortAnimation();
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                dragging = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                float dx = event.getX() - lastTouchX;
                float dy = event.getY() - lastTouchY;
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                scrollX -= dx;
                scrollY -= dy;
                clampScroll();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    velocityTracker.computeCurrentVelocity(1000);
                    scroller.fling((int) scrollX, (int) scrollY,
                            (int) -velocityTracker.getXVelocity(), (int) -velocityTracker.getYVelocity(),
                            0, maxScrollX(), 0, maxScrollY());
                    postInvalidateOnAnimation();
                    performClick();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollX = scroller.getCurrX();
            scrollY = scroller.getCurrY();
            clampScroll();
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // clear() et non recycle() : la vue peut etre rattachee (rotation, changement de theme).
        velocityTracker.clear();
        if (!scroller.isFinished()) scroller.abortAnimation();
    }

    private void clampScroll() {
        if (scrollX < 0f) scrollX = 0f;
        if (scrollY < 0f) scrollY = 0f;
        float maxX = maxScrollX();
        float maxY = maxScrollY();
        if (scrollX > maxX) scrollX = maxX;
        if (scrollY > maxY) scrollY = maxY;
    }

    private int maxScrollX() {
        return Math.max(0, contentWidth - (getWidth() - gutterWidth));
    }

    private int maxScrollY() {
        return Math.max(0, contentHeight - (getHeight() - headerHeight));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }

    private static long key(int row, int column) {
        return ((long) row << 32) | (column & 0xffffffffL);
    }

    static String columnName(int index) {
        StringBuilder sb = new StringBuilder(3);
        int value = index;
        do {
            sb.insert(0, (char) ('A' + value % 26));
            value = value / 26 - 1;
        } while (value >= 0);
        return sb.toString();
    }
}
