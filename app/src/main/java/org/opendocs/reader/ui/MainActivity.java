/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.opendocs.reader.R;
import org.opendocs.reader.data.RecentFile;
import org.opendocs.reader.data.RecentStore;
import org.opendocs.reader.util.UriFiles;

import java.util.ArrayList;
import java.util.List;

/** Accueil : documents recents, ouverture via Storage Access Framework, "Ouvrir avec". */
public class MainActivity extends BaseActivity implements RecentAdapter.Listener {

    private static final String[] MIME_TYPES = {
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv",
            "text/comma-separated-values",
            "application/csv",
            "text/plain",
    };

    private final List<RecentFile> items = new ArrayList<>();
    private RecentAdapter adapter;
    private View emptyState;

    private final ActivityResultLauncher<String[]> openDocument =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onDocumentPicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            if (id == R.id.action_clear_recents) {
                confirmClearRecents();
                return true;
            }
            return false;
        });

        RecyclerView recents = findViewById(R.id.recents);
        recents.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecentAdapter(items, this);
        recents.setAdapter(adapter);

        emptyState = findViewById(R.id.emptyState);
        findViewById(R.id.openFab).setOnClickListener(v -> openDocument.launch(MIME_TYPES));

        if (savedInstanceState == null) handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        items.clear();
        items.addAll(RecentStore.load(this));
        adapter.notifyDataSetChanged();
        emptyState.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Traite un lancement externe "Ouvrir avec OpenDocs". */
    private void handleIntent(@Nullable Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;
        Uri uri = intent.getData();
        if (uri == null) return;
        if ((intent.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
            UriFiles.takePersistableRead(this, uri);
        }
        openViewer(uri, UriFiles.displayName(this, uri), UriFiles.mimeType(this, uri));
    }

    private void onDocumentPicked(@Nullable Uri uri) {
        if (uri == null) return;
        UriFiles.takePersistableRead(this, uri);
        openViewer(uri, UriFiles.displayName(this, uri), UriFiles.mimeType(this, uri));
    }

    private void openViewer(Uri uri, @Nullable String name, @Nullable String mime) {
        Intent intent = new Intent(this, ViewerActivity.class);
        intent.setData(uri);
        if (name != null) intent.putExtra(ViewerActivity.EXTRA_DISPLAY_NAME, name);
        if (mime != null) intent.putExtra(ViewerActivity.EXTRA_MIME, mime);
        startActivity(intent);
    }

    private void confirmClearRecents() {
        if (items.isEmpty()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_recents)
                .setMessage(R.string.recent_empty_hint)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    RecentStore.clear(this);
                    refresh();
                })
                .show();
    }

    @Override
    public void onOpen(@NonNull RecentFile file) {
        Uri uri = Uri.parse(file.uri);
        openViewer(uri, file.name, file.mime);
    }

    @Override
    public void onRemove(@NonNull RecentFile file) {
        RecentStore.remove(this, file.uri);
        refresh();
    }
}
