/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import org.opendocs.reader.R;
import org.opendocs.reader.core.model.Block;
import org.opendocs.reader.core.model.Paragraph;
import org.opendocs.reader.core.model.Table;
import org.opendocs.reader.util.Images;

import java.util.List;

/** Rendu d'un DOCX : paragraphes riches, tableaux, images, filets et sauts de page. */
public class DocxBlockAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final float TABLE_TEXT_PT = 10.5f;

    private final List<Block> blocks;

    public DocxBlockAdapter(List<Block> blocks) {
        this.blocks = blocks;
    }

    @Override
    public int getItemCount() {
        return blocks.size();
    }

    @Override
    public int getItemViewType(int position) {
        return blocks.get(position).kind().ordinal();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (Block.Kind.values()[viewType]) {
            case PARAGRAPH:
                return new ParagraphHolder(inflater.inflate(R.layout.item_docx_paragraph, parent, false));
            case TABLE:
                return new TableHolder(inflater.inflate(R.layout.item_docx_table, parent, false));
            case IMAGE:
                return new ImageHolder(inflater.inflate(R.layout.item_docx_image, parent, false));
            case RULE:
                return new SimpleHolder(inflater.inflate(R.layout.item_docx_rule, parent, false));
            default:
                return new SimpleHolder(inflater.inflate(R.layout.item_docx_pagebreak, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Block block = blocks.get(position);
        if (holder instanceof ParagraphHolder && block instanceof Block.ParagraphBlock) {
            ((ParagraphHolder) holder).bind((Block.ParagraphBlock) block);
        } else if (holder instanceof TableHolder && block instanceof Block.TableBlock) {
            ((TableHolder) holder).bind((Block.TableBlock) block);
        } else if (holder instanceof ImageHolder && block instanceof Block.ImageBlock) {
            ((ImageHolder) holder).bind((Block.ImageBlock) block);
        }
    }

    static class SimpleHolder extends RecyclerView.ViewHolder {
        SimpleHolder(View view) {
            super(view);
        }
    }

    static class ParagraphHolder extends RecyclerView.ViewHolder {
        private final TextView view;

        ParagraphHolder(View view) {
            super(view);
            this.view = (TextView) view;
        }

        void bind(Block.ParagraphBlock block) {
            Context context = view.getContext();
            Paragraph paragraph = block.paragraph;

            view.setText(TextFormat.paragraph(context, paragraph));
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, TextFormat.sizePx(context, TextFormat.baseSizePt(paragraph)));
            view.setTextAlignment(TextFormat.alignment(paragraph.align));
            // MovementMethod null desactiverait la selection : ArrowKeyMovementMethod la conserve.
            view.setMovementMethod(hasLink(paragraph)
                    ? LinkMovementMethod.getInstance()
                    : ArrowKeyMovementMethod.getInstance());

            if (paragraph.backgroundColor != 0) view.setBackgroundColor(paragraph.backgroundColor);
            else view.setBackground(null);

            int base = dp(context, 16);
            int extra = Math.max(0, paragraph.indentLevel) * dp(context, 16);
            if (paragraph.bullet) extra += dp(context, 16);
            view.setPaddingRelative(base + extra, dp(context, 3), base, dp(context, 3));

            if (paragraph.headingLevel > 0) {
                view.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                view.setTypeface(Typeface.DEFAULT);
            }
        }

        private static boolean hasLink(Paragraph paragraph) {
            for (org.opendocs.reader.core.model.Run run : paragraph.runs) {
                if (run.link != null && !run.link.isEmpty()) return true;
            }
            return false;
        }
    }

    static class TableHolder extends RecyclerView.ViewHolder {
        private final TableLayout layout;

        TableHolder(View view) {
            super(view);
            layout = view.findViewById(R.id.table);
        }

        void bind(Block.TableBlock block) {
            Context context = itemView.getContext();
            Table table = block.table;
            layout.removeAllViews();
            layout.setStretchAllColumns(false);

            int columns = Math.max(1, table.columnCount());
            int outline = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOutlineVariant);
            int headerFill = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorSurfaceVariant);

            int[] columnPx = columnWidths(context, table, columns);
            float stroke = Math.max(1f, context.getResources().getDisplayMetrics().density);

            for (int r = 0; r < table.rows.size(); r++) {
                List<Table.Cell> row = table.rows.get(r);
                TableRow tableRow = new TableRow(context);
                int column = 0;
                for (Table.Cell cell : row) {
                    if (cell == null || column >= columns) break;
                    int span = Math.max(1, Math.min(cell.colSpan, columns - column));

                    TextView cellView = new TextView(context);
                    cellView.setText(TextFormat.tableCell(context, cell));
                    cellView.setTextSize(TypedValue.COMPLEX_UNIT_PX, TextFormat.sizePx(context, TABLE_TEXT_PT));
                    cellView.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));

                    boolean header = cell.header || (table.firstRowIsHeader && r == 0);
                    if (header) cellView.setTypeface(Typeface.DEFAULT_BOLD);

                    GradientDrawable background = new GradientDrawable();
                    background.setShape(GradientDrawable.RECTANGLE);
                    if (cell.background != 0) background.setColor(cell.background);
                    else if (header) background.setColor(headerFill);
                    else background.setColor(Color.TRANSPARENT);
                    background.setStroke(Math.round(stroke), outline);
                    cellView.setBackground(background);

                    int width = 0;
                    for (int i = column; i < column + span; i++) width += columnPx[i];
                    TableRow.LayoutParams params =
                            new TableRow.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.span = span;
                    tableRow.addView(cellView, params);
                    column += span;
                }
                layout.addView(tableRow);
            }
        }

        /**
         * Largeurs absolues plutot que des poids : dans un HorizontalScrollView le
         * TableLayout est mesure en UNSPECIFIED et des enfants a poids vaudraient 0 px.
         */
        private static int[] columnWidths(Context context, Table table, int columns) {
            int available = context.getResources().getDisplayMetrics().widthPixels - dp(context, 32);
            int minimum = dp(context, 64);
            int[] widths = new int[columns];
            float[] weights = table.columnWeights;
            float total = 0f;
            for (int c = 0; c < columns; c++) {
                float weight = weights != null && c < weights.length && weights[c] > 0 ? weights[c] : 1f;
                widths[c] = Math.round(weight);
                total += weight;
            }
            int sum = 0;
            for (int c = 0; c < columns; c++) {
                widths[c] = Math.max(minimum, Math.round(available * (widths[c] / total)));
                sum += widths[c];
            }
            if (sum < available) {
                widths[columns - 1] += available - sum;
            }
            return widths;
        }
    }

    static class ImageHolder extends RecyclerView.ViewHolder {
        private final ImageView image;
        private final TextView caption;

        ImageHolder(View view) {
            super(view);
            image = view.findViewById(R.id.image);
            caption = view.findViewById(R.id.caption);
        }

        void bind(Block.ImageBlock block) {
            Context context = itemView.getContext();
            Bitmap bitmap = Images.decode(block.data, 2048);
            if (bitmap == null) {
                image.setVisibility(View.GONE);
            } else {
                image.setImageBitmap(bitmap);
                image.setMaxHeight(dp(context, 560));
                image.setVisibility(View.VISIBLE);
            }
            String label = block.alt != null && !block.alt.isEmpty() ? block.alt : block.name;
            if (label != null && !label.isEmpty()) {
                caption.setText(label);
                caption.setVisibility(View.VISIBLE);
            } else {
                caption.setVisibility(View.GONE);
            }
        }
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
