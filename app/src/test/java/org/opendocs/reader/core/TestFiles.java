/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core;

import java.io.File;

/** Localise le dossier samples/ quel que soit le repertoire de travail du test. */
public final class TestFiles {

    private TestFiles() {
    }

    public static File sample(String name) {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        for (int i = 0; i < 6 && dir != null; i++) {
            File candidate = new File(dir, "samples/" + name);
            if (candidate.isFile()) return candidate;
            dir = dir.getParentFile();
        }
        throw new IllegalStateException("Echantillon introuvable : samples/" + name
                + " (lancer : python3 tools/make_samples.py --out samples)");
    }
}
