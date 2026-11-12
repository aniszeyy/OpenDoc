/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Pont entre les URI du Storage Access Framework et les fichiers locaux :
 * le coeur de parsing travaille sur des File, pas sur des InputStream.
 */
public final class UriFiles {

    private static final int BUFFER = 64 * 1024;

    private UriFiles() {
    }

    @Nullable
    public static String displayName(Context context, Uri uri) {
        String name = query(context, uri, OpenableColumns.DISPLAY_NAME);
        if (name != null && !name.isEmpty()) return name;
        String last = uri.getLastPathSegment();
        if (last == null) return null;
        int slash = last.lastIndexOf('/');
        return slash >= 0 ? last.substring(slash + 1) : last;
    }

    public static long size(Context context, Uri uri) {
        String value = query(context, uri, OpenableColumns.SIZE);
        if (value == null) return -1;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Nullable
    public static String mimeType(Context context, Uri uri) {
        if (uri == null) return null;
        String type = context.getContentResolver().getType(uri);
        if (type != null && !type.isEmpty()) return type;
        return null;
    }

    @Nullable
    private static String query(Context context, Uri uri, String column) {
        if (uri == null) return null;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{column}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(column);
                if (index >= 0 && !cursor.isNull(index)) return cursor.getString(index);
            }
        } catch (Exception ignored) {
            // Fournisseur capricieux : on retombe sur le nom de l'URI.
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    /** Copie le document dans le cache de l'application et renvoie le fichier local. */
    @NonNull
    public static File importToCache(Context context, Uri uri, String displayName) throws IOException {
        File dir = new File(context.getCacheDir(), "imports");
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IOException("Dossier de travail inaccessible");
        }
        File target = new File(dir, safeName(displayName));
        ContentResolver resolver = context.getContentResolver();
        InputStream in = resolver.openInputStream(uri);
        if (in == null) throw new IOException("Flux illisible pour " + uri);
        OutputStream out = new FileOutputStream(target);
        try {
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
        return target;
    }

    /** Ecrit un export texte dans le cache et renvoie le fichier correspondant. */
    @NonNull
    public static File exportText(Context context, String baseName, String text) throws IOException {
        File dir = new File(context.getCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IOException("Dossier d'export inaccessible");
        }
        File target = new File(dir, safeName(baseName) + ".txt");
        FileOutputStream out = new FileOutputStream(target);
        try {
            out.write(text.getBytes("UTF-8"));
            out.flush();
        } finally {
            closeQuietly(out);
        }
        return target;
    }

    /** Conserve l'autorisation de lecture entre deux lancements, si le fournisseur l'accorde. */
    public static boolean takePersistableRead(Context context, Uri uri) {
        if (uri == null) return false;
        try {
            context.getContentResolver().takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    /** Supprime tout le cache de travail (imports et exports). */
    public static void clearWorkCache(Context context) {
        deleteRecursively(new File(context.getCacheDir(), "imports"));
        deleteRecursively(new File(context.getCacheDir(), "exports"));
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static String safeName(String name) {
        String base = name == null || name.trim().isEmpty() ? "document" : name.trim();
        StringBuilder sb = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            boolean bad = c < 32 || c == '/' || c == '\\' || c == ':' || c == '*' || c == '?'
                    || c == '"' || c == '<' || c == '>' || c == '|';
            sb.append(bad ? '_' : c);
        }
        String cleaned = sb.toString();
        if (cleaned.length() > 120) {
            int dot = cleaned.lastIndexOf('.');
            String ext = dot > 0 ? cleaned.substring(dot) : "";
            cleaned = cleaned.substring(0, 100) + ext;
        }
        return cleaned;
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
            // rien a faire
        }
    }
}
