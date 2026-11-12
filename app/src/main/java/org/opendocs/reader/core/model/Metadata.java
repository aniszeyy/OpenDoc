/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metadonnees du document : proprietes Dublin Core (docProps/core.xml),
 * informations applicatives (docProps/app.xml) et statistiques calculees.
 */
public final class Metadata {

    public String title = "";
    public String subject = "";
    public String creator = "";
    public String description = "";
    public String keywords = "";
    public String lastModifiedBy = "";
    public String created = "";
    public String modified = "";
    public String revision = "";
    public String application = "";
    public String appVersion = "";

    /** Champs supplementaires affiches dans la fiche "Proprietes". */
    public final Map<String, String> extra = new LinkedHashMap<>();
    /** Statistiques calculees pendant le parsing (mots, lignes, slides...). */
    public final Map<String, String> stats = new LinkedHashMap<>();

    public void putExtra(String key, String value) {
        if (value != null && !value.trim().isEmpty()) extra.put(key, value.trim());
    }

    public void putStat(String key, String value) {
        if (value != null) stats.put(key, value);
    }

    public void putStat(String key, long value) {
        stats.put(key, String.valueOf(value));
    }

    public String displayTitle(String fallback) {
        return title == null || title.trim().isEmpty() ? fallback : title.trim();
    }
}
