/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.ui;

import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;

import org.opendocs.reader.BuildConfig;
import org.opendocs.reader.R;
import org.opendocs.reader.data.Prefs;
import org.opendocs.reader.data.RecentStore;

/** Reglages : theme, taille de texte, nombre de recents. */
public class SettingsActivity extends BaseActivity {

    private int pendingFontScale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView version = findViewById(R.id.version);
        version.setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));

        setupTheme();
        setupFontScale();
        setupRecentsMax();

        findViewById(R.id.clearRecents).setOnClickListener(v -> {
            RecentStore.clear(this);
            Toast.makeText(this, R.string.recents_cleared, Toast.LENGTH_SHORT).show();
        });
    }

    private void setupTheme() {
        RadioGroup group = findViewById(R.id.themeGroup);
        int theme = Prefs.theme(this);
        if (theme == Prefs.THEME_LIGHT) group.check(R.id.themeLight);
        else if (theme == Prefs.THEME_DARK) group.check(R.id.themeDark);
        else group.check(R.id.themeSystem);

        group.setOnCheckedChangeListener((radioGroup, checkedId) -> {
            int value = checkedId == R.id.themeLight ? Prefs.THEME_LIGHT
                    : checkedId == R.id.themeDark ? Prefs.THEME_DARK
                    : Prefs.THEME_SYSTEM;
            if (value == Prefs.theme(this)) return;
            Prefs.setTheme(this, value);
            Prefs.applyTheme(this);
        });
    }

    private void setupFontScale() {
        final SeekBar seekBar = findViewById(R.id.fontScale);
        final TextView value = findViewById(R.id.fontScaleValue);
        final int steps = (Prefs.FONT_MAX - Prefs.FONT_MIN) / Prefs.FONT_STEP;

        pendingFontScale = Prefs.fontScalePercent(this);
        value.setText(getString(R.string.pref_font_scale_value, pendingFontScale));
        seekBar.setMax(steps);
        seekBar.setProgress((pendingFontScale - Prefs.FONT_MIN) / Prefs.FONT_STEP);

        seekBar.setOnSeekBarChangeListener(new SeekListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                pendingFontScale = Prefs.FONT_MIN + progress * Prefs.FONT_STEP;
                value.setText(getString(R.string.pref_font_scale_value, pendingFontScale));
                Prefs.setFontScalePercent(SettingsActivity.this, pendingFontScale);
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                // La taille est lue a l'attache du contexte : il faut recréer l'ecran.
                recreate();
            }
        });
    }

    private void setupRecentsMax() {
        final SeekBar seekBar = findViewById(R.id.recentsMax);
        final TextView value = findViewById(R.id.recentsValue);
        final int steps = (Prefs.RECENTS_LIMIT - Prefs.RECENTS_MIN) / Prefs.RECENTS_STEP;

        value.setText(String.valueOf(Prefs.recentsMax(this)));
        seekBar.setMax(steps);
        seekBar.setProgress((Prefs.recentsMax(this) - Prefs.RECENTS_MIN) / Prefs.RECENTS_STEP);

        seekBar.setOnSeekBarChangeListener(new SeekListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int max = Prefs.RECENTS_MIN + progress * Prefs.RECENTS_STEP;
                value.setText(String.valueOf(max));
                Prefs.setRecentsMax(SettingsActivity.this, max);
                RecentStore.trim(SettingsActivity.this);
            }
        });
    }

    /** Evite de reecrire les trois methodes quand seule onProgressChanged nous interesse. */
    private abstract static class SeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
