package io.github.abdurazaaqmohammed.ui.activities;

import android.content.Intent;
import io.github.abdurazaaqmohammed.ui.EditorMinimizer;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apk.axml.ResourceTableParser;
import com.apk.axml.aXMLDecoder;
import com.apk.axml.aXMLEncoder;
import com.apk.axml.serializableItems.ResEntry;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.io.IOException;
import java.io.InterruptedIOException;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.fragment.UnifiedEditorFragment;
import io.github.abdurazaaqmohammed.domain.editor.EditorDocumentReader;
import io.github.rosemoe.sora.text.Content;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.abdurazaaqmohammed.utils.RootStaging;
import io.github.codehasan.colorpicker.extensions.Extensions;
import modder.hub.dexeditor.views.FastScrollerRecyclerView;

public class TextEditorActivity extends BaseActivity implements UnifiedEditorFragment.EditorCallback, EditorMinimizer.SessionOwner {

    private static class EditorTab {
        String title;
        Uri fileUri;
        File file;
        String pendingSearch;
        boolean pendingSearchRegex;
        boolean pendingSearchMatchCase;
        String rootOriginalPath;
        String zipFilePath;
        String zipEntryPath;
        boolean axml;
        boolean savedAxml;
        List<ResEntry> resEntries;
        String resourceTablePath;
        String pendingDecoded;
        String content;
        String savedContent;
        boolean loaded;
        boolean loading;
        boolean modified;
        boolean loadFailed;
        boolean saving;
        EditorDocumentReader.Mode mode = EditorDocumentReader.Mode.EDIT;
        Charset charset = StandardCharsets.UTF_8;
        byte[] bom = new byte[0];
        int loadGeneration;
        Future<?> loadTask;
        long snapshotVersion = -1;
        long editRevision;
        long saveRevision;

        boolean isPreview() {
            return mode == EditorDocumentReader.Mode.PREVIEW || mode == EditorDocumentReader.Mode.BINARY;
        }
    }

    private static class RetainedSession {
        final List<EditorTab> tabs;
        final int currentIndex;
        final boolean minimized;
        RetainedSession(List<EditorTab> tabs, int currentIndex, boolean minimized) {
            this.tabs = tabs;
            this.currentIndex = currentIndex;
            this.minimized = minimized;
        }
    }

    private DrawerLayout drawerLayout;
    private FastScrollerRecyclerView tabsRecyclerView;
    private TabRowAdapter tabAdapter;
    private ImageButton btnUndo, btnRedo, btnSave, btnEdit, btnFile;

    private Uri currentFileUri;
    private File currentFile;
    private boolean axml;
    private boolean manualFinish;
    private List<ResEntry> resEntries;

    private final List<EditorTab> tabs = new ArrayList<>();
    private int currentIndex = -1;
    private boolean sessionRestored;
    private boolean sessionRestoring;
    private boolean retainSession, restoreForRecreation, sessionClosed;
    private final String sessionId = java.util.UUID.randomUUID().toString();
    private final android.os.Handler dirtyHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable checkDirty = this::checkCurrentDocumentDirty;
    private final List<Intent> pendingIntents = new ArrayList<>();

    private UnifiedEditorFragment editorFragment;
    private EditorTab displayedTab;
    private final ExecutorService documentLoader = Executors.newSingleThreadExecutor(r -> new Thread(r, "hexora-document-load"));
    private final ExecutorService sessionWriter = Executors.newSingleThreadExecutor(r -> new Thread(r, "hexora-editor-session"));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoreForRecreation = savedInstanceState != null;
        retainSession = getSharedPreferences("text_editor_session_state", MODE_PRIVATE).getBoolean("minimized", false);
        getSharedPreferences("text_editor_session_state", MODE_PRIVATE).edit().putString("owner", sessionId).apply();
        setContentView(R.layout.activity_editor);

