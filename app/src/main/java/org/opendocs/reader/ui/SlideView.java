/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.LruCache;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import org.opendocs.reader.core.model.Paragraph;
import org.opendocs.reader.core.model.Shape;
import org.opendocs.reader.core.model.Slide;
import org.opendocs.reader.core.model.Table;
import org.opendocs.reader.util.Images;

import java.util.ArrayList;
import java.util.List;

/**
 * Rendu Canvas d'une diapositive PPTX. Les coordonnees du modele sont en EMU :
 * une seule mise a l'echelle par la largeur suffit a rester fidele au 16:9 / 4:3.
 */
public class SlideView extends View {

    private static final float SHAPE_INSET_RATIO = 0.03f;

    private Slide slide;
    private long slideWidthEmu = 9144000L;
    private long slideHeightEmu = 6858000L;
    private LruCache<String, Bitmap> imageCache;

    private final List<Op> ops = new ArrayList<>();
    private int builtWidth = -1;
    private float scale = 1f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tableText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF destination = new RectF();

    private final int colorSurface;
    private final int colorOnSurface;
    private final int colorSurfaceVariant;

    private static final class Op {
        Shape shape;
        final RectF rect = new RectF();
        final List<StaticLayout> layouts = new ArrayList<>(0);
        Bitmap bitmap;
    }

