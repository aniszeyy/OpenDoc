/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.parse;

import org.opendocs.reader.core.xml.XmlNode;
import org.opendocs.reader.core.xml.XmlTree;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Un fichier OOXML (docx/pptx/xlsx) est une archive ZIP : on l'ouvre avec
 * java.util.zip.ZipFile, sans aucune bibliotheque tierce.
 *
 * <p>Protections anti "zip bomb" : nombre d'entrees, taille decompressee par
 * entree et volume cumule sont bornes.</p>
 */
public final class OoxmlPackage implements Closeable {

    public static final long MAX_ENTRY_BYTES = 96L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 384L * 1024 * 1024;
    public static final int MAX_ENTRIES = 20000;

    private final ZipFile zip;
    private final long fileSize;
    private long totalRead;

    public OoxmlPackage(File file) throws IOException {
        this.fileSize = file.length();
        this.zip = new ZipFile(file);
        if (zip.size() > MAX_ENTRIES) {
            zip.close();
            throw new IOException("Archive trop complexe : " + zip.size() + " entrees");
        }
    }

    public long fileSize() {
        return fileSize;
    }

    public boolean has(String path) {
        return zip.getEntry(path) != null;
    }

    /** Lit une entree ; renvoie null si elle n'existe pas. */
    public byte[] read(String path) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) return null;
        long declared = entry.getSize();
        if (declared > MAX_ENTRY_BYTES) {
            throw new IOException("Entree trop volumineuse : " + path + " (" + declared + " octets)");
        }
        InputStream in = zip.getInputStream(entry);
        try {
            LimitedInputStream limited = new LimitedInputStream(in, MAX_ENTRY_BYTES);
            byte[] data = readFully(limited, declared > 0 ? (int) Math.min(declared, MAX_ENTRY_BYTES) : 8192);
            totalRead += data.length;
            if (totalRead > MAX_TOTAL_BYTES) {
                throw new IOException("Document trop volumineux une fois decompresse");
            }
            return data;
        } finally {
            closeQuietly(in);
        }
    }

    /** Flux borne, pour le parsing en streaming des grandes feuilles. */
    public InputStream open(String path) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) return null;
        return new LimitedInputStream(zip.getInputStream(entry), MAX_ENTRY_BYTES);
    }

    /** Lit et parse une entree XML ; renvoie null si absente. */
    public XmlNode xml(String path) throws IOException {
        byte[] data = read(path);
        if (data == null) return null;
        return XmlTree.parse(data);
    }

    public List<String> names() {
        List<String> out = new ArrayList<>(zip.size());
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) out.add(entries.nextElement().getName());
        Collections.sort(out);
        return out;
    }

    /** Noms d'entrees correspondant a un prefixe et un suffixe (ex. slides/slide*.xml). */
    public List<String> namesMatching(String prefix, String suffix) {
        List<String> out = new ArrayList<>();
        for (String name : names()) {
            if (name.startsWith(prefix) && name.endsWith(suffix)) out.add(name);
        }
        return out;
    }

    @Override
    public void close() {
        closeQuietly(zip);
    }

    public static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
            // fermeture silencieuse
        }
    }

    private static byte[] readFully(InputStream in, int initialCapacity) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(Math.max(1024, initialCapacity));
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    /** InputStream qui refuse de depasser une taille maximale. */
    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int c = super.read();
            if (c >= 0) account(1);
            return c;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) account(n);
            return n;
        }

        private void account(long n) throws IOException {
            count += n;
            if (count > limit) throw new IOException("flux XML trop volumineux (> " + limit + " octets)");
        }
    }

    /** Helper : flux d'un tableau d'octets (utilise par les parsers et les tests). */
    public static InputStream streamOf(byte[] data) {
        return new ByteArrayInputStream(data);
    }
}
