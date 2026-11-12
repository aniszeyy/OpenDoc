/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** Reglages persistants (theme, taille de texte, nombre de recents). */
public final class Prefs {

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final int FONT_MIN = 80;
    public static final int FONT_MAX = 160;
    public static final int FONT_STEP = 4;
    public static final int RECENTS_MIN = 5;
    public static final int RECENTS_LIMIT = 50;
    public static final int RECENTS_STEP = 5;

    private static final String FILE = "opendocs_settings";
    private static final String KEY_THEME = "theme";
    private static final String KEY_FONT_SCALE = "font_scale";
    private static final String KEY_RECENTS_MAX = "recents_max";

    private Prefs() {
    }

    private static SharedPreferences sp(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int theme(Context context) {
        return clamp(sp(context).getInt(KEY_THEME, THEME_SYSTEM), THEME_SYSTEM, THEME_DARK);
    }

    public static void setTheme(Context context, int value) {
        sp(context).edit().putInt(KEY_THEME, clamp(value, THEME_SYSTEM, THEME_DARK)).apply();
    }

    public static int nightMode(int theme) {
        switch (theme) {
            case THEME_LIGHT:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case THEME_DARK:
                return AppCompatDelegate.MODE_NIGHT_YES;
            default:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }

    /** Applique le theme choisi ; AppCompat recree les activites si necessaire. */
    public static void applyTheme(Context context) {
        AppCompatDelegate.setDefaultNightMode(nightMode(theme(context)));
    }

    public static int fontScalePercent(Context context) {
        return clamp(sp(context).getInt(KEY_FONT_SCALE, 100), FONT_MIN, FONT_MAX);
    }

    public static void setFontScalePercent(Context context, int value) {
        sp(context).edit().putInt(KEY_FONT_SCALE, clamp(value, FONT_MIN, FONT_MAX)).apply();
    }

    public static int recentsMax(Context context) {
        return clamp(sp(context).getInt(KEY_RECENTS_MAX, 20), RECENTS_MIN, RECENTS_LIMIT);
    }

    public static void setRecentsMax(Context context, int value) {
        sp(context).edit().putInt(KEY_RECENTS_MAX, clamp(value, RECENTS_MIN, RECENTS_LIMIT)).apply();
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