    public SlideView(Context context) {
        super(context);
        colorSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface);
        colorOnSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
        colorSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant);
        stroke.setStyle(Paint.Style.STROKE);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(1f);
    }

    public void setSlide(Slide slide, long widthEmu, long heightEmu, LruCache<String, Bitmap> cache) {
        this.slide = slide;
        if (widthEmu > 0) this.slideWidthEmu = widthEmu;
        if (heightEmu > 0) this.slideHeightEmu = heightEmu;
        this.imageCache = cache;
        this.builtWidth = -1;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) width = getSuggestedMinimumWidth();
        if (width <= 0) width = 480;
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int height = heightMode == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(heightMeasureSpec)
                : Math.round(width * (slideHeightEmu / (float) slideWidthEmu));
        setMeasuredDimension(width, Math.max(1, height));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (slide == null) return;
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        if (builtWidth != width) {
            build(width);
            builtWidth = width;
        }

        canvas.drawColor(slide.background != 0 ? slide.background : colorSurface);
        for (int i = 0; i < ops.size(); i++) {
            drawOp(canvas, ops.get(i));
        }
    }

    private void build(int width) {
        ops.clear();
        scale = width / (float) slideWidthEmu;
        if (slide == null) return;
        List<Shape> shapes = slide.shapes;
        for (int i = 0; i < shapes.size(); i++) {
            collect(shapes.get(i));
        }
    }

    private void collect(Shape shape) {
        if (shape == null) return;
        if (shape.kind == Shape.Kind.GROUP) {
            for (int i = 0; i < shape.children.size(); i++) {
                collect(shape.children.get(i));
            }
            return;
        }
        Op op = new Op();
        op.shape = shape;
        op.rect.set(emu(shape.x), emu(shape.y), emu(shape.x + shape.cx), emu(shape.y + shape.cy));
        switch (shape.kind) {
            case PICTURE:
                op.bitmap = decode(shape);
                break;
            case TABLE:
                tableText.setTextSize(TextFormat.sizePx(getContext(), 9f));
                break;
            default:
                buildLayouts(op);
                break;
        }
        ops.add(op);
    }

    private float emu(long value) {
        return value * scale;
    }

    private void buildLayouts(Op op) {
        Shape shape = op.shape;
        if (shape.paragraphs.isEmpty()) return;
        float inset = Math.max(2f, op.rect.width() * SHAPE_INSET_RATIO);
        int textWidth = Math.max(1, Math.round(op.rect.width() - inset * 2f));

        for (int i = 0; i < shape.paragraphs.size(); i++) {
            Paragraph paragraph = shape.paragraphs.get(i);
            CharSequence content = TextFormat.paragraph(getContext(), paragraph);
            if (content.length() == 0) continue;

            float points = paragraph.headingLevel > 0
                    ? TextFormat.headingPt(paragraph.headingLevel)
                    : (shape.defaultSizePt > 0 ? shape.defaultSizePt : TextFormat.baseSizePt(paragraph));

            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(TextFormat.sizePx(getContext(), points));
            paint.setColor(shape.defaultColor != 0 ? shape.defaultColor : colorOnSurface);

            Layout.Alignment alignment = paragraph.align == Paragraph.ALIGN_CENTER
                    ? Layout.Alignment.ALIGN_CENTER
                    : paragraph.align == Paragraph.ALIGN_RIGHT
                    ? Layout.Alignment.ALIGN_OPPOSITE
                    : Layout.Alignment.ALIGN_NORMAL;

            StaticLayout layout = StaticLayout.Builder
                    .obtain(content, 0, content.length(), paint, textWidth)
                    .setAlignment(alignment)
                    .setIncludePad(false)
                    .setLineSpacing(0f, paragraph.lineSpacing > 0 ? paragraph.lineSpacing : 1f)
                    .build();
            op.layouts.add(layout);
        }
    }

    @Nullable
    private Bitmap decode(Shape shape) {
        if (shape.image == null || shape.image.length == 0) return null;
        String key = (shape.imageName == null ? "" : shape.imageName) + '#' + shape.image.length;
        if (imageCache != null) {
            Bitmap cached = imageCache.get(key);
            if (cached != null) return cached;
        }
        Bitmap bitmap = Images.decode(shape.image, 1600);
        if (bitmap != null && imageCache != null) imageCache.put(key, bitmap);
        return bitmap;
    }

    private void drawOp(Canvas canvas, Op op) {
        Shape shape = op.shape;
        int save = canvas.save();
        if (shape.rotationDegrees != 0f) {
            canvas.rotate(shape.rotationDegrees, op.rect.centerX(), op.rect.centerY());
        }
        switch (shape.kind) {
            case PICTURE:
                drawPicture(canvas, op);
                break;
            case TABLE:
                drawTable(canvas, shape.table, op.rect);
                break;
            default:
                drawGeometry(canvas, shape, op.rect);
                drawLayouts(canvas, op);
                break;
        }
        canvas.restoreToCount(save);
    }

    private void drawGeometry(Canvas canvas, Shape shape, RectF rect) {
        String geometryName = shape.geometry == null ? "" : shape.geometry;
        if ("none".equals(geometryName)) return;

        Path path = buildPath(geometryName, rect);
        if (shape.fill != 0) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(shape.fill);
            canvas.drawPath(path, fill);
        }
        if (shape.lineColor != 0 && shape.lineWidthPt > 0f) {
            stroke.setColor(shape.lineColor);
            stroke.setStrokeWidth(Math.max(1f, TextFormat.sizePx(getContext(), shape.lineWidthPt)));
            canvas.drawPath(path, stroke);
        }
    }

    private Path buildPath(String geometryName, RectF rect) {
        Path path = new Path();
        switch (geometryName) {
            case "ellipse":
            case "circle":
                path.addOval(rect, Path.Direction.CW);
                break;
            case "roundRect":
                float radius = Math.min(rect.width(), rect.height()) * 0.12f;
                path.addRoundRect(rect, radius, radius, Path.Direction.CW);
                break;
            case "line":
                path.moveTo(rect.left, rect.top);
                path.lineTo(rect.right, rect.top + Math.max(rect.height(), 0.01f));
                break;
            case "triangle":
                path.moveTo(rect.centerX(), rect.top);
                path.lineTo(rect.right, rect.bottom);
                path.lineTo(rect.left, rect.bottom);
                path.close();
                break;
            case "rtTriangle":
                path.moveTo(rect.left, rect.top);
                path.lineTo(rect.right, rect.bottom);
                path.lineTo(rect.left, rect.bottom);
                path.close();
                break;
            case "diamond":
                path.moveTo(rect.centerX(), rect.top);
                path.lineTo(rect.right, rect.centerY());
                path.lineTo(rect.centerX(), rect.bottom);
                path.lineTo(rect.left, rect.centerY());
                path.close();
                break;
            default:
                path.addRect(rect, Path.Direction.CW);
                break;
        }
        return path;
    }

    private void drawLayouts(Canvas canvas, Op op) {
        if (op.layouts.isEmpty()) return;
        float inset = Math.max(2f, op.rect.width() * SHAPE_INSET_RATIO);
        float total = 0f;
        for (int i = 0; i < op.layouts.size(); i++) {
            total += op.layouts.get(i).getHeight();
        }
        float top;
        switch (op.shape.verticalAnchor) {
            case 1:
                top = op.rect.centerY() - total / 2f;
                break;
            case 2:
                top = op.rect.bottom - inset - total;
                break;
            default:
                top = op.rect.top + inset;
                break;
        }
        if (top < op.rect.top + 1f) top = op.rect.top + 1f;

        float x = op.rect.left + inset;
        int save = canvas.save();
        canvas.clipRect(op.rect);
        for (int i = 0; i < op.layouts.size(); i++) {
            StaticLayout layout = op.layouts.get(i);
            int inner = canvas.save();
            canvas.translate(x, top);
            layout.draw(canvas);
            canvas.restoreToCount(inner);
            top += layout.getHeight();
        }
        canvas.restoreToCount(save);
    }

    private void drawPicture(Canvas canvas, Op op) {
        Bitmap bitmap = op.bitmap;
        if (bitmap == null) return;
        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();
        if (bitmapWidth <= 0f || bitmapHeight <= 0f) return;

        float fit = Math.min(op.rect.width() / bitmapWidth, op.rect.height() / bitmapHeight);
        float width = bitmapWidth * fit;
        float height = bitmapHeight * fit;
        float left = op.rect.centerX() - width / 2f;
        float top = op.rect.centerY() - height / 2f;
        destination.set(left, top, left + width, top + height);
        canvas.drawBitmap(bitmap, null, destination, null);
    }

    private void drawTable(Canvas canvas, Table table, RectF rect) {
        if (table == null) return;
        int rows = table.rowCount();
        int columns = table.columnCount();
        if (rows <= 0 || columns <= 0) return;

        float[] edges = new float[columns + 1];
        float[] weights = new float[columns];
        float totalWeight = 0f;
        for (int c = 0; c < columns; c++) {
            float weight = table.columnWeights != null && c < table.columnWeights.length
                    && table.columnWeights[c] > 0 ? table.columnWeights[c] : 1f;
            weights[c] = weight;
            totalWeight += weight;
        }
        edges[0] = rect.left;
        for (int c = 0; c < columns; c++) {
            edges[c + 1] = edges[c] + rect.width() * weights[c] / totalWeight;
        }

        float rowHeight = rect.height() / rows;
        for (int r = 0; r < rows; r++) {
            List<Table.Cell> row = table.rows.get(r);
            int column = 0;
            for (int i = 0; i < row.size() && column < columns; i++) {
                Table.Cell cell = row.get(i);
                if (cell == null) {
                    column++;
                    continue;
                }
                int span = Math.max(1, Math.min(cell.colSpan, columns - column));
                int rowSpan = Math.max(1, cell.rowSpan);

                float left = edges[column];
                float right = edges[Math.min(columns, column + span)];
                float top = rect.top + r * rowHeight;
                float bottom = Math.min(rect.bottom, top + rowSpan * rowHeight);
                destination.set(left, top, right, bottom);

                boolean header = cell.header || (table.firstRowIsHeader && r == 0);
                if (cell.background != 0) {
                    fill.setStyle(Paint.Style.FILL);
                    fill.setColor(cell.background);
                    canvas.drawRect(destination, fill);
                } else if (header) {
                    fill.setStyle(Paint.Style.FILL);
                    fill.setColor(colorSurfaceVariant);
                    canvas.drawRect(destination, fill);
                }
                canvas.drawRect(destination, grid);

                String text = cell.text().replace('\n', ' ');
                if (!text.isEmpty()) {
                    tableText.setColor(colorOnSurface);
                    tableText.setFakeBoldText(header);
                    int save = canvas.save();
                    canvas.clipRect(destination);
                    float baseline = destination.top + destination.height() / 2f
                            - (tableText.descent() + tableText.ascent()) / 2f;
                    canvas.drawText(text, destination.left + 4f, baseline, tableText);
                    canvas.restoreToCount(save);
                }
                column += span;
            }
        }
    }
}
