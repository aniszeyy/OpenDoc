/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.opendocs.reader.R;

import java.io.File;

/** Partages sortants (texte brut ou document d'origine). */
public final class Share {

    private Share() {
    }

    public static void text(Activity activity, String subject, String body) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);
        launch(activity, intent);
    }

    public static void file(Activity activity, File file, String mime, String authority) {
        if (file == null || !file.exists()) {
            Toast.makeText(activity, R.string.error_io, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(activity, authority, file);
        } catch (IllegalArgumentException e) {
            Toast.makeText(activity, R.string.error_io, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        launch(activity, intent);
    }

    private static void launch(Activity activity, Intent intent) {
        Intent chooser = Intent.createChooser(intent, activity.getString(R.string.share_via));
        try {
            activity.startActivity(chooser);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.no_app_for_share, Toast.LENGTH_SHORT).show();
        }
    }
}