        initViews();
        setupListeners();
        initEditorFragment();
        Object previous = getLastCustomNonConfigurationInstance();
        if (previous instanceof RetainedSession) {
            RetainedSession retained = (RetainedSession) previous;
            tabs.addAll(retained.tabs);
            retainSession = retained.minimized;
            if (!tabs.isEmpty()) selectTab(Math.max(0, Math.min(retained.currentIndex, tabs.size() - 1)));
            handleIntent(getIntent());
            return;
        }
        pendingIntents.add(getIntent());
        restoreSession();
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        EditorTab tab = getCurrentTab();
        if (tab != null) captureTabContent(tab, getFragment());
        return new RetainedSession(new ArrayList<>(tabs), currentIndex, retainSession);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!EditorMinimizer.ACTION_RESUME.equals(intent.getAction())) {
            if (sessionRestoring) pendingIntents.add(intent);
            else handleIntent(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        EditorMinimizer.onEditorResumed(this);
        if (editorFragment != null) {
            editorFragment.loadBottomBarFunctions();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        UnifiedEditorFragment f = getFragment();
        EditorTab t = getCurrentTab();
        if (t != null) captureTabContent(t, f);
        persistSession();
    }

    private File sessionFile() {
        return new File(getCacheDir(), "text_editor_session.json");
    }

    private void persistSession() {
        if (sessionRestoring || manualFinish) return;
        try {
            UnifiedEditorFragment f = getFragment();
            JSONArray arr = new JSONArray();
            int savedCurrent = 0;
            for (int i = 0; i < tabs.size(); i++) {
                EditorTab t = tabs.get(i);
                if (t.axml) continue;
                JSONObject o = new JSONObject();
                o.put("title", t.title);
                if (t.file != null) o.put("file", t.file.getPath());
                else if (t.fileUri != null) o.put("uri", t.fileUri.toString());
                if (t.rootOriginalPath != null && !t.rootOriginalPath.isEmpty()) {
                    o.put("rootOriginal", t.rootOriginalPath);
                }
                if (t.zipFilePath != null && !t.zipFilePath.isEmpty()) {
                    o.put("zipFile", t.zipFilePath);
                }
                if (t.zipEntryPath != null && !t.zipEntryPath.isEmpty()) {
                    o.put("zipEntry", t.zipEntryPath);
                }
                o.put("modified", t.modified);
                o.put("charset", t.charset.name());
                o.put("bom", android.util.Base64.encodeToString(t.bom, android.util.Base64.NO_WRAP));
                boolean untitledWithText = t.file == null && t.fileUri == null && t.loaded
                        && t.content != null && !t.content.isEmpty();
                if (t.modified || untitledWithText) {
                    String content;
                    captureTabContent(t, f);
                    content = t.content == null ? "" : t.content;
                    o.put("content", content);
                }
                if (i == currentIndex) savedCurrent = arr.length();
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("tabs", arr);
            root.put("current", savedCurrent);
            File file = sessionFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            // JSON escaping/encoding and disk I/O must not block opening or leaving a large tab.
            if (!sessionWriter.isShutdown()) sessionWriter.execute(() -> {
                try {
                    if (!sessionId.equals(getSharedPreferences("text_editor_session_state", MODE_PRIVATE)
                            .getString("owner", ""))) return;
                    io.github.abdurazaaqmohammed.domain.files.SafeFileOperations.writeAtomically(file,
                            root.toString().getBytes(StandardCharsets.UTF_8), (temporary, destination) -> {
                                if (!temporary.renameTo(destination)) throw new IOException("Cannot save editor session");
                            });
                } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }

    private void restoreSession() {
        sessionRestoring = true;
        UnifiedEditorFragment fragment = getFragment();
        if (fragment != null) fragment.showDocumentLoading();
        documentLoader.submit(() -> {
            File file = sessionFile();
            List<EditorTab> restored = new ArrayList<>();
            int restoredIndex = 0;
            String restoreError = null;
            if ((retainSession || restoreForRecreation) && file.exists()) try (InputStream input = FileUtils.getInputStream(file);
                    InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                long limit = Math.max(2 * 1024 * 1024, Math.min(16 * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16));
                if (file.length() > limit) throw new IOException("Saved editor session is too large to restore safely");
                StringBuilder json = new StringBuilder();
                char[] buffer = new char[8192];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) return;
                    if (json.length() + count > limit) throw new IOException("Saved editor session is too large");
                    json.append(buffer, 0, count);
                }
                JSONObject root = new JSONObject(json.toString());
                JSONArray arr = root.optJSONArray("tabs");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        EditorTab t = new EditorTab();
                        t.title = o.optString("title", getString(android.R.string.untitled));
                        String filePath = o.has("file") ? o.getString("file") : null;
                        if (filePath != null) {
                            t.file = new File(filePath);
                            t.fileUri = Uri.fromFile(t.file);
                        } else {
                            String uriStr = o.optString("uri", null);
                            if (uriStr != null) t.fileUri = Uri.parse(uriStr);
                        }
                        String savedRootOriginal = o.optString("rootOriginal", null);
                        if (savedRootOriginal != null && !savedRootOriginal.isEmpty()) {
                            t.rootOriginalPath = savedRootOriginal;
                            if (t.title == null || !t.title.endsWith(" (root)")) {
                                t.title = new File(savedRootOriginal).getName() + " (root)";
                            }
                        }
                        String savedZipFile = o.optString("zipFile", null);
                        if (savedZipFile != null && !savedZipFile.isEmpty()) t.zipFilePath = savedZipFile;
                        String savedZipEntry = o.optString("zipEntry", null);
                        if (savedZipEntry != null && !savedZipEntry.isEmpty()) t.zipEntryPath = savedZipEntry;
                        t.modified = o.optBoolean("modified", false);
                        try {
                            t.charset = Charset.forName(o.optString("charset", "UTF-8"));
                            t.bom = android.util.Base64.decode(o.optString("bom", ""), android.util.Base64.NO_WRAP);
                        } catch (IllegalArgumentException ignored) {
                            t.charset = StandardCharsets.UTF_8;
                            t.bom = new byte[0];
                        }
                        if (o.has("content")) {
                            t.content = o.getString("content");
                            t.loaded = true;
                        } else if (t.file == null && t.fileUri == null) {
                            continue; // nothing restorable for this tab
                        }
                        restored.add(t);
                    }
                    restoredIndex = Math.max(0, Math.min(root.optInt("current", 0), restored.size() - 1));
                }
            } catch (Exception | OutOfMemoryError e) {
                restored.clear();
                // Preserve recovery data instead of repeatedly parsing an oversized/corrupt session.
                File recovery = new File(getCacheDir(), "text_editor_session.recovery-" + System.currentTimeMillis() + ".json");
                boolean preserved = file.renameTo(recovery);
                restoreError = "Could not restore editor tabs. Recovery data retained at "
                        + (preserved ? recovery.getAbsolutePath() : file.getAbsolutePath());
            }
            final int index = restoredIndex;
            final String error = restoreError;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                sessionRestoring = false;
                tabs.addAll(restored);
                sessionRestored = !tabs.isEmpty();
                if (sessionRestored) {
                    currentIndex = index;
                    selectTab(currentIndex);
                }
                List<Intent> requests = new ArrayList<>(pendingIntents);
                pendingIntents.clear();
                for (Intent request : requests) handleIntent(request);
                if (error != null) new ErrorUtil(this).showError(error);
            });
        });
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        findViewById(R.id.action_minimize).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            EditorMinimizer.minimize(this);
        });
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
        btnSave = findViewById(R.id.btn_save);
        btnEdit = findViewById(R.id.btn_edit);
        btnFile = findViewById(R.id.btn_file);

        tabsRecyclerView = findViewById(R.id.tabs_recycler_view);
        tabsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        tabAdapter = new TabRowAdapter();
        tabsRecyclerView.setAdapter(tabAdapter);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationContentDescription(R.string.opened_files);
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
    }

    private void initEditorFragment() {
        FragmentManager fm = getSupportFragmentManager();
        editorFragment = (UnifiedEditorFragment) fm.findFragmentById(R.id.editor_container);
        if (editorFragment == null) {
            // Pass null (not "") for content: a non-null initialContentText makes the fragment's
            // posted init runnable wipe whatever text we set synchronously (e.g. decoded axml)
            editorFragment = UnifiedEditorFragment.newInstance(null, "Editor", null, UnifiedEditorFragment.TYPE_TEXT);
            fm.beginTransaction().replace(R.id.editor_container, editorFragment).commit();
            fm.executePendingTransactions();
        }
        editorFragment.setCallback(this);
    }

    private UnifiedEditorFragment getFragment() {
        if (editorFragment == null) {
            editorFragment = (UnifiedEditorFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.editor_container);
        }
        return editorFragment;
    }

    private EditorTab getCurrentTab() {
        if (currentIndex >= 0 && currentIndex < tabs.size()) return tabs.get(currentIndex);
        return null;
    }

    private void setupListeners() {
        btnUndo.setOnClickListener(v -> {
            UnifiedEditorFragment f = getFragment();
            if (f != null && f.getEditor() != null) {
                if (f.getEditor().canUndo()) f.getEditor().undo();
                updateUndoRedo(f.getEditor().canUndo(), f.getEditor().canRedo());
            }
        });

        btnRedo.setOnClickListener(v -> {
            UnifiedEditorFragment f = getFragment();
            if (f != null && f.getEditor() != null) {
                if (f.getEditor().canRedo()) f.getEditor().redo();
                updateUndoRedo(f.getEditor().canUndo(), f.getEditor().canRedo());
            }
        });

        btnSave.setOnClickListener(v -> saveFile());

        btnEdit.setOnClickListener(v -> {
            UnifiedEditorFragment f = getFragment();
            if (f != null) f.showEditMenu(btnEdit);
        });

        btnFile.setOnClickListener(v -> {
            UnifiedEditorFragment f = getFragment();
            if (f != null) f.showFileMenu(btnFile);
        });
    }

    private class TabRowAdapter extends RecyclerView.Adapter<TabRowAdapter.TabVH> {

        private static class TabVH extends RecyclerView.ViewHolder {
            final TextView title;
            final View close;

            TabVH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tab_title);
                close = itemView.findViewById(R.id.tab_close);
            }
        }

        @NonNull
        @Override
        public TabVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new TabVH(getLayoutInflater().inflate(R.layout.editor_tab_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull TabVH holder, int position) {
            EditorTab tab = tabs.get(position);
            holder.title.setText(getTabLabel(tab));
            holder.title.setTextColor(position == currentIndex
                    ? MaterialColors.getColor(holder.title, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
                    : MaterialColors.getColor(holder.title, com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
            holder.close.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos >= 0) closeTab(pos);
            });
            holder.itemView.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos >= 0) {
                    selectTab(pos);
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
            });
        }

        @Override
        public int getItemCount() {
            return tabs.size();
        }
    }

    private void updateTabsList() {
        if (tabAdapter != null) tabAdapter.notifyDataSetChanged();
    }

    private String getTabLabel(EditorTab tab) {
        return (tab.modified ? "\u25CF " : "") + tab.title;
    }

    private String getSyntaxDocumentKey(EditorTab tab) {
        if (tab.zipEntryPath != null) return "zip:" + tab.zipFilePath + "!" + tab.zipEntryPath;
        if (tab.fileUri != null) return tab.fileUri.toString();
        return tab.file == null ? tab.title : tab.file.getAbsolutePath();
    }

    private void updateTitleBar() {
        EditorTab t = getCurrentTab();
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle(t != null ? t.title : null);
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        UnifiedEditorFragment f = getFragment();

        if (currentIndex == index && (tabs.get(index).loading
                || (tabs.get(index).loaded && displayedTab == tabs.get(index)))) return;
        if (currentIndex >= 0 && currentIndex < tabs.size() && currentIndex != index) {
            EditorTab previous = tabs.get(currentIndex);
            captureTabContent(previous, f);
            cancelLoad(previous);
            // Clean files can be re-read; keeping every large document doubles memory per tab.
            if (!previous.modified && (previous.file != null || previous.fileUri != null)) {
                previous.content = null;
                previous.savedContent = null;
                previous.loaded = false;
            }
        }

        currentIndex = index;
        EditorTab t = tabs.get(index);
        currentFile = t.file;
        currentFileUri = t.fileUri;
        axml = t.axml;
        resEntries = t.resEntries;
        if (f != null) f.setAxml(t.axml);

        queueDocumentLoad(t, t.loaded || (t.modified && t.content != null), true, null);
        updateTitleBar();
        updateTabsList();
        tabsRecyclerView.scrollToPosition(index);
    }

    private void addTabAndOpen(EditorTab tab) {
        tabs.add(tab);
        selectTab(tabs.size() - 1);

        if (!tab.loaded) loadTabContent(tab);
        persistSession();
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        UnifiedEditorFragment f = getFragment();
        if (f == null) return;

        Uri uri = null;
        File file = null;
        boolean isAxml = false;
        List<ResEntry> entries = null;
        String extraText = null;

        if (intent.hasExtra("axml")) {
            isAxml = true;
            if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            }
            if (intent.hasExtra("resEntries")) {
                //noinspection unchecked
                entries = (List<ResEntry>) intent.getSerializableExtra("resEntries");
            }
            file = new File(intent.getStringExtra("path"));
            uri = Uri.fromFile(file);
        } else if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)
                || Intent.ACTION_SEND.equals(action)) {
            uri = intent.getData();
            if (uri == null && intent.hasExtra(Intent.EXTRA_STREAM)) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
            if (uri != null) {
                if ("file".equals(uri.getScheme())) file = new File(uri.getPath());
            } else if (intent.hasExtra("path")) {
                file = new File(intent.getStringExtra("path"));
                uri = Uri.fromFile(file);
            } else if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            }
        } else if (intent.hasExtra("path")) {
            file = new File(intent.getStringExtra("path"));
            uri = Uri.fromFile(file);
        }

        EditorTab existing = findExistingTab(file, uri);
        String earlyRootOriginal = intent.getStringExtra("rootOriginalPath");
        if (existing == null && earlyRootOriginal != null && !earlyRootOriginal.isEmpty()) {
            existing = findExistingRootTab(earlyRootOriginal);
        }
        if (existing != null) {
            if (!existing.modified && file != null && existing.file != null
                    && !file.getAbsolutePath().equals(existing.file.getAbsolutePath())
                    && earlyRootOriginal != null && !earlyRootOriginal.isEmpty()
                    && earlyRootOriginal.equals(existing.rootOriginalPath)) {
                existing.file = file;
                existing.fileUri = uri;
                existing.loaded = false;
                existing.content = null;
                existing.loadFailed = false;
            }
            if (existing.loadFailed) {
                existing.loaded = false;
            }
            String existingZipFile = intent.getStringExtra("zf");
            String existingZipEntry = intent.getStringExtra("zipEntryPath");
            if ((existing.zipFilePath == null || existing.zipFilePath.isEmpty()) && existingZipFile != null && !existingZipFile.isEmpty()) {
                existing.zipFilePath = existingZipFile;
            }
            if ((existing.zipEntryPath == null || existing.zipEntryPath.isEmpty()) && existingZipEntry != null && !existingZipEntry.isEmpty()) {
                existing.zipEntryPath = existingZipEntry;
            }
            int idx = tabs.indexOf(existing);
            selectTab(idx);
            updateTabsList();
            if (intent.hasExtra("search")) {
                existing.pendingSearch = intent.getStringExtra("search");
                existing.pendingSearchRegex = intent.getBooleanExtra("searchRegex", false);
                existing.pendingSearchMatchCase = intent.getBooleanExtra("searchMatchCase", false);
                if (existing.loaded) {
                    UnifiedEditorFragment f2 = getFragment();
                    if (f2 != null) {
                        f2.searchFor(existing.pendingSearch, existing.pendingSearchRegex, existing.pendingSearchMatchCase);
                        existing.pendingSearch = null;
                    }
                }
            }
            return;
        }

        if (file == null && uri == null && extraText == null && !tabs.isEmpty()) {
            // Bare launch with a restored session: keep the previously open tabs
            updateTabsList();
            return;
        }

        EditorTab tab = new EditorTab();
        tab.file = file;
        tab.fileUri = uri;
        tab.axml = isAxml;
        tab.resEntries = entries;
        tab.resourceTablePath = intent.getStringExtra("rssPath");
        String zipFileExtra = intent.getStringExtra("zf");
        String zipEntryExtra = intent.getStringExtra("zipEntryPath");
        if (zipFileExtra != null && !zipFileExtra.isEmpty()) tab.zipFilePath = zipFileExtra;
        if (zipEntryExtra != null && !zipEntryExtra.isEmpty()) tab.zipEntryPath = zipEntryExtra;
        if (intent.hasExtra("search")) {
            tab.pendingSearch = intent.getStringExtra("search");
            tab.pendingSearchRegex = intent.getBooleanExtra("searchRegex", false);
            tab.pendingSearchMatchCase = intent.getBooleanExtra("searchMatchCase", false);
        }
        String rootOriginal = intent.getStringExtra("rootOriginalPath");
        if (rootOriginal != null && !rootOriginal.isEmpty() && file != null
                && !rootOriginal.equals(file.getAbsolutePath())) {
            tab.rootOriginalPath = rootOriginal;
        }
        tab.title = tab.rootOriginalPath != null
                ? new File(tab.rootOriginalPath).getName() + " (root)"
                : resolveTitle(file, uri);
        // Decoded axml / shared text is applied asynchronously via loadTabContent, exactly
        // like regular files — setting editor text synchronously during onCreate gets wiped
        // by the fragment's deferred initialization. If no text arrived with the intent
        // (e.g. zip flow without rssPath decode), readTabText decodes the binary on disk.
        if (extraText != null && !extraText.isEmpty() && (isAxml || (file == null && uri == null))) {
            tab.pendingDecoded = extraText;
        }
        addTabAndOpen(tab);
    }

    private String resolveTitle(File file, Uri uri) {
        if (file != null) return file.getName();
        if (uri != null) {
            String last = uri.getLastPathSegment();
            if (last != null && !last.isEmpty()) {
                int slash = last.lastIndexOf('/');
                return slash >= 0 ? last.substring(slash + 1) : last;
            }
            return uri.toString();
        }
        return getString(android.R.string.untitled);
    }

    private EditorTab findExistingTab(File file, Uri uri) {
        for (EditorTab tab : tabs) {
            if (file != null && file.equals(tab.file)) return tab;
            if (file == null && uri != null && uri.equals(tab.fileUri) && tab.file == null) return tab;
        }
        return null;
    }

    private EditorTab findExistingRootTab(String rootOriginalPath) {
        if (rootOriginalPath == null || rootOriginalPath.isEmpty()) return null;
        for (EditorTab tab : tabs) {
            if (rootOriginalPath.equals(tab.rootOriginalPath)) return tab;
        }
        return null;
    }

    private void loadTabContent(EditorTab tab) {
        if (!tab.loading && !tab.loaded) queueDocumentLoad(tab, false, true, null);
    }

    private void cancelLoad(EditorTab tab) {
        tab.loadGeneration++;
        if (tab.loadTask != null) tab.loadTask.cancel(true);
        tab.loadTask = null;
        tab.loading = false;
    }

    private boolean isCurrentLoad(EditorTab tab, int generation) {
        return !isFinishing() && !isDestroyed() && tabs.contains(tab) && tab.loadGeneration == generation;
    }

    private void queueDocumentLoad(EditorTab tab, boolean useCached, boolean checkRecovery, File recovery) {
        if (isFinishing() || isDestroyed() || documentLoader.isShutdown()) return;
        cancelLoad(tab);
        final int generation = tab.loadGeneration;
        final String cachedText = useCached ? tab.content : null;
        tab.loading = true;
        tab.loaded = false;
        tab.loadFailed = false;
        if (getCurrentTab() == tab) {
            displayedTab = null;
            UnifiedEditorFragment f = getFragment();
            if (f != null) f.showDocumentLoading();
            updateDocumentActions();
        }
        tab.loadTask = documentLoader.submit(() -> {
            try {
                if (tab.rootOriginalPath != null && (tab.file == null || !tab.file.exists())) {
                    File restaged = RootStaging.stageForRead(this, tab.rootOriginalPath);
                    tab.file = restaged;
                    tab.fileUri = Uri.fromFile(restaged);
                }
                if (Thread.currentThread().isInterrupted()) return;
                if (recovery != null) FileUtils.copyFile(recovery, tab.file);
                File recoveryFile = tab.file == null ? null
                        : new File(getCacheDir(), tab.file.getPath().replace(File.separator, "."));
                if (cachedText == null && tab.pendingDecoded == null && checkRecovery && recoveryFile != null
                        && recoveryFile.exists() && recoveryFile.length() != tab.file.length()) {
                    runOnUiThread(() -> {
                        if (!isCurrentLoad(tab, generation)) return;
                        new MaterialAlertDialogBuilder(this).setMessage(R.string.rest_chang)
                                .setTitle(R.string.unsaved_changes_found)
                                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                    if (isCurrentLoad(tab, generation)) queueDocumentLoad(tab, false, false, recoveryFile);
                                })
                                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                                    if (isCurrentLoad(tab, generation)) queueDocumentLoad(tab, false, false, null);
                                })
                                .setOnCancelListener(dialog -> {
                                    if (isCurrentLoad(tab, generation)) queueDocumentLoad(tab, false, false, null);
                                }).show();
                    });
                    return;
                }
                EditorDocumentReader.Result result;
                if (cachedText != null) result = EditorDocumentReader.fromText(cachedText);
                else if (tab.pendingDecoded != null) result = EditorDocumentReader.fromText(tab.pendingDecoded);
                else result = readTabText(tab);
                if (Thread.currentThread().isInterrupted()) return;
                Content prepared = new Content(result.text);
                // Construction normally records the initial insert in the undo stack.
                prepared.setUndoEnabled(false);
                prepared.setUndoEnabled(true);
                if (Thread.currentThread().isInterrupted()) { prepared.release(); return; }
                runOnUiThread(() -> {
                    if (!isCurrentLoad(tab, generation)) { prepared.release(); return; }
                    tab.loading = false;
                    tab.loaded = true;
                    tab.loadFailed = false;
                    tab.mode = result.mode;
                    if (cachedText == null || !tab.modified) {
                        tab.savedContent = result.isReadOnly() ? null : result.text;
                        tab.savedAxml = tab.axml;
                        if (cachedText == null) tab.modified = false;
                    }
                    if (cachedText == null) {
                        tab.charset = result.charset;
                        tab.bom = result.bom;
                    }
                    // Never replace unsaved content with a shortened preview in session recovery.
                    if (!result.isReadOnly()) tab.content = result.text;
                    else if (!tab.modified) tab.content = null;
                    if (!result.isReadOnly() || tab.file != null || tab.fileUri != null) tab.pendingDecoded = null;
                    if (getCurrentTab() != tab) {
                        prepared.release();
                        if (!tab.modified) { tab.content = null; tab.savedContent = null; tab.loaded = false; }
                        return;
                    }
                    resEntries = tab.resEntries;
                    UnifiedEditorFragment f = getFragment();
                    if (f != null) {
                        displayedTab = tab;
                        f.setSyntaxForFilename(tab.title, getSyntaxDocumentKey(tab));
                        int notice = switch (result.mode) {
                            case EDIT -> 0;
                            case LARGE -> R.string.editor_large_mode;
                            case PREVIEW -> R.string.editor_large_preview;
                            case BINARY -> R.string.editor_binary_preview;
                        };
                        f.setPreparedDocument(prepared, result.isLightweight(), result.isReadOnly(), notice,
                                result.isReadOnly() && tab.file != null && tab.zipFilePath == null);
                        tab.snapshotVersion = prepared.getDocumentVersion();
                        if (tab.pendingSearch != null && !tab.pendingSearch.isEmpty()) {
                            f.searchFor(tab.pendingSearch, tab.pendingSearchRegex, tab.pendingSearchMatchCase);
                            tab.pendingSearch = null;
                        }
                    } else prepared.release();
                    updateDocumentActions();
                    updateTabsList();
                });
            } catch (InterruptedIOException ignored) {
                // A tab switch, reload or activity destruction cancelled this generation.
            } catch (Exception | OutOfMemoryError e) {
                runOnUiThread(() -> {
                    if (!isCurrentLoad(tab, generation)) return;
                    tab.loading = false;
                    tab.loadFailed = true;
                    tab.loaded = false;
                    UnifiedEditorFragment f = getFragment();
                    if (getCurrentTab() == tab && f != null) f.showDocumentLoadFailed();
                    updateDocumentActions();
                    new ErrorUtil(this).showError(e instanceof Exception ? (Exception) e
                            : new IOException("Not enough memory to open this document"));
                });
            }
        });
    }

    private EditorDocumentReader.Result readTabText(EditorTab tab) throws IOException {
        try (InputStream is = tab.file != null ? FileUtils.getInputStream(tab.file)
                : tab.fileUri != null ? getContentResolver().openInputStream(tab.fileUri)
                : new java.io.ByteArrayInputStream(new byte[0])) {
            if (tab.axml) {
                if (tab.file != null && tab.file.length() > EditorDocumentReader.MAX_BYTES) {
                    throw new IOException("Compiled XML is too large to decode safely in the text editor");
                }
                if (tab.resEntries == null && tab.resourceTablePath != null) {
                    try (InputStream resources = FileUtils.getInputStream(tab.resourceTablePath)) {
                        tab.resEntries = new ResourceTableParser(resources).parse();
                    }
                }
                return EditorDocumentReader.fromText(new aXMLDecoder(is, tab.resEntries).decodeAsString());
            }
            return EditorDocumentReader.read(is, tab.file == null ? -1 : tab.file.length());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Cannot read document", e);
        }
    }

    private void updateDocumentActions() {
        EditorTab tab = getCurrentTab();
        boolean ready = tab != null && tab.loaded && !tab.loading && !tab.loadFailed && !tab.isPreview();
        boolean canSave = ready && tab.modified && !tab.saving;
        btnSave.setEnabled(canSave);
        btnSave.setAlpha(canSave ? 1f : 0.38f);
        btnEdit.setEnabled(ready);
        UnifiedEditorFragment f = getFragment();
        btnUndo.setEnabled(ready && f != null && f.getEditor() != null && f.getEditor().canUndo());
        btnRedo.setEnabled(ready && f != null && f.getEditor() != null && f.getEditor().canRedo());
    }

    private void captureTabContent(EditorTab tab, UnifiedEditorFragment fragment) {
        if (displayedTab != tab || !tab.loaded || tab.loading || !tab.modified || tab.isPreview()
                || fragment == null || fragment.getEditor() == null) return;
        Content content = fragment.getEditor().getText();
        if (tab.snapshotVersion != content.getDocumentVersion()) {
            tab.content = content.toString();
            tab.snapshotVersion = content.getDocumentVersion();
        }
    }

    private void checkCurrentDocumentDirty() {
        EditorTab tab = getCurrentTab();
        UnifiedEditorFragment fragment = getFragment();
        if (tab == null || tab != displayedTab || !tab.loaded || tab.loading || tab.isPreview()
                || tab.savedContent == null || fragment == null || fragment.getEditor() == null
                || documentLoader.isShutdown()) return;
        Content content = fragment.getEditor().getText();
        String saved = tab.savedContent;
        boolean formatChanged = tab.axml != tab.savedAxml;
        long revision = tab.editRevision;
        documentLoader.submit(() -> {
            // Most keystrokes change length; compare equal-length buffers off the UI thread.
            boolean modified = formatChanged || content.length() != saved.length() || !content.toString().equals(saved);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || getCurrentTab() != tab || displayedTab != tab
                        || tab.editRevision != revision || tab.savedContent != saved) return;
                if (tab.modified != modified) {
                    tab.modified = modified;
                    updateTabsList();
                }
                updateDocumentActions();
            });
        });
    }

    @Override
    public void onEditorMinimized() {
        retainSession = true;
        getSharedPreferences("text_editor_session_state", MODE_PRIVATE).edit().putBoolean("minimized", true).apply();
        persistSession();
    }

    @Override
    public void finish() {
        if (!sessionClosed) {
            sessionClosed = true;
            manualFinish = true;
            retainSession = false;
            getSharedPreferences("text_editor_session_state", MODE_PRIVATE).edit().remove("minimized").apply();
            EditorMinimizer.onEditorClosed(this);
            dirtyHandler.removeCallbacks(checkDirty);
            List<File> recoveries = new ArrayList<>();
            for (EditorTab tab : tabs) if (tab.file != null) {
                recoveries.add(new File(getCacheDir(), tab.file.getPath().replace(File.separator, ".")));
            }
            if (!sessionWriter.isShutdown()) sessionWriter.execute(() -> {
                if (sessionId.equals(getSharedPreferences("text_editor_session_state", MODE_PRIVATE).getString("owner", ""))) {
                    sessionFile().delete();
                    for (File recovery : recoveries) recovery.delete();
                }
            });
        }
        super.finish();
    }

    @Override
    public void onOpenHexRequested() {
        EditorTab tab = getCurrentTab();
        if (tab == null || tab.file == null || tab.zipFilePath != null) return;
        Intent intent = new Intent(this, HexEditorActivity.class).putExtra("path", tab.file.getAbsolutePath());
        if (tab.rootOriginalPath != null) intent.putExtra("rootOriginalPath", tab.rootOriginalPath);
        startActivity(intent);
    }

    private void closeTab(final int position) {
        if (position < 0 || position >= tabs.size()) return;
        final EditorTab tab = tabs.get(position);
        if (!tab.modified) {
            removeTab(position);
            return;
        }
        new MaterialAlertDialogBuilder(this).setTitle(R.string.changes_made)
                .setPositiveButton(R.string.save_and_exit, (dialog, which) -> {
                    if (position == currentIndex) saveFile(() -> removeTabRef(tab)); // editor holds the latest text
                    else saveTabText(tab, tab.content, () -> removeTabRef(tab));       // content was stashed when switching away
                })
                .setNegativeButton(R.string.dont_save, (dialog, which) -> removeTab(position))
                .setNeutralButton(android.R.string.cancel, null)
                .setMessage(getString(R.string.confirm_save, tab.title))
                .show();
    }

    private void saveTabText(EditorTab tab, String text) {
        saveTabText(tab, text, null);
    }

    private void saveTabText(EditorTab tab, String text, Runnable onDone) {
        if (tab.loading || (!tab.loaded && tab.content == null) || tab.loadFailed || tab.isPreview()) {
            Extensions.showMessage(this, getString(R.string.editor_preview_no_save));
            return;
        }
        if (tab.fileUri == null && tab.file == null) {
            Extensions.showMessage(this, getString(R.string.editor_no_file));
            return;
        }
        // Root-staged tab: confirm before touching key system paths, then
        // write the staged copy locally and push bytes back via su.
        if (tab.file != null && tab.rootOriginalPath != null) {
            if (RootStaging.needsWriteConfirm(tab.rootOriginalPath)) {
                String target = tab.rootOriginalPath;
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.editor_write_system))
                        .setMessage(getString(R.string.editor_write_system_msg, target))
                        .setPositiveButton(android.R.string.ok, (d, w) -> saveTabTextRoot(tab, text, onDone))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return;
            }
            saveTabTextRoot(tab, text, onDone);
            return;
        }
        // Plain .xml that looks like Android XML: offer to compile as AXML on save.
        if (!tab.axml && isXmlFile(tab) && FileUtils.looksLikeAxmlText(text)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.compile_axml_title))
                    .setMessage(getString(R.string.compile_axml_msg))
                    .setPositiveButton(getString(R.string.save_axml), (d, w) -> {
                        tab.axml = true;
                        if (getCurrentTab() == tab) {
                            axml = true;
                            UnifiedEditorFragment f = getFragment();
                            if (f != null) f.setAxml(true);
                        }
                        saveTabText(tab, text, onDone);
                    })
                    .setNegativeButton(getString(R.string.save_plain), (d, w) -> writeTabText(tab, text, onDone))
                    .setNeutralButton(android.R.string.cancel, (d, w) -> {
                        if (onDone != null) { /* keep modified flag, don't close */ }
                    })
                    .show();
            return;
        }
        writeTabText(tab, text, onDone);
    }

    private void writeTabText(EditorTab tab, String text, Runnable onDone) {
        if (tab.saving) return;
        tab.saving = true;
        updateDocumentActions();
        final long savedRevision = tab.editRevision;
        // Encode before opening any stream: invalid XML must never truncate the original.
        new Thread(() -> {
            try {
                byte[] bytes = tab.axml ? new aXMLEncoder().encodeString(text, this, tab.resEntries)
                        : EditorDocumentReader.encode(text, tab.charset, tab.bom);
                backupForSave(tab.file, null);
                if (tab.file != null) {
                    io.github.abdurazaaqmohammed.domain.files.SafeFileOperations.writeAtomically(tab.file, bytes, (temporary, destination) -> {
                        // Same-directory POSIX rename replaces atomically, including Android 4.4.
                        if (!temporary.renameTo(destination)) {
                            throw new java.io.IOException("Cannot replace document; original retained: " + destination);
                        }
                    });
                } else {
                    try (OutputStream output = getContentResolver().openOutputStream(tab.fileUri, "wt")) {
                        if (output == null) throw new java.io.IOException("Cannot open document for writing.");
                        output.write(bytes);
                    }
                }
                runOnUiThread(() -> {
                    tab.saving = false;
                    boolean unchanged = tab.editRevision == savedRevision;
                    tab.savedContent = text;
                    tab.savedAxml = tab.axml;
                    if (unchanged) tab.content = text;
                    tab.modified = !unchanged;
                    updateDocumentActions();
                    updateTabsList();
                    persistSession();
                    if (onDone != null && unchanged) onDone.run();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tab.saving = false;
                    tab.modified = true;
                    updateDocumentActions();
                    new ErrorUtil(this).showError(e);
                });
            }
        }, "hexora-editor-save").start();
    }

    private void saveTabTextRoot(EditorTab tab, String text) {
        saveTabTextRoot(tab, text, null);
    }

    private void saveTabTextRoot(EditorTab tab, String text, Runnable onDone) {
        if (tab.saving) return;
        tab.saving = true;
        tab.saveRevision = tab.editRevision;
        updateDocumentActions();
        backupForSave(tab.file, tab.rootOriginalPath);
        try (OutputStream os = FileUtils.getOutputStream(tab.file)) {
            os.write(tab.axml ? new aXMLEncoder().encodeString(text, this, tab.resEntries) : EditorDocumentReader.encode(text, tab.charset, tab.bom));
        } catch (Exception e) {
            tab.saving = false;
            tab.modified = true;
            updateDocumentActions();
            new ErrorUtil(this).showError(e);
            return;
        }
        tab.content = text;
        Extensions.showMessage(this, getString(R.string.editor_writing_root));
        new Thread(() -> {
            try {
                if (text.isEmpty() && originalKnownNonEmpty(tab)) {
                    String target = tab.rootOriginalPath;
                    runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.editor_overwrite_empty))
                            .setMessage(getString(R.string.editor_overwrite_empty_msg, target))
                            .setPositiveButton(getString(R.string.editor_overwrite), (d, w) -> new Thread(() -> doRootWriteBack(tab, text, onDone)).start())
                            .setNegativeButton(android.R.string.cancel, (d, w) -> {
                                tab.saving = false;
                                updateDocumentActions();
                            })
                            .setOnCancelListener(d -> {
                                tab.saving = false;
                                updateDocumentActions();
                            })
                            .show());
                    return;
                }
                doRootWriteBack(tab, text, onDone);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tab.saving = false;
                    updateDocumentActions();
                    new ErrorUtil(this).showError(e);
                });
            }
        }).start();
    }

    private boolean originalKnownNonEmpty(EditorTab tab) {
        try {
            if (tab.rootOriginalPath == null) {
                File f = tab.file;
                return f != null && f.isFile() && f.length() > 0;
            }
            return AccessManager.getSize(this, tab.rootOriginalPath) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void doRootWriteBack(EditorTab tab, String text) {
        doRootWriteBack(tab, text, null);
    }

    private void doRootWriteBack(EditorTab tab, String text, Runnable onDone) {
        try {
            RootStaging.writeBack(this, tab.file, tab.rootOriginalPath);
            runOnUiThread(() -> {
                tab.savedContent = text;
                tab.savedAxml = tab.axml;
                tab.saving = false;
                tab.modified = tab.editRevision != tab.saveRevision;
                updateDocumentActions();
                updateTabsList();
                persistSession();
                Extensions.showMessage(this, getString(R.string.editor_saved_root));
                if (onDone != null && !tab.modified) onDone.run();
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                tab.saving = false;
                updateDocumentActions();
                new ErrorUtil(this).showError(e);
            });
        }
    }

    private void removeTabRef(EditorTab tab) {
        int idx = tabs.indexOf(tab);
        if (idx >= 0) removeTab(idx);
    }

    private void removeTab(int position) {
        if (position < 0 || position >= tabs.size()) return;
        boolean closingCurrent = position == currentIndex;
        cancelLoad(tabs.get(position));
        tabs.remove(position);

        if (tabs.isEmpty()) {
            currentIndex = -1;
            persistSession();
            finish();
            return;
        }

        int next = Math.min(position, tabs.size() - 1);
        if (closingCurrent) {
            selectTab(next);
        } else if (position < currentIndex) {
            currentIndex--;
        }
        updateTitleBar();
        updateTabsList();
        persistSession();
    }

    public void updateUndoRedo(boolean canUndo, boolean canRedo) {
        updateDocumentActions();
    }

    @Override
    public void onContentModified(String className) {
        EditorTab t = getCurrentTab();
        if (t != null && (!t.loaded || t.loading || t.isPreview() || t.loadFailed)) return;
        if (t != null) {
            t.modified = true;
            t.editRevision++;
            updateTabsList();
            updateDocumentActions();
            dirtyHandler.removeCallbacks(checkDirty);
            dirtyHandler.postDelayed(checkDirty, 250);
        }
    }

    @Override
    public void onUndoRedoChanged(boolean canUndo, boolean canRedo) {
        updateDocumentActions();
    }

    @Override
    public boolean isAxmlMode() {
        EditorTab t = getCurrentTab();
        return t != null && t.axml;
    }

    @Override
    public void onToggleAxmlMode() {
        EditorTab t = getCurrentTab();
        if (t == null) return;
        t.axml = !t.axml;
        t.modified = true;
        t.editRevision++;
        UnifiedEditorFragment f = getFragment();
        if (f != null) f.setAxml(t.axml);
        axml = t.axml;
        updateDocumentActions();
        updateTabsList();
        Extensions.showMessage(this, t.axml ? getString(R.string.save_as_axml) : getString(R.string.save_as_plain_xml));
    }

    private boolean isXmlFile(EditorTab tab) {
        try {
            String name = null;
            if (tab.file != null) name = tab.file.getName();
            else if (tab.title != null) name = tab.title;
            if (name == null && currentFile != null) name = currentFile.getName();
            return name != null && name.toLowerCase().endsWith(".xml");
        } catch (Exception e) {
            return false;
        }
    }
    @Override
    public void onSaveRequested() {
        // Mapped from the fragment's "Reload file" item: re-read the file from disk
        EditorTab t = getCurrentTab();
        if (t == null || t.axml) return;
        if (!t.loading) queueDocumentLoad(t, false, false, null);
    }

    @Override
    public void onCloseRequested() {
        requestEditorExit();
    }

    private void requestEditorExit() {
        EditorTab current = getCurrentTab();
        if (current != null) captureTabContent(current, getFragment());
        List<EditorTab> modified = new ArrayList<>();
        for (EditorTab tab : tabs) {
            if (tab.modified && tab.axml == tab.savedAxml && tab.savedContent != null
                    && tab.content != null && tab.content.equals(tab.savedContent)) {
                tab.modified = false;
            }
            if (tab.modified) modified.add(tab);
        }
        if (modified.isEmpty()) { finish(); return; }
        String[] labels = new String[modified.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = modified.get(i).title;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.unsaved_changes)
                .setMessage(android.text.TextUtils.join("\n", labels))
                .setPositiveButton(R.string.save_and_exit, (dialog, which) -> saveTabsBeforeExit(modified, 0))
                .setNegativeButton(R.string.dont_save, (dialog, which) -> finish())
                .setNeutralButton(android.R.string.cancel, null).show();
    }

    private void saveTabsBeforeExit(List<EditorTab> modified, int index) {
        if (isFinishing() || isDestroyed()) return;
        if (index == modified.size()) {
            EditorTab tab = getCurrentTab();
            if (tab != null && (tab.fileUri != null || tab.file != null)) {
                Uri uri = tab.fileUri != null ? tab.fileUri : Uri.fromFile(tab.file);
                Intent result = new Intent().setData(uri).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (tab.zipFilePath != null) result.putExtra("zipFilePath", tab.zipFilePath);
                if (tab.zipEntryPath != null) result.putExtra("zipEntryPath", tab.zipEntryPath);
                setResult(757, result);
            }
            finish();
            return;
        }
        EditorTab tab = modified.get(index);
        captureTabContent(tab, getFragment());
        saveTabText(tab, tab.content == null ? "" : tab.content, () -> saveTabsBeforeExit(modified, index + 1));
    }

    @Override
    public void onPreferencesRequested() {
        startActivity(new Intent(this, EditorSettingsActivity.class));
    }

    @Override
    protected void onDestroy() {
        EditorTab tab = getCurrentTab();
        if (!manualFinish && tab != null && tab.modified && !tab.isPreview()
                && tab.file != null && tab.content != null) {
            String snapshot = tab.content;
            Charset charset = tab.charset;
            byte[] bom = tab.bom;
            File recovery = new File(getCacheDir(), tab.file.getPath().replace(File.separator, "."));
            sessionWriter.execute(() -> {
                try (OutputStream output = FileUtils.getOutputStream(recovery)) {
                    output.write(EditorDocumentReader.encode(snapshot, charset, bom));
                } catch (Exception ignored) { }
            });
        }
        dirtyHandler.removeCallbacks(checkDirty);
        for (EditorTab t : tabs) cancelLoad(t);
        documentLoader.shutdownNow();
        sessionWriter.shutdown(); // Drain the last session/recovery write; Fragment owns editor.release().
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        requestEditorExit();
    }

    private void backupForSave(File directFile, String rootOriginal) {
        try {
            if (!UiPrefs.genBackup(this)) return;
            if (rootOriginal != null) {
                if (AccessManager.fileOpsOn(this) && AccessManager.exists(this, rootOriginal)) {
                    AccessManager.copyFile(this, rootOriginal, rootOriginal + ".bak", true);
                }
            } else if (directFile != null && directFile.isFile()) {
                File bak = new File(directFile.getPath() + ".bak");
                FileUtils.copyFile(directFile, bak);
            }
        } catch (Exception ignored) {
        }
    }

    private void saveFile() {
        UnifiedEditorFragment f = getFragment();
        if (f == null || f.getEditor() == null) return;
        EditorTab tab = getCurrentTab();
        if (tab == null) return;
        saveFile(null);
    }

    private void saveFile(Runnable onDone) {
        UnifiedEditorFragment f = getFragment();
        if (f == null || f.getEditor() == null) {
            if (onDone != null) onDone.run();
            return;
        }
        EditorTab tab = getCurrentTab();
        if (tab == null) {
            if (onDone != null) onDone.run();
            return;
        }
        if (tab.loading || !tab.loaded || tab.loadFailed || tab.isPreview()) {
            Extensions.showMessage(this, getString(R.string.editor_preview_no_save));
            return;
        }
        captureTabContent(tab, f);
        saveTabText(tab, tab.content == null ? f.getCode() : tab.content, onDone);
        updateTabsList();
        persistSession();
    }
}
