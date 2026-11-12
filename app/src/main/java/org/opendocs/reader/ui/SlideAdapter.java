/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.ui;

import android.graphics.Bitmap;
import android.util.LruCache;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.opendocs.reader.core.model.ParsedDocument;

/** Une diapositive par page ; les images decodees sont partagees entre les diapositives. */
public class SlideAdapter extends RecyclerView.Adapter<SlideAdapter.Holder> {

    private static final int IMAGE_CACHE_BYTES = 12 * 1024 * 1024;

    private final ParsedDocument document;
    private final LruCache<String, Bitmap> images;

    public SlideAdapter(ParsedDocument document) {
        this.document = document;
        this.images = new LruCache<String, Bitmap>(IMAGE_CACHE_BYTES) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    static class Holder extends RecyclerView.ViewHolder {
        final SlideView view;

        Holder(SlideView view) {
            super(view);
            this.view = view;
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        SlideView view = new SlideView(parent.getContext());
        view.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.view.setSlide(document.slides.get(position),
                document.slideWidthEmu, document.slideHeightEmu, images);
    }

    @Override
    public int getItemCount() {
        return document.slides.size();
    }
}
