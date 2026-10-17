package io.github.abdurazaaqmohammed.features.files;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputLayout;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;
import io.github.abdurazaaqmohammed.core.ui.util.PopupMenus;
import io.github.abdurazaaqmohammed.data.prefs.ListPrefs;
import io.github.abdurazaaqmohammed.domain.files.FileSorting;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.ui.views.SortDirectionToggle;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Listing sort/filter/hidden-files behaviour extracted from MainActivity.
 * Pure sort math lives in domain.files.FileSorting, prefs in
 * data.prefs.ListPrefs; this class owns the dialogs and pane rebinding.
 */
public class SortFilterController {

    private final MainActivity activity;
    private final ListPrefs prefs;
    private String currentPane1Filter = "";
    private String currentPane2Filter = "";
    private final boolean[] filterVisible = new boolean[2];
    private TextInputLayout filterBox;
    private EditText filterBar;
    private View pathLayout;
    private boolean syncingFilterUi;
    private final android.os.Handler filterHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable[] pendingFilters = new Runnable[2];
    private final java.util.concurrent.ThreadPoolExecutor filterWorker = new java.util.concurrent.ThreadPoolExecutor(
            2, 2, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<>());
    private final java.util.concurrent.Future<?>[] filterJobs = new java.util.concurrent.Future<?>[2];
    private final int[] filterRequests = new int[2];

    public void close() {
        filterHandler.removeCallbacksAndMessages(null);
        filterWorker.shutdownNow();
    }

    public SortFilterController(MainActivity activity) {
        this.activity = activity;
        this.prefs = new ListPrefs(activity);
    }

    public void setupFilterBar() {
        LinearLayout topBar = activity.findViewById(R.id.topBar);
        filterBox =
                UiFields.box(activity, "Filter...");
        filterBar = UiFields.field(filterBox, 0);
        pathLayout = topBar.getChildAt(1);
        filterBox.setVisibility(View.GONE);
        filterBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        filterBar.setSingleLine(true);
        topBar.addView(filterBox, 2);

        filterBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (syncingFilterUi) return;
                if (activity.lastPaneSelected == 1) currentPane1Filter = s.toString().toLowerCase(Locale.ROOT);
                else currentPane2Filter = s.toString().toLowerCase(Locale.ROOT);
                boolean pane1 = activity.lastPaneSelected == 1;
                int index = pane1 ? 0 : 1;
                filterRequests[index]++;
                if (filterJobs[index] != null) filterJobs[index].cancel(true);
                if (pendingFilters[index] != null) filterHandler.removeCallbacks(pendingFilters[index]);
                pendingFilters[index] = () -> applyFilterToPane(pane1);
                filterHandler.postDelayed(pendingFilters[index], 150);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    public void toggleFilterBar() {
        boolean pane1 = activity.lastPaneSelected == 1;
        int index = pane1 ? 0 : 1;
        filterVisible[index] = !filterVisible[index];
        if (!filterVisible[index]) {
            if (pane1) currentPane1Filter = ""; else currentPane2Filter = "";
            applyFilterToCurrentPane();
        }
        onPaneSelected();
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard == null) return;
        if (filterVisible[index]) {
            filterBar.requestFocus();
            keyboard.showSoftInput(filterBar, InputMethodManager.SHOW_IMPLICIT);
        } else keyboard.hideSoftInputFromWindow(filterBar.getWindowToken(), 0);
    }

