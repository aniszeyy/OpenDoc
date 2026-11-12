/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import org.opendocs.reader.R;
import org.opendocs.reader.core.model.Metadata;
import org.opendocs.reader.core.model.ParsedDocument;
import org.opendocs.reader.core.model.Sheet;
import org.opendocs.reader.core.parse.DocumentParser;
import org.opendocs.reader.data.RecentFile;
import org.opendocs.reader.data.RecentStore;
import org.opendocs.reader.util.Formats;
import org.opendocs.reader.util.Share;
import org.opendocs.reader.util.UriFiles;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lecteur : onglets Contenu / Texte / Proprietes, parsing hors interface
 * utilisateur et rendu adapte au format detecte.
 */
public class ViewerActivity extends BaseActivity {

    public static final String EXTRA_DISPLAY_NAME = "org.opendocs.reader.DISPLAY_NAME";
    public static final String EXTRA_MIME = "org.opendocs.reader.MIME";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private MaterialToolbar toolbar;
    private TabLayout tabs;
    private FrameLayout renderHost;
    private ScrollView textScroll;
    private ScrollView propsScroll;
    private ScrollView notesScroll;
    private TextView textView;
    private TextView counter;
    private TextView notesText;
    private TextView loadingLabel;
    private LinearLayout bottomBar;
    private LinearLayout emptyState;
    private LinearLayout loading;
    private LinearLayout propsContainer;
    private Spinner sheetSpinner;
    private MaterialButton notesToggle;
    private ViewPager2 pager;

    private SheetView sheetView;
    private ParsedDocument document;
    private File localFile;
    private String displayName = "";
    private String mime;
    private String lastQuery = "";
    private int firstMatch = -1;

