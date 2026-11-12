/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.model;

/** Formats supportes par le lecteur. */
public enum DocType {

    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("csv", "text/csv"),
    UNKNOWN("", "application/octet-stream");

    private final String extension;
    private final String mimeType;

    DocType(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    public String extension() {
        return extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public boolean isOffice() {
        return this == DOCX || this == PPTX || this == XLSX;
    }

    /** Detection par nom de fichier (le contenu est verifie ensuite par DocumentParser). */
    public static DocType fromFileName(String name) {
        if (name == null) return UNKNOWN;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return UNKNOWN;
        String ext = name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        switch (ext) {
            case "docx":
            case "docm":
                return DOCX;
            case "pptx":
            case "pptm":
                return PPTX;
            case "xlsx":
            case "xlsm":
                return XLSX;
            case "csv":
            case "tsv":
            case "tab":
                return CSV;
            default:
                return UNKNOWN;
        }
    }

    public static DocType fromMimeType(String mime) {
        if (mime == null) return UNKNOWN;
        String m = mime.toLowerCase(java.util.Locale.ROOT);
        if (m.contains("wordprocessingml")) return DOCX;
        if (m.contains("presentationml")) return PPTX;
        if (m.contains("spreadsheetml")) return XLSX;
        if (m.contains("csv") || m.contains("comma-separated") || m.contains("tab-separated")) return CSV;
        return UNKNOWN;
    }
}
