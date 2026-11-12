/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.opendocs.reader.R;
import org.opendocs.reader.core.model.DocType;
import org.opendocs.reader.data.RecentFile;
import org.opendocs.reader.util.Formats;

import java.util.List;

/** Liste des documents recents : vignette de type, nom, taille et date. */
public class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.Holder> {

    public interface Listener {
        void onOpen(@NonNull RecentFile file);

        void onRemove(@NonNull RecentFile file);
    }

    private final List<RecentFile> items;
    private final Listener listener;

    public RecentAdapter(List<RecentFile> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView badge;
        final TextView name;
        final TextView meta;
        final ImageButton remove;

        Holder(View view) {
            super(view);
            badge = view.findViewById(R.id.badge);
            name = view.findViewById(R.id.name);
            meta = view.findViewById(R.id.meta);
            remove = view.findViewById(R.id.remove);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final RecentFile file = items.get(position);
        Context context = holder.itemView.getContext();

        holder.name.setText(file.name);
        holder.meta.setText(subtitle(context, file));
        holder.badge.setText(Formats.typeLabel(file.type));

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(10f * context.getResources().getDisplayMetrics().density);
        background.setColor(badgeColor(context, file.type));
        holder.badge.setBackground(background);

        holder.itemView.setOnClickListener(v -> listener.onOpen(file));
        holder.remove.setOnClickListener(v -> listener.onRemove(file));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String subtitle(Context context, RecentFile file) {
        StringBuilder sb = new StringBuilder();
        if (file.size > 0) sb.append(Formats.size(file.size));
        if (file.openedAt > 0) {
            if (sb.length() > 0) sb.append(" \u00b7 ");
            sb.append(Formats.date(file.openedAt));
        }
        if (sb.length() == 0) sb.append(Formats.typeLabel(file.type));
        return sb.toString();
    }

    private static int badgeColor(Context context, DocType type) {
        int res;
        switch (type) {
            case DOCX:
                res = R.color.badge_docx;
                break;
            case PPTX:
                res = R.color.badge_pptx;
                break;
            case XLSX:
                res = R.color.badge_xlsx;
                break;
            default:
                res = R.color.badge_csv;
                break;
        }
        return ContextCompat.getColor(context, res);
    }
}