    private final ViewPager2.OnPageChangeCallback pageCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            onSlideChanged(position);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewer);
        bindViews();
        setupTabs();

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_viewer);
        toolbar.setOnMenuItemClickListener(this::onMenuItem);
        configureSearch(toolbar.getMenu().findItem(R.id.action_search));

        Intent intent = getIntent();
        String extra = intent.getStringExtra(EXTRA_DISPLAY_NAME);
        Uri uri = intent.getData();
        displayName = extra != null && !extra.isEmpty()
                ? extra
                : uri == null ? "" : UriFiles.displayName(this, uri);
        if (displayName == null || displayName.isEmpty()) displayName = getString(R.string.app_name);
        mime = intent.getStringExtra(EXTRA_MIME);
        toolbar.setTitle(displayName);

        if (uri == null) {
            showError(getString(R.string.error_io), null);
            return;
        }
        parse(uri);
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        tabs = findViewById(R.id.tabs);
        renderHost = findViewById(R.id.renderHost);
        textScroll = findViewById(R.id.textScroll);
        propsScroll = findViewById(R.id.propsScroll);
        notesScroll = findViewById(R.id.notesScroll);
        textView = findViewById(R.id.textView);
        counter = findViewById(R.id.counter);
        notesText = findViewById(R.id.notesText);
        loadingLabel = findViewById(R.id.loadingLabel);
        bottomBar = findViewById(R.id.bottomBar);
        emptyState = findViewById(R.id.emptyState);
        loading = findViewById(R.id.loading);
        propsContainer = findViewById(R.id.propsContainer);
        sheetSpinner = findViewById(R.id.sheetSpinner);
        notesToggle = findViewById(R.id.notesToggle);
        loadingLabel.setText(R.string.loading);
    }

    private void setupTabs() {
        tabs.addTab(tabs.newTab().setText(R.string.tab_content));
        tabs.addTab(tabs.newTab().setText(R.string.tab_text));
        tabs.addTab(tabs.newTab().setText(R.string.tab_properties));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showSection(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                showSection(tab.getPosition());
            }
        });
    }

    private void showSection(int index) {
        renderHost.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        textScroll.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        propsScroll.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        boolean emptyContent = document != null && document.isEmptyContent();
        emptyState.setVisibility(emptyContent && index == 0 ? View.VISIBLE : View.GONE);
        if (index == 1 && !lastQuery.isEmpty()) {
            String query = lastQuery;
            lastQuery = "";
            applySearch(query);
        }
    }

    private void parse(final Uri uri) {
        loading.setVisibility(View.VISIBLE);
        worker.execute(() -> {
            try {
                File file = UriFiles.importToCache(this, uri, displayName);
                final ParsedDocument parsed = DocumentParser.parse(file, displayName, mime);
                runOnUiThread(() -> onParsed(parsed, file));
            } catch (final Throwable error) {
                runOnUiThread(() -> showError(describe(error), error));
            }
        });
    }

    private void onParsed(ParsedDocument parsed, File file) {
        if (isFinishing() || isDestroyed()) return;
        document = parsed;
        localFile = file;
        loading.setVisibility(View.GONE);

        Uri uri = getIntent().getData();
        if (uri != null) {
            RecentStore.add(this, new RecentFile(parsed.fileName, uri.toString(), mime,
                    parsed.fileSize, System.currentTimeMillis(), parsed.type));
        }

        toolbar.setTitle(parsed.title());
        textView.setText(parsed.plainText());
        fillProperties();
        buildContent(parsed);

        MenuItem shareFile = toolbar.getMenu().findItem(R.id.action_share_file);
        if (shareFile != null) shareFile.setEnabled(localFile != null && localFile.exists());

        if (tabs.getTabAt(0) != null) tabs.selectTab(tabs.getTabAt(0));
    }

    private void buildContent(ParsedDocument parsed) {
        renderHost.removeAllViews();
        switch (parsed.type) {
            case DOCX: {
                RecyclerView list = new RecyclerView(this);
                list.setLayoutManager(new LinearLayoutManager(this));
                list.setAdapter(new DocxBlockAdapter(parsed.blocks));
                list.setClipToPadding(false);
                list.setPadding(0, 0, 0, dp(24));
                renderHost.addView(list, matchParent(), matchParent());
                break;
            }
            case PPTX: {
                pager = new ViewPager2(this);
                pager.setAdapter(new SlideAdapter(parsed));
                pager.registerOnPageChangeCallback(pageCallback);
                renderHost.addView(pager, matchParent(), matchParent());
                bottomBar.setVisibility(View.VISIBLE);
                notesToggle.setVisibility(View.VISIBLE);
                notesToggle.setOnClickListener(v -> toggleNotes());
                onSlideChanged(0);
                break;
            }
            case XLSX:
            case CSV: {
                if (parsed.sheets.isEmpty()) break;
                sheetView = new SheetView(this);
                sheetView.setSheet(parsed.sheets.get(0));
                renderHost.addView(sheetView, matchParent(), matchParent());
                bottomBar.setVisibility(View.VISIBLE);
                setupSheetSelector(parsed);
                updateSheetCounter(0);
                break;
            }
            default:
                break;
        }
    }

    private void setupSheetSelector(final ParsedDocument parsed) {
        if (parsed.sheets.size() <= 1) {
            sheetSpinner.setVisibility(View.GONE);
            return;
        }
        List<String> names = new ArrayList<>();
        for (Sheet sheet : parsed.sheets) {
            names.add(sheet.name == null || sheet.name.isEmpty()
                    ? getString(R.string.tab_content) : sheet.name);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sheetSpinner.setAdapter(adapter);
        sheetSpinner.setVisibility(View.VISIBLE);
        sheetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (sheetView != null && position < parsed.sheets.size()) {
                    sheetView.setSheet(parsed.sheets.get(position));
                    updateSheetCounter(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void updateSheetCounter(int index) {
        if (document == null || index >= document.sheets.size()) return;
        Sheet sheet = document.sheets.get(index);
        String text = getString(R.string.sheet_counter, index + 1, document.sheets.size());
        if (sheet.truncated) text = text + " \u00b7 " + getString(R.string.truncated_notice);
        counter.setText(text);
    }

    private void onSlideChanged(int position) {
        if (document == null || position >= document.slides.size()) return;
        counter.setText(getString(R.string.slide_counter, position + 1, document.slideCount()));

        String notes = document.slides.get(position).notes;
        boolean hasNotes = notes != null && !notes.trim().isEmpty();
        notesText.setText(hasNotes ? notes : "");
        notesToggle.setEnabled(hasNotes);
        if (!hasNotes) notesScroll.setVisibility(View.GONE);
    }

    private void toggleNotes() {
        notesScroll.setVisibility(notesScroll.getVisibility() == View.VISIBLE
                ? View.GONE : View.VISIBLE);
    }

    private void configureSearch(@Nullable MenuItem item) {
        if (item == null) return;
        View actionView = item.getActionView();
        if (!(actionView instanceof SearchView)) return;
        SearchView searchView = (SearchView) actionView;
        searchView.setQueryHint(getString(R.string.search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                applySearch(query);
                if (tabs.getTabAt(1) != null) tabs.selectTab(tabs.getTabAt(1));
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                applySearch(newText);
                if (newText != null && !newText.trim().isEmpty() && tabs.getTabAt(1) != null) {
                    tabs.selectTab(tabs.getTabAt(1));
                }
                return true;
            }
        });
    }

    private void applySearch(String raw) {
        if (document == null) return;
        String query = raw == null ? "" : raw.trim();
        if (query.equals(lastQuery)) return;
        lastQuery = query;

        String full = document.plainText();
        if (query.isEmpty()) {
            textView.setText(full);
            toolbar.setSubtitle(null);
            firstMatch = -1;
            return;
        }

        SpannableString highlighted = new SpannableString(full);
        String haystack = full.toLowerCase(Locale.getDefault());
        String needle = query.toLowerCase(Locale.getDefault());
        int color = ContextCompat.getColor(this, R.color.search_highlight);

        int from = 0;
        int count = 0;
        firstMatch = -1;
        while (needle.length() > 0) {
            int index = haystack.indexOf(needle, from);
            if (index < 0) break;
            if (firstMatch < 0) firstMatch = index;
            highlighted.setSpan(new BackgroundColorSpan(color), index, index + needle.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            count++;
            from = index + Math.max(1, needle.length());
        }
        textView.setText(highlighted);

        if (count == 0) {
            toolbar.setSubtitle(getString(R.string.search_no_result));
        } else {
            toolbar.setSubtitle(getResources().getQuantityString(R.plurals.search_results, count, count));
            scrollToMatch();
        }
    }

    private void scrollToMatch() {
        final int index = firstMatch;
        if (index < 0) return;
        textView.post(() -> {
            Layout layout = textView.getLayout();
            if (layout == null) return;
            int offset = Math.min(index, textView.getText().length());
            int line = layout.getLineForOffset(offset);
            textScroll.scrollTo(0, Math.max(0, layout.getLineTop(line) - textView.getPaddingTop()));
        });
    }

    private void fillProperties() {
        propsContainer.removeAllViews();
        if (document == null) return;

        addSection(R.string.section_file);
        addRow(R.string.prop_name, document.fileName);
        addRow(R.string.prop_type, Formats.typeLabel(document.type));
        addRow(R.string.prop_size, Formats.size(document.fileSize));
        addRow(R.string.prop_parse_time, Formats.duration(document.parseTimeMs));

        Metadata metadata = document.metadata;
        addSection(R.string.section_metadata);
        boolean any = false;
        any |= addRow(R.string.prop_title, metadata.title);
        any |= addRow(R.string.prop_subject, metadata.subject);
        any |= addRow(R.string.prop_author, metadata.creator);
        any |= addRow(R.string.prop_last_modified_by, metadata.lastModifiedBy);
        any |= addRow(R.string.prop_description, metadata.description);
        any |= addRow(R.string.prop_keywords, metadata.keywords);
        any |= addRow(R.string.prop_created, metadata.created);
        any |= addRow(R.string.prop_modified, metadata.modified);
        any |= addRow(R.string.prop_revision, metadata.revision);
        any |= addRow(R.string.prop_application, metadata.application);
        for (Map.Entry<String, String> entry : metadata.extra.entrySet()) {
            any |= addRow(entry.getKey(), entry.getValue());
        }
        if (!any) addNote(getString(R.string.prop_none));

        if (!metadata.stats.isEmpty()) {
            addSection(R.string.section_stats);
            for (Map.Entry<String, String> entry : metadata.stats.entrySet()) {
                addRow(entry.getKey(), entry.getValue());
            }
        }

        if (!document.warnings.isEmpty()) {
            addSection(R.string.section_warnings);
            for (String warning : document.warnings) {
                addNote(warning);
            }
        }
    }

    private void addSection(int titleRes) {
        TextView view = new TextView(this);
        view.setText(titleRes);
        view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        view.setTextColor(MaterialColors.getColor(propsContainer,
                com.google.android.material.R.attr.colorPrimary));
        view.setPadding(0, dp(20), 0, dp(4));
        propsContainer.addView(view);
    }

    private boolean addRow(int labelRes, @Nullable String value) {
        if (value == null || value.trim().isEmpty()) return false;
        return addRow(getString(labelRes), value);
    }

    private boolean addRow(String label, String value) {
        View view = getLayoutInflater().inflate(R.layout.item_property_row, propsContainer, false);
        ((TextView) view.findViewById(R.id.label)).setText(label);
        ((TextView) view.findViewById(R.id.value)).setText(value);
        propsContainer.addView(view);
        return true;
    }

    private void addNote(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        int padding = dp(4);
        view.setPadding(0, padding, 0, padding);
        propsContainer.addView(view);
    }

    private boolean onMenuItem(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_copy) {
            copyText();
            return true;
        }
        if (id == R.id.action_share_text) {
            if (document != null) Share.text(this, document.title(), document.plainText());
            return true;
        }
        if (id == R.id.action_share_file) {
            if (document != null) {
                Share.file(this, localFile, document.type.mimeType(),
                        getPackageName() + ".fileprovider");
            }
            return true;
        }
        return false;
    }

    private void copyText() {
        if (document == null) return;
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) return;
        manager.setPrimaryClip(ClipData.newPlainText(document.title(), document.plainText()));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void showError(String message, @Nullable Throwable error) {
        if (isFinishing() || isDestroyed()) return;
        loading.setVisibility(View.GONE);
        if (error == null) error = new IOException(message);
        String detail = error.getMessage();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.error_title)
                .setMessage(detail == null || detail.isEmpty() ? message : message + "\n\n" + detail)
                .setPositiveButton(R.string.close, (dialog, which) -> finish());
        final Throwable failure = error;
        builder.setNeutralButton(R.string.error_details, (dialog, which) -> showDetails(failure));
        builder.show();
    }

    private void showDetails(Throwable error) {
        StringBuilder sb = new StringBuilder(error.toString());
        StackTraceElement[] frames = error.getStackTrace();
        for (int i = 0; i < frames.length && i < 12; i++) {
            sb.append('\n').append("    at ").append(frames[i]);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.error_details)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private String describe(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? getString(R.string.error_io) : message;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pager != null) pager.unregisterOnPageChangeCallback(pageCallback);
        worker.shutdownNow();
    }

    private static int matchParent() {
        return ViewGroup.LayoutParams.MATCH_PARENT;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
