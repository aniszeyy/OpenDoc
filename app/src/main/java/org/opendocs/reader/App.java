/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader;

import android.app.Application;

import org.opendocs.reader.data.Prefs;
import org.opendocs.reader.data.RecentStore;
import org.opendocs.reader.util.UriFiles;

import java.util.concurrent.Executors;

/** Point d'entree : applique le theme puis nettoie le cache de travail. */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Doit etre pose avant la creation de la premiere activite.
        Prefs.applyTheme(this);
        final Application app = this;
        Executors.newSingleThreadExecutor().execute(() -> {
            UriFiles.clearWorkCache(app);
            RecentStore.trim(app);
        });
    }
}