    public void onPaneSelected() {
        if (filterBar == null) return;
        boolean pane1 = activity.lastPaneSelected == 1;
        syncingFilterUi = true;
        filterBar.setText(pane1 ? currentPane1Filter : currentPane2Filter);
        filterBar.setSelection(filterBar.length());
        syncingFilterUi = false;
        boolean visible = filterVisible[pane1 ? 0 : 1];
        filterBox.setVisibility(visible ? View.VISIBLE : View.GONE);
        pathLayout.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    public File[] filterFiles(File[] files, boolean pane1) {
        String filter = pane1 ? currentPane1Filter : currentPane2Filter;
        if (filter.isEmpty()) return files;
        List<File> visible = new ArrayList<>();
        for (File file : files) if (file.getName().toLowerCase(Locale.ROOT).contains(filter)) visible.add(file);
        return visible.toArray(new File[0]);
    }

    public List<ZipEntryInfo> filterZipEntries(List<ZipEntryInfo> entries, boolean pane1) {
        String filter = pane1 ? currentPane1Filter : currentPane2Filter;
        if (filter.isEmpty()) return entries;
        List<ZipEntryInfo> visible = new ArrayList<>();
        for (ZipEntryInfo entry : entries) {
            if (entry.getName().equals("..") || entry.getName().toLowerCase(Locale.ROOT).contains(filter)) visible.add(entry);
        }
        return visible;
    }

    public void applyFilterToCurrentPane() {
        boolean pane1 = activity.lastPaneSelected == 1;
        int index = pane1 ? 0 : 1;
        if (pendingFilters[index] != null) filterHandler.removeCallbacks(pendingFilters[index]);
        applyFilterToPane(pane1);
    }

    private void applyFilterToPane(boolean isPane1) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        RecyclerView pane = activity.findViewById(isPane1 ? R.id.listViewPane1 : R.id.listViewPane2);
        RecyclerView.Adapter a = pane.getAdapter();
        if (!(a instanceof MainFilesArrayAdapter)) return;
        MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) a;
        int index = isPane1 ? 0 : 1;
        int request = ++filterRequests[index];
        if (filterJobs[index] != null) filterJobs[index].cancel(true);
        filterWorker.purge();
        String query = isPane1 ? currentPane1Filter : currentPane2Filter;
        File folder = isPane1 ? activity.pane1Folder : activity.pane2Folder;
        if (adapter.isInZip) {
            List<ZipEntryInfo> entries = activity.paneZipEntries(isPane1);
            if (entries == null) return;
            filterJobs[index] = filterWorker.submit(() -> {
            List<ZipEntryInfo> filtered = new ArrayList<>();
            for (ZipEntryInfo entry : entries) {
                if (Thread.currentThread().isInterrupted()) return;
                if (entry.getName().equals("..") || entry.getName().toLowerCase(Locale.ROOT).contains(query)) filtered.add(entry);
            }
            filterHandler.post(() -> {
            if (!canPublishFilter(pane, adapter, index, request)) return;
            activity.replacePaneListing(isPane1, "zip:" + folder.getAbsolutePath()
                    + "!" + adapter.currentZipPath, new MainFilesArrayAdapter(activity, filtered.toArray(new ZipEntryInfo[0]), null, isPane1, true,
                    adapter.currentZipPath));
            if (activity.lastPaneSelected == (isPane1 ? 1 : 2)) activity.setCurrentFolder(activity.archiveDisplayPath(folder)
                    + "!" + adapter.currentZipPath, filtered);
            activity.findViewById(isPane1 ? R.id.pane1Empty : R.id.pane2Empty).setVisibility(filtered.size() <= 1 ? View.VISIBLE : View.GONE);
            });
            });
        } else {
            File[] files = activity.paneFiles(isPane1);
            if (files == null) return;
            filterJobs[index] = filterWorker.submit(() -> {
            List<File> matches = new ArrayList<>();
            for (File file : files) {
                if (Thread.currentThread().isInterrupted()) return;
                if (file.getName().toLowerCase(Locale.ROOT).contains(query)) matches.add(file);
            }
            File[] filtered = matches.toArray(new File[0]);
            filterHandler.post(() -> {
            if (!canPublishFilter(pane, adapter, index, request)) return;
            activity.replacePaneListing(isPane1, "file:" + folder.getAbsolutePath(), new MainFilesArrayAdapter(activity, filtered,
                    folder.getParentFile() != null ? folder.getParentFile() : folder, isPane1, false, null));
            if (activity.lastPaneSelected == (isPane1 ? 1 : 2)) activity.setCurrentFolder(folder, filtered);
            activity.findViewById(isPane1 ? R.id.pane1Empty : R.id.pane2Empty).setVisibility(filtered.length == 0 ? View.VISIBLE : View.GONE);
            });
            });
        }
    }

    private boolean canPublishFilter(RecyclerView pane, MainFilesArrayAdapter source, int index, int request) {
        return !activity.isFinishing() && !activity.isDestroyed()
                && filterRequests[index] == request && pane.getAdapter() == source;
    }

    public boolean isNotHidden(File f) {
        return (prefs.showSystemHidden() || (!f.isHidden() && !f.getName().startsWith(".")))
                && (prefs.showManualHidden() || !prefs.manuallyHidden().contains(f.getPath()));
    }

    public boolean isNotHidden(ZipEntryInfo e) {
        return (prefs.showSystemHidden() || !e.getName().startsWith("."))
                && (prefs.showManualHidden() || !prefs.manuallyHidden().contains(e.getFullPath()));
    }

    public void showSortDialog() {
        boolean isPane1 = activity.lastPaneSelected == 1;
        RecyclerView.Adapter a = activity.getCurrentPane().getAdapter();
        if (!(a instanceof MainFilesArrayAdapter)) return;
        MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) activity.getCurrentPane().getAdapter();
        String currentPath = adapter.isInZip ? (isPane1 ? activity.pane1Folder : activity.pane2Folder).getPath() + "!" + adapter.currentZipPath
                : (isPane1 ? activity.pane1Folder.getPath() : activity.pane2Folder.getPath());

        int sortBy = prefs.sortBy(currentPath);
        boolean reverse = prefs.sortReverse(currentPath);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        String[] sortOptions = {activity.rss.getString(R.string.name), activity.rss.getString(R.string.size),
                activity.rss.getString(R.string.sort_date), activity.rss.getString(R.string.type)};
        RadioGroup radioGroup = new RadioGroup(activity);
        for (int i = 0; i < sortOptions.length; i++) {
            RadioButton rb = new RadioButton(activity);
            rb.setText(sortOptions[i]);
            rb.setId(i);
            radioGroup.addView(rb);
        }
        radioGroup.check(sortBy);
        layout.addView(radioGroup);

        CheckBox cbOnlyThisFolder = new CheckBox(activity);
        cbOnlyThisFolder.setText(activity.rss.getString(R.string.only_for_this_folder));
        layout.addView(cbOnlyThisFolder);

        SortDirectionToggle directionToggle = new SortDirectionToggle(activity);
        directionToggle.setDescending(reverse);
        layout.addView(directionToggle);

        activity.dialogUtil.getDialogBuilder()
                .setTitle(activity.getString(R.string.sort))
                .setView(layout)
                .setPositiveButton(activity.getString(R.string.apply), (dialog, which) -> {
                    int selectedSort = radioGroup.getCheckedRadioButtonId();
                    boolean selectedReverse = directionToggle.isDescending();
                    prefs.saveSort(cbOnlyThisFolder.isChecked() ? currentPath : null,
                            selectedSort, selectedReverse);
                    activity.reloadCurrentFolder();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void showEditHiddenFilesDialog() {
        List<String> hiddenList = new ArrayList<>(prefs.manuallyHidden());

        ListView listView = new ListView(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, hiddenList);
        listView.setAdapter(adapter);

        AlertDialog dialog = activity.dialogUtil.getDialogBuilder()
                .setTitle(R.string.edit_hidden_files)
                .setView(listView)
                .setPositiveButton(R.string.done, (d, w) -> activity.reloadCurrentFolder())
                .create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String path = hiddenList.get(position);
            hiddenList.remove(position);
            prefs.unhide(path);
            adapter.notifyDataSetChanged();
            Extensions.showMessage(activity, activity.getString(R.string.unhidden, path));
        });

        dialog.show();
    }

    public void sortFiles(File[] files, String folderPath) {
        FileSorting.sortFiles(files, prefs.sortBy(folderPath), prefs.sortReverse(folderPath));
    }

    public void sortZipEntries(List<ZipEntryInfo> entries, String folderPath) {
        FileSorting.sortZipEntries(entries, folderPath, prefs.sortBy(folderPath), prefs.sortReverse(folderPath));
    }

    public static void forceShowIcons(PopupMenu popupMenu) {
        PopupMenus.forceShowIcons(popupMenu);
    }
}
