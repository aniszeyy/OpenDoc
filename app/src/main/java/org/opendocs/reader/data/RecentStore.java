/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Persistance de la liste des documents recents (sans base de donnees). */
public final class RecentStore {

    private static final String FILE = "opendocs_recents";
    private static final String KEY_ITEMS = "items";

    private RecentStore() {
    }

    private static SharedPreferences sp(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized List<RecentFile> load(Context context) {
        String raw = sp(context).getString(KEY_ITEMS, "");
        List<RecentFile> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                RecentFile f = RecentFile.fromJson(o);
                if (f != null) out.add(f);
            }
        } catch (JSONException e) {
            // Liste illisible : on repart de zero plutot que de planter au demarrage.
            sp(context).edit().remove(KEY_ITEMS).apply();
        }
        return out;
    }

    private static void save(Context context, List<RecentFile> items) {
        JSONArray array = new JSONArray();
        for (RecentFile f : items) {
            try {
                array.put(f.toJson());
            } catch (JSONException ignored) {
                // une entree perdue ne doit pas empecher l'enregistrement des autres
            }
        }
        sp(context).edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    /** Ajoute ou remonte le document en tete, puis tronque a la limite configuree. */
    public static synchronized void add(Context context, RecentFile file) {
        List<RecentFile> items = load(context);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).uri.equals(file.uri)) items.remove(i);
        }
        items.add(0, file);
        int max = Prefs.recentsMax(context);
        while (items.size() > max) items.remove(items.size() - 1);
        save(context, items);
    }

    public static synchronized void remove(Context context, String uri) {
        List<RecentFile> items = load(context);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).uri.equals(uri)) items.remove(i);
        }
        save(context, items);
    }

    public static synchronized void clear(Context context) {
        sp(context).edit().remove(KEY_ITEMS).apply();
    }

    /** Tronque la liste si l'utilisateur a reduit le nombre de recents conserves. */
    public static synchronized void trim(Context context) {
        List<RecentFile> items = load(context);
        int max = Prefs.recentsMax(context);
        if (items.size() <= max) return;
        save(context, new ArrayList<>(items.subList(0, max)));
    }
}
