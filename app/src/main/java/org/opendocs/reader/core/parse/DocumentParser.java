/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.parse;

import org.opendocs.reader.core.model.DocType;
import org.opendocs.reader.core.model.ParsedDocument;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Point d'entree unique du parsing : detecte le format reel (signature ZIP +
 * contenu de l'archive, pas seulement l'extension) puis delegue au parseur.
 *
 * <p>Aucune dependance Android ici : cette classe tourne aussi en test JVM.</p>
 */
public final class DocumentParser {

    /** Taille maximale d'un CSV charge en memoire. */
    public static final long MAX_CSV_BYTES = 32L * 1024 * 1024;

    private DocumentParser() {
    }

    public static ParsedDocument parse(File file, String displayName, String mimeHint) throws IOException {
        long started = System.currentTimeMillis();
        ParsedDocument doc = new ParsedDocument();
        doc.fileName = displayName == null || displayName.isEmpty() ? file.getName() : displayName;
        doc.fileSize = file.length();
        doc.type = detect(file, doc.fileName, mimeHint);

        switch (doc.type) {
            case DOCX:
            case PPTX:
            case XLSX: {
                OoxmlPackage pkg = new OoxmlPackage(file);
                try {
                    if (doc.type == DocType.DOCX) DocxParser.parse(pkg, doc);
                    else if (doc.type == DocType.PPTX) PptxParser.parse(pkg, doc);
                    else XlsxParser.parse(pkg, doc);
                } finally {
                    pkg.close();
                }
                break;
            }
            case CSV: {
                if (file.length() > MAX_CSV_BYTES) {
                    throw new IOException("Fichier texte trop volumineux (> 32 Mo)");
                }
                byte[] data = readAll(file);
                CsvParser.parse(data, doc);
                break;
            }
            default:
                throw new IOException("Format non reconnu : seuls DOCX, PPTX, XLSX et CSV sont supportes");
        }
        doc.computeStats();
        doc.parseTimeMs = System.currentTimeMillis() - started;
        return doc;
    }

    /** Detection : extension, MIME, puis signature et contenu de l'archive. */
    public static DocType detect(File file, String name, String mimeHint) throws IOException {
        DocType byName = DocType.fromFileName(name);
        if (byName != DocType.UNKNOWN) return byName;
        DocType byMime = DocType.fromMimeType(mimeHint);
        if (byMime != DocType.UNKNOWN) return byMime;

        byte[] header = new byte[4];
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            int read = raf.read(header);
            if (read < 2) return DocType.UNKNOWN;
        } finally {
            raf.close();
        }
        boolean zip = header[0] == 'P' && header[1] == 'K';
        if (zip) {
            OoxmlPackage pkg = new OoxmlPackage(file);
            try {
                if (pkg.has("word/document.xml")) return DocType.DOCX;
                if (pkg.has("ppt/presentation.xml")) return DocType.PPTX;
                if (pkg.has("xl/workbook.xml")) return DocType.XLSX;
            } finally {
                pkg.close();
            }
            return DocType.UNKNOWN;
        }
        // Pas une archive : on tente le texte (CSV sans extension).
        return DocType.CSV;
    }

    private static byte[] readAll(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            raf.readFully(data);
        } finally {
            raf.close();
        }
        return data;
    }
}
