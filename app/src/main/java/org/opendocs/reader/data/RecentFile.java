/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.opendocs.reader.core.model.DocType;

/** Entree de la liste "Documents recents". */
public final class RecentFile {

    public final String name;
    public final String uri;
    public final String mime;
    public final long size;
    public final long openedAt;
    public final DocType type;

    public RecentFile(String name, String uri, String mime, long size, long openedAt, DocType type) {
        this.name = name == null ? "" : name;
        this.uri = uri == null ? "" : uri;
        this.mime = mime == null ? "" : mime;
        this.size = size;
        this.openedAt = openedAt;
        this.type = type == null ? DocType.UNKNOWN : type;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("uri", uri);
        o.put("mime", mime);
        o.put("size", size);
        o.put("openedAt", openedAt);
        o.put("type", type.name());
        return o;
    }

    @Nullable
    public static RecentFile fromJson(JSONObject o) {
        String uri = o.optString("uri", "");
        if (uri.isEmpty()) return null;
        DocType type;
        try {
            type = DocType.valueOf(o.optString("type", DocType.UNKNOWN.name()));
        } catch (IllegalArgumentException e) {
            type = DocType.UNKNOWN;
        }
        return new RecentFile(
                o.optString("name", ""),
                uri,
                o.optString("mime", ""),
                o.optLong("size", 0L),
                o.optLong("openedAt", 0L),
                type);
    }
}
