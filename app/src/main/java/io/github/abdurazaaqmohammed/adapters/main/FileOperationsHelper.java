package io.github.abdurazaaqmohammed.adapters.main;

import io.github.abdurazaaqmohammed.utils.ZipArchiveCache;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.reandroid.apkeditor.Util;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.abdurazaaqmohammed.domain.files.SafeFileOperations;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.features.dex.DexTools;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFileOps;
import io.github.abdurazaaqmohammed.adapters.FtpFilesArrayAdapter;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.RootStaging;
import io.github.abdurazaaqmohammed.utils.SignWrapper;

public class FileOperationsHelper {

    public static final int UPDATE_MODE_REPLACE_ALL = 0;
    public static final int UPDATE_MODE_UPDATE_AND_REPLACE = 1;
    public static final int UPDATE_MODE_SKIP_ALL = 2;

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final MainFilesArrayAdapter adapter;
    private final DexTools dex;

    private volatile ProgressManager activeProgress;
    private volatile Thread operationWorker;
    private final AtomicBoolean running = new AtomicBoolean();
    private boolean moving;
    private long completedWork, totalWork;
    private boolean knownWork;
    private boolean batchingPaste, deferredMoveSizing;
    private final Map<Object, Long> copyWork = new LinkedHashMap<>();
    private final Map<File, Long> deleteWork = new LinkedHashMap<>();

    private long cachedCopyWork(Object item) throws IOException {
        Long work = copyWork.get(item);
        if (work == null) { work = itemCopyWork(item); copyWork.put(item, work); }
        return work;
    }

    private long cachedDeleteWork(File file) {
        Long work = deleteWork.get(file);
        if (work == null) { work = itemDeleteWork(file); deleteWork.put(file, work); }
        return work;
    }

    private long itemCopyWork(Object item) throws IOException {
        if (item instanceof File file) {
            try { return io.github.abdurazaaqmohammed.domain.files.FileWorkProgress.copyUnits(file); }
            catch (IOException e) { knownWork = false; return 1; }
        }
        ZipEntryInfo entry = (ZipEntryInfo) item;
        if (!entry.isDirectory()) return Math.max(0, entry.getSize()) + 1;
        long work = 1;
        String prefix = entry.getFullPath().replaceAll("/+$", "") + "/";
        try (ZipFile zip = io.github.abdurazaaqmohammed.domain.files.ArchivePasswords.open(entry.getZipFile())) {
            for (FileHeader header : zip.getFileHeaders()) {
                if (header.getFileName().startsWith(prefix) && !header.getFileName().equals(prefix))
                    work = Math.addExact(work, header.isDirectory() ? 1 : Math.max(0, header.getUncompressedSize()) + 1);
            }
        }
        return work;
    }

    private void advanceWork(long units) {
        completedWork += units;
        ProgressManager progress = activeProgress;
        if (progress != null && knownWork && totalWork > 0) progress.setProgress(Math.min(completedWork, Math.max(0, totalWork - 1)), totalWork);
    }

    private void finishItemWork(long target) { advanceWork(Math.max(0, target - completedWork)); }

    private long itemDeleteWork(File file) {
        try { return io.github.abdurazaaqmohammed.domain.files.FileWorkProgress.deleteUnits(file); }
        catch (IOException e) { knownWork = false; return 1; }
    }

    private interface IoOperation {
        void run() throws Exception;
    }

    private void checkOperationCancelled() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Operation cancelled");
        ProgressManager progress = activeProgress;
        if (progress != null && running.get()) progress.checkCancelled();
    }

    public FileOperationsHelper(MainActivity context, DialogUtil dialogUtil, MainFilesArrayAdapter adapter) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.adapter = adapter;
        this.dex = new DexTools(context, dialogUtil, adapter.pane1, new DexTools.OpenWith() {
            @Override
            public void open(File file, String fileName) {
                adapter.openWithForFile(file, fileName);
            }

            @Override
            public void open(File file, String fileName, File zipFile, String zipEntryPath) {
                adapter.openAutomatically(file, fileName, zipFile, zipEntryPath);
            }
        });
    }

    public void showDexOptionsDialog(File dexFile, File zipFile, String entryPath, String displayName) {
        dex.showDexOptionsDialog(dexFile, zipFile, entryPath, displayName);
    }

    public void openDexEditor(File file, File zip, String entry) {
        dex.openDexEditor(file, zip, entry);
    }

    public void handleZipEntryClick(ZipEntryInfo zipEntry) {
        dex.handleZipEntryClick(zipEntry);
    }

    private void showActiveProgress(String text) {
        dismissActiveProgress();
        ProgressManager pm = new ProgressManager(context, true);
        pm.setText(text);
        if (knownWork && totalWork > 0) pm.setProgress(Math.min(completedWork, totalWork - 1), totalWork);
        activeProgress = pm;
        Thread worker = operationWorker;
        if (worker != null) pm.onCancel(worker::interrupt);
        pm.show();
    }

    private void dismissActiveProgress() {
        ProgressManager pm = activeProgress;
        if (pm != null) pm.dismiss();
    }

    private void runWithProgress(String text, List<Object> items, boolean move, IoOperation op) {
        runWithProgress(text,items,move,op,() -> { });
    }
    private boolean runWithProgress(String text,List<Object> items,boolean move,IoOperation op,Runnable finished) {
        if (!running.compareAndSet(false, true)) return false;
        completedWork = totalWork = 0;
        copyWork.clear();
        deleteWork.clear();
        knownWork = false;
        showActiveProgress(text);
        Thread worker = new Thread(() -> {
            try {
                activeProgress.checkCancelled();
                completedWork = 0;
                totalWork = 0;
                knownWork = true;
                activeProgress.setText(context.getString(R.string.progress_counting));
                RecyclerView destinationView = context.findViewById(adapter.pane1 ? R.id.listViewPane2 : R.id.listViewPane1);
                boolean archiveDestination = destinationView.getAdapter() instanceof MainFilesArrayAdapter
                        && ((MainFilesArrayAdapter) destinationView.getAdapter()).isInZip;
                // A same-volume move is a rename, even for a folder with millions of files.
                // Only count its contents if rename fails and copying is actually needed.
                deferredMoveSizing = move && !archiveDestination && !adapter.isInZip
                        && items.stream().allMatch(item -> item instanceof File);
                for (Object item : items) {
                    activeProgress.checkCancelled();
                    long copy = deferredMoveSizing ? 1 : cachedCopyWork(item);
                    if (archiveDestination && item instanceof ZipEntryInfo) copy *= move ? 3 : 2;
                    if (move && !deferredMoveSizing && item instanceof File file) copy = Math.addExact(copy * 2,
                            cachedDeleteWork(file));
                    totalWork = Math.addExact(totalWork, copy);
                }
                if (move && adapter.isInZip && !items.isEmpty()) totalWork += Math.max(1, ((ZipEntryInfo) items.get(0)).getZipFile().length());
                activeProgress.setText(text);
                if (knownWork) activeProgress.setProgress(0L, Math.max(1, totalWork));
                try (io.github.abdurazaaqmohammed.domain.files.FileWorkProgress.Scope scope =
                             io.github.abdurazaaqmohammed.domain.files.FileWorkProgress.track(this::advanceWork)) {
                    activeProgress.checkCancelled();
                    op.run();
                }
                activeProgress.setProgress(Math.max(1, totalWork), Math.max(1, totalWork));
            } catch (Exception e) {
                activeProgress.reportError(e);
            } finally {
                dismissActiveProgress();
                copyWork.clear();
                deleteWork.clear();
                moving = false;
                operationWorker = null;
                running.set(false);
                context.handler.post(context::refreshAllPanes);
                context.handler.post(finished);
            }
        }, "hexora-transfer");
        operationWorker = worker;
        activeProgress.onCancel(worker::interrupt);
        worker.start();
        return true;
    }

    public boolean pasteFilesAsync(List<File> files,boolean cut,File destination,java.util.function.Consumer<List<File>> finished) {
        List<Object> snapshot=new ArrayList<>(files);
        List<File> completed=new ArrayList<>();
        return runWithProgress(context.getString(cut ? R.string.moving : R.string.copying,summarizeItems(snapshot)),snapshot,cut,() -> {
            // Validate all destinations before a cut can remove any source.
            for (File source:files) {
                checkOperationCancelled();
                if (!source.exists() && !(AccessManager.fileOpsOn(context) && AccessManager.exists(context,source.getAbsolutePath())))
                    throw new IOException("Clipboard source no longer exists: "+source);
                if (!isSameDirectory(source,destination)) SafeFileOperations.validateDestination(source,new File(destination,source.getName()));
            }
            batchingPaste = true;
            try {
                for (File source:files) {
                    checkOperationCancelled();
                    List<Object> item=Collections.singletonList(source);
                    if (cut) moveToDestination(item,destination,adapter,true);
                    else copyToRegularFolder(item,destination);
                    completed.add(source);
                }
            } finally { batchingPaste = false; }
        },() -> { context.refreshAllPanes(); finished.accept(new ArrayList<>(completed)); });
    }

    public void copyItemsAsync(List<Object> items) {
        List<Object> snapshot = new ArrayList<>(items);
        runWithProgress(context.rss.getString(R.string.copying, summarizeItems(snapshot)), snapshot, false, () -> copyMultiple(snapshot));
    }

    public void copyAsync(Object item) {
        if (adapter.isMultiSelectMode() && !adapter.getSelectedFiles().isEmpty()) copyItemsAsync(adapter.getSelectedFiles());
        else copyItemsAsync(Collections.singletonList(item));
    }

    public void moveAsync(Object item) {
        List<Object> snapshot = adapter.isMultiSelectMode() && !adapter.getSelectedFiles().isEmpty()
                ? new ArrayList<>(adapter.getSelectedFiles()) : Collections.singletonList(item);
        runWithProgress(context.rss.getString(R.string.moving, summarizeItems(snapshot)), snapshot, true, () -> moveItems(snapshot));
    }

    public void copy(Object item) throws IOException {
        if (adapter.isMultiSelectMode() && !adapter.getSelectedFiles().isEmpty()) {
            copyMultiple(adapter.getSelectedFiles());
        } else {
            copyMultiple(Collections.singletonList(item));
        }
    }

    public void copyMultiple(List<Object> items) throws IOException {
        if (adapter.isInZip) {
            copyFromZip(items);
        } else {
            copyToDestination(items);
        }
    }

    public void move(Object item) throws IOException {
        moveItems(adapter.isMultiSelectMode() && !adapter.getSelectedFiles().isEmpty()
                ? new ArrayList<>(adapter.getSelectedFiles()) : Collections.singletonList(item));
    }

    private void moveItems(List<Object> itemsToMove) throws IOException {
        moving = true;
        if (adapter.isInZip) {
            if (!copyFromZip(itemsToMove)) return;
            deleteZipEntry(itemsToMove.toArray(new ZipEntryInfo[0]));
            context.handler.post(adapter::clearSelection);
            context.handler.post(context::refreshAllPanes);
        } else {
            moveToDestination(itemsToMove);
        }
    }

    private boolean moveToDestination(List<Object> items) throws IOException {
        File destinationFolder = adapter.pane1 ? context.pane2Folder : context.pane1Folder;
        RecyclerView.Adapter rvAdapter = ((RecyclerView) context.findViewById(adapter.pane1 ? R.id.listViewPane2 : R.id.listViewPane1)).getAdapter();
        return moveToDestination(items,destinationFolder,rvAdapter,false);
    }

    private boolean moveToDestination(List<Object> items,File destinationFolder,RecyclerView.Adapter rvAdapter,boolean paste) throws IOException {
        if (rvAdapter instanceof FtpFilesArrayAdapter) {
            ((FtpFilesArrayAdapter) rvAdapter).uploadFiles(items, true);
            return true;
        }
        MainFilesArrayAdapter otherPaneAdapter = (MainFilesArrayAdapter) rvAdapter;
        boolean destIsZip = otherPaneAdapter != null && otherPaneAdapter.isInZip;
        if (destIsZip) {
            if (!copyToZip(items, destinationFolder, otherPaneAdapter.currentZipPath)) return false;
            for (Object item : items) {
                checkOperationCancelled();
                if (item instanceof File) SafeFileOperations.delete((File) item);
                else if (item instanceof ZipEntryInfo) deleteZipEntry((ZipEntryInfo) item);
            }
            context.handler.post(adapter::clearSelection);
            context.handler.post(context::refreshAllPanes);
            return true;
        }

        boolean useElevated = AccessManager.fileOpsOn(context);

        for (Object item : items) {
            checkOperationCancelled();
            if (item instanceof File f) {
                long targetWork = completedWork + (deferredMoveSizing ? 1
                        : cachedCopyWork(f) * 2 + cachedDeleteWork(f));
                if (isSameDirectory(f, destinationFolder)) { finishItemWork(targetWork); continue; }
                if (io.github.abdurazaaqmohammed.utils.LocalStorage.isPath(f)
                        || io.github.abdurazaaqmohammed.utils.LocalStorage.isPath(destinationFolder)) {
                    if (deferredMoveSizing) {
                        long work = cachedCopyWork(f) * 2 + cachedDeleteWork(f);
                        totalWork = Math.addExact(totalWork, work - 1);
                        targetWork = completedWork + work;
                    }
                    io.github.abdurazaaqmohammed.utils.LocalStorage.transfer(f, destinationFolder, true);
                    finishItemWork(targetWork);
                    continue;
                }
                File dest = getUnusedDest(destinationFolder, f.getName(), useElevated);
                SafeFileOperations.validateDestination(f, dest);
                if (f.renameTo(dest)) { finishItemWork(targetWork); continue; }
                if (useElevated) {
                    try {
                        AccessManager.rename(context, f.getAbsolutePath(), dest.getAbsolutePath(), true);
                        finishItemWork(targetWork);
                        continue;
                    } catch (IOException ignored) { }
                }
                if (ShizukuFileOps.involvesShizukuPath(f, destinationFolder) && ShizukuFileOps.shellMove(f, destinationFolder, dest.getName())) {
                    finishItemWork(targetWork);
                    continue;
                }
                if (useElevated) {
                    try {
                        if (f.isDirectory()) AccessManager.copyDir(context, f.getAbsolutePath(), dest.getAbsolutePath(), true);
                        else AccessManager.copyFile(context, f.getAbsolutePath(), dest.getAbsolutePath(), true);
                        AccessManager.preserveTime(context, f.getAbsolutePath(), dest.getAbsolutePath());
                        checkOperationCancelled();
                        AccessManager.delete(context, f.getAbsolutePath(), true);
                        finishItemWork(targetWork);
                        continue;
                    } catch (Exception e) {
                    }
                }
                if (deferredMoveSizing) {
                    long work = cachedCopyWork(f) * 2 + cachedDeleteWork(f);
                    totalWork = Math.addExact(totalWork, work - 1);
                    targetWork = completedWork + work;
                }
                if (f.isDirectory()) {
                    if (useElevated) {
                        try {
                            AccessManager.mkdir(context, dest.getAbsolutePath(), true);
                        } catch (Exception ignored) {
                            //noinspection ResultOfMethodCallIgnored
                            dest.mkdir();
                        }
                    } else {
                        //noinspection ResultOfMethodCallIgnored
                        dest.mkdir();
                    }
                    FileUtils.copyFolder(f, dest);
                } else {
                    SafeFileOperations.copyFile(f, dest);
                    AccessManager.preserveTime(context, f.getAbsolutePath(), dest.getAbsolutePath());
                }
                SafeFileOperations.verifyCopy(f, dest);
                SafeFileOperations.delete(f);
                finishItemWork(targetWork);
            } else if (item instanceof ZipEntryInfo) {
                extractZipEntry((ZipEntryInfo) item, destinationFolder);
            }
        }
        if (!paste) context.handler.post(() -> {
            adapter.clearSelection();
            context.loadFolderInPane(destinationFolder, !adapter.pane1);
            // Source pane also changed after a move (files deleted) — reload it too.
            try {
                File sourceFolder = adapter.pane1 ? context.pane1Folder : context.pane2Folder;
                if (sourceFolder != null && !sourceFolder.equals(destinationFolder)) {
                    context.loadFolderInPane(sourceFolder, adapter.pane1);
                } else {
                    context.refreshAllPanes();
                }
            } catch (Exception ignored) { }
        });
        return true;
    }

    private File getUnusedDest(File destDir, String name, boolean useElevated) {
        File first = FileUtils.getUnusedFile(destDir, name);
        if (!useElevated) return first;
        try {
            if (!AccessManager.exists(context, first.getAbsolutePath())) return first;
            String base = FilenameUtils.getBaseName(name);
            String ext = FilenameUtils.getExtension(name);
            for (int i = 1; i < 1000; i++) {
                String candidate = ext.isEmpty() ? base + " (" + i + ")" : base + " (" + i + ")." + ext;
                File c = new File(destDir, candidate);
                if (!AccessManager.exists(context, c.getAbsolutePath())) return c;
            }
        } catch (Exception ignored) {
        }
        return first;
    }

    private boolean copyToDestination(List<Object> items) throws IOException {
        File destinationFolder = adapter.pane1 ? context.pane2Folder : context.pane1Folder;
        RecyclerView.Adapter rvAdapter = ((RecyclerView) context.findViewById(adapter.pane1 ? R.id.listViewPane2 : R.id.listViewPane1)).getAdapter();
        if (rvAdapter instanceof FtpFilesArrayAdapter) {
            ((FtpFilesArrayAdapter) rvAdapter).uploadFiles(items);
            return true;
        }
        MainFilesArrayAdapter otherPaneAdapter = (MainFilesArrayAdapter) rvAdapter;
        boolean destIsZip = otherPaneAdapter != null && otherPaneAdapter.isInZip;
        if (destIsZip) {
            return copyToZip(items, destinationFolder, otherPaneAdapter.currentZipPath);
        } else {
            return copyToRegularFolder(items, destinationFolder);
        }
    }

    private boolean copyToRegularFolder(List<Object> items, File destinationFolder) throws IOException {
        boolean useElevated = AccessManager.fileOpsOn(context);

        for (Object item : items) {
            checkOperationCancelled();
            if (item instanceof File f) {
                long targetWork = completedWork + cachedCopyWork(f);
                if (io.github.abdurazaaqmohammed.utils.LocalStorage.isPath(f)
                        || io.github.abdurazaaqmohammed.utils.LocalStorage.isPath(destinationFolder)) {
                    io.github.abdurazaaqmohammed.utils.LocalStorage.transfer(f, destinationFolder, false);
                    finishItemWork(targetWork);
                    continue;
                }
                File dest = isSameDirectory(f, destinationFolder) ? promptForDuplicateName(f, destinationFolder) : getUnusedDest(destinationFolder, f.getName(), useElevated);
                if (dest == null || dest.equals(f)) { finishItemWork(targetWork); continue; }
                SafeFileOperations.validateDestination(f, dest);
                if (useElevated) {
                    try {
                        if (f.isDirectory()) AccessManager.copyDir(context, f.getAbsolutePath(), dest.getAbsolutePath(), true);
                        else AccessManager.copyFile(context, f.getAbsolutePath(), dest.getAbsolutePath(), true);
                        AccessManager.preserveTime(context, f.getAbsolutePath(), dest.getAbsolutePath());
                        finishItemWork(targetWork);
                        continue;
                    } catch (Exception e) {
                    }
                }
                if (ShizukuFileOps.involvesShizukuPath(f, destinationFolder) && ShizukuFileOps.shellCopy(f, destinationFolder, dest.getName()) != null) {
                    finishItemWork(targetWork);
                    continue;
                }
                if (f.isDirectory()) {
                    if (useElevated) {
                        try {
                            AccessManager.mkdir(context, dest.getAbsolutePath(), true);
                        } catch (Exception ignored) {
                            //noinspection ResultOfMethodCallIgnored
                            dest.mkdir();
                        }
                    } else {
                        //noinspection ResultOfMethodCallIgnored
                        dest.mkdir();
                    }
                    FileUtils.copyFolder(f, dest);
                } else {
                    SafeFileOperations.copyFile(f, dest);
                    AccessManager.preserveTime(context, f.getAbsolutePath(), dest.getAbsolutePath());
                }
                finishItemWork(targetWork);
            } else if (item instanceof ZipEntryInfo) {
                extractZipEntry((ZipEntryInfo) item, destinationFolder);
            }
        }
        // A move must remove the archive source before either pane opens it
        // again. Reloading here races zip4j's replacement on shared storage.
        if (!moving && !batchingPaste) context.handler.post(context::refreshAllPanes);
        return true;
    }

    private boolean isSameDirectory(File file, File destinationFolder) {
        File parent = file.getParentFile();
        if (parent == null || destinationFolder == null) return false;
        try {
            return parent.getCanonicalPath().equals(destinationFolder.getCanonicalPath());
        } catch (IOException e) {
            return parent.getAbsolutePath().equals(destinationFolder.getAbsolutePath());
        }
    }

    private String getDuplicateName(String fileName, File destinationFolder) {
        String base = FilenameUtils.getBaseName(fileName);
        String ext = FilenameUtils.getExtension(fileName);
        boolean useElevated = false;
        try {
            useElevated = AccessManager.fileOpsOn(context);
        } catch (Exception ignored) {
        }
        int i = 1;
        String candidate;
        do {
            candidate = ext.isEmpty() ? base + " (" + i + ")" : base + " (" + i + ")." + ext;
            i++;
        } while (new File(destinationFolder, candidate).exists()
                || (useElevated && AccessManager.exists(context, new File(destinationFolder, candidate).getAbsolutePath())));
        return candidate;
    }

    private File promptForDuplicateName(File sourceFile, File destinationFolder) throws IOException {
        final CountDownLatch latch = new CountDownLatch(1);
        final File[] result = new File[1];
        final String defaultName = getDuplicateName(sourceFile.getName(), destinationFolder);
        dismissActiveProgress();
        context.handler.post(() -> {
            View view = LayoutInflater.from(context).inflate(R.layout.enter_name, null);
            EditText input = view.findViewById(R.id.m_et_edittext);
            input.setText(defaultName);
            input.setSelection(0, defaultName.length());
            input.requestFocus();
            MaterialAlertDialogBuilder builder = dialogUtil.getDialogBuilder()
                    .setTitle(context.rss.getString(R.string.enter_name_for_copy))
                    .setView(view)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> latch.countDown());
            AlertDialog dialog = builder.create();
            dialog.setOnDismissListener(d -> latch.countDown());
            dialogUtil.styleAlertDialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    String name = input.getText().toString().trim();
                    SafeFileOperations.validateName(name);
                    File target = new File(destinationFolder, name);
                    if (target.exists()) throw new IOException("Destination already exists.");
                    result[0] = target;
                    dialog.dismiss();
                } catch (IOException e) { input.setError(e.getMessage()); }
            });
            dialog.setOnShowListener(d -> {
                input.requestFocus();
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            });
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        if (result[0] != null) showActiveProgress(context.rss.getString(R.string.copying, sourceFile));
        return result[0];
    }

    public boolean copyToZip(List items, File zipFile, String currentPath) throws IOException {
        for (Object item : items) {
            File source = item instanceof File ? (File) item : ((ZipEntryInfo) item).getZipFile();
            if (source.getCanonicalFile().equals(zipFile.getCanonicalFile())) {
                if (item instanceof File) throw new IOException("An archive cannot contain itself.");
                if (moving && item instanceof ZipEntryInfo entry) {
                    String from = entry.getFullPath().replace('\\', '/').replaceAll("/+$", "");
                    String to = (TextUtils.isEmpty(currentPath) ? "" : currentPath.replace('\\', '/').replaceAll("/+$", "") + "/") + entry.getName();
                    if (to.equals(from) || (entry.isDirectory() && to.startsWith(from + "/"))) {
                        throw new IOException("Choose another archive folder for this move.");
                    }
                }
            }
            if (item instanceof File) SafeFileOperations.validateDestination(source, zipFile);
        }
        final boolean isApk = zipFile.getName().endsWith(".apk");
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] proceed = {false};
        final CompressionLevel[] compressionLevel = new CompressionLevel[1];
        final int[] updateMode = {UPDATE_MODE_REPLACE_ALL};
        final boolean[] autosign = new boolean[1];

        dismissActiveProgress();
        context.handler.post(() -> {
            LinearLayout ll = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.dialog_add_to_zip, null);
            ll.<TextView>findViewById(R.id.addToZipText).setText(context.rss.getString(R.string.confirm_add_to_zip_f, summarizeItems(items), zipFile.getName()));

            AutoCompleteTextView compressLevelInput = ll.findViewById(R.id.compress_level);
            compressLevelInput.setText(settings.getString("compressLevel", CompressionLevel.NO_COMPRESSION.name()));
            List<String> compressionLevels = new ArrayList<>();
            for (CompressionLevel cl : CompressionLevel.values()) compressionLevels.add(cl.name());
            compressLevelInput.setAdapter(new ArrayAdapter<>(context, R.layout.dropdownitem, compressionLevels));
            DialogUtil.enableChoices(compressLevelInput, context.getString(R.string.compression_level),
                    compressionLevels.toArray(new String[0]), position -> settings.edit().putString("compressLevel", compressionLevels.get(position)).apply());

            AutoCompleteTextView updateModeInput = ll.findViewById(R.id.update_mode);
            List<String> updateModes = new ArrayList<>(Arrays.asList(
                    context.rss.getString(R.string.replace_all),
                    context.rss.getString(R.string.update_and_replace),
                    context.rss.getString(R.string.skip_all)));
            updateModeInput.setText(updateModes.get(0));
            updateModeInput.setAdapter(new ArrayAdapter<>(context, R.layout.dropdownitem, updateModes));
            if (moving) {
                // A skipped entry is not a successful move and must never delete its source.
                updateModeInput.setEnabled(false);
            }
            DialogUtil.enableChoices(updateModeInput, context.getString(R.string.add),
                    updateModes.toArray(new String[0]), position -> updateMode[0] = position);

            View signRow = ll.findViewById(R.id.sign_row);
            if (isApk) {
                CheckBox autosignCb = ll.findViewById(R.id.autosign);
                autosignCb.setChecked(autosign[0] = settings.getBoolean("autosign", true));
                autosignCb.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", autosign[0] = isChecked).apply());
                ll.findViewById(R.id.sign_settings).setOnClickListener(context.uiHelper.showSignSettingsDialog());
            } else signRow.setVisibility(View.GONE);

            String add = context.rss.getString(R.string.add);
            AlertDialog dialog = dialogUtil.getDialogBuilder()
                    .setTitle(add)
                    .setView(ll)
                    .setPositiveButton(add, (d, w) -> {
                        String level = compressLevelInput.getText().toString();
                        if (level.isEmpty()) level = settings.getString("compressLevel", CompressionLevel.NO_COMPRESSION.name());
                        compressionLevel[0] = CompressionLevel.valueOf(level);
                        proceed[0] = true;
                        latch.countDown();
                    })
                    .setNegativeButton(android.R.string.cancel, (d, w) -> latch.countDown())
                    .create();
            dialog.setOnDismissListener(d -> latch.countDown());
            dialogUtil.styleAlertDialog(dialog);
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        if (!proceed[0]) return false;

        showActiveProgress(context.rss.getString(R.string.adding_to, summarizeItems(items), zipFile.getName()));

        performAddToZip(items, zipFile, currentPath, compressionLevel[0], updateMode[0]);

        if (isApk && autosign[0]) context.handler.post(() ->
                SignWrapper.requireAuth(context, sw -> {
                    activeProgress.setText(context.rss.getString(R.string.signing, zipFile.getName()));
                    new Thread(() -> {
                        try {
                            sw.signApk(zipFile);
                            dismissActiveProgress();
                        } catch (Exception e) {
                            dismissActiveProgress();
                            new ErrorUtil(context).showError(e);
                        }
                    }).start();
                }));
        // A move still has to remove the originals. Starting a pane read here
        // races zip4j's replacement of the same archive on shared storage.
        if (!moving) openZipFile(zipFile, currentPath);
        return true;
    }

    private void performAddToZip(List items, File zipFile, String currentPath, CompressionLevel compressionLevel, int updateMode) throws IOException {
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setCompressionLevel(compressionLevel);
        if (compressionLevel == CompressionLevel.NO_COMPRESSION)
            zipParameters.setCompressionMethod(CompressionMethod.STORE);

        String targetDir = TextUtils.isEmpty(currentPath) ? ""
                : currentPath.replace('\\', '/').replaceAll("/+$", "") + "/";

        File bak = FileUtils.getUnusedFile(zipFile.getParentFile(), zipFile.getName() + ".bak");
        FileUtils.copyFile(zipFile, bak);
        File tempFileDir = null;
        try (ZipFile sourceZip = io.github.abdurazaaqmohammed.domain.files.ArchivePasswords.open(zipFile)) {
            Map<String, Long> existingEntries = new LinkedHashMap<>();
            for (FileHeader fh : sourceZip.getFileHeaders()) existingEntries.put(fh.getFileName(), fh.getLastModifiedTimeEpoch());

            Set<String> toRemove = new LinkedHashSet<>();
            // {file, entryName}; a null entryName marks a directory added under targetDir keeping its own name
            List<Object[]> namedAdds = new ArrayList<>();
            List<File> plainFilesToAdd = new ArrayList<>();

            if (items.get(0) instanceof File) {
                for (Object itemObj : items) {
                    File f = (File) itemObj;
                    String entryName = targetDir.isEmpty() ? f.getName() : targetDir + f.getName();
                    if (!shouldAdd(entryName, f.lastModified(), existingEntries, updateMode, toRemove)) continue;
                    if (f.isDirectory()) namedAdds.add(new Object[] {f, null});
                    else if (targetDir.isEmpty()) plainFilesToAdd.add(f);
                    else namedAdds.add(new Object[] {f, entryName});
                }
            } else {
                tempFileDir = new File(context.getCacheDir(), UUID.randomUUID().toString());
                if (!tempFileDir.mkdirs()) throw new IOException("Cannot create archive staging directory.");
                int counter = 0;
                for (Object itemObj : items) {
                    ZipEntryInfo zipEntry = (ZipEntryInfo) itemObj;
                    String entryName = targetDir + zipEntry.getName();
                    if (!shouldAdd(entryName, zipEntry.getLastModified(), existingEntries, updateMode, toRemove)) continue;
                    File staging = new File(tempFileDir, "item_" + counter++);
                    if (!staging.mkdirs()) throw new IOException("Cannot create archive staging directory.");
                    extractZipEntry(zipEntry, staging);
                    File stagedEntry = new File(staging, zipEntry.getName());
                    namedAdds.add(new Object[] {stagedEntry, zipEntry.isDirectory() ? null : entryName});
                }
            }
            if (!toRemove.isEmpty()) sourceZip.removeFiles(new ArrayList<>(toRemove));

            if (!plainFilesToAdd.isEmpty()) {
                long units = 0;
                for (File file : plainFilesToAdd) units += itemCopyWork(file);
                io.github.abdurazaaqmohammed.utils.ZipTaskProgress.run(sourceZip,
                        () -> sourceZip.addFiles(plainFilesToAdd, zipParameters), units);
            }
            for (Object[] add : namedAdds) {
                File f = (File) add[0];
                ZipParameters params = new ZipParameters(zipParameters);
                if (add[1] == null) {
                    if (!targetDir.isEmpty())
                        params.setRootFolderNameInZip(targetDir.substring(0, targetDir.length() - 1));
                    io.github.abdurazaaqmohammed.utils.ZipTaskProgress.run(sourceZip,
                            () -> sourceZip.addFolder(f, params), itemCopyWork(f));
                } else {
                    params.setFileNameInZip((String) add[1]);
                    io.github.abdurazaaqmohammed.utils.ZipTaskProgress.run(sourceZip,
                            () -> sourceZip.addFile(f, params), itemCopyWork(f));
                }
            }
            if (moving) {
                for (File f : plainFilesToAdd) verifyAddedFile(sourceZip, f, targetDir + f.getName());
                for (Object[] add : namedAdds) {
                    File f = (File) add[0];
                    verifyAddedFile(sourceZip, f, add[1] == null ? targetDir + f.getName() : (String) add[1]);
                }
            }
        } catch (IOException | RuntimeException e) {
            // Rollback must finish even when cancellation interrupted the worker.
            boolean interrupted = Thread.interrupted();
            try { FileUtils.copyFile(bak, zipFile); }
            catch (IOException restoreError) { e.addSuppressed(restoreError); }
            finally { if (interrupted) Thread.currentThread().interrupt(); }
            ZipArchiveCache.invalidate(zipFile);
            throw e;
        } finally {
            if (tempFileDir != null) Util.deleteDir(tempFileDir);
        }
    }

    private static void verifyAddedFile(ZipFile zip, File file, String path) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot verify folder: " + file);
            if (children.length == 0 && zip.getFileHeader(path + "/") == null) {
                throw new IOException("Empty directory missing from archive: " + path);
            }
            for (File child : children) verifyAddedFile(zip, child, path + "/" + child.getName());
            io.github.abdurazaaqmohammed.domain.files.FileWorkProgress.advance(1);
        } else {
            FileHeader header = zip.getFileHeader(path);
            if (header == null || header.getUncompressedSize() != file.length()) throw new IOException("Archive verification failed: " + path);
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            try (InputStream input = FileUtils.getInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    crc.update(buffer, 0, count);
                    io.github.abdurazaaqmohammed.domain.files.FileWorkProgress.advance(count);
                }
            }
            if (crc.getValue() != header.getCrc()) throw new IOException("Archive verification failed: " + path);
            io.github.abdurazaaqmohammed.domain.files.FileWorkProgress.advance(1);
        }
    }

    private boolean shouldAdd(String targetName, long sourceModified, Map<String, Long> existingEntries, int updateMode, Set<String> toRemove) {
        Long existingTime = null;
        for (Map.Entry<String, Long> e : existingEntries.entrySet()) {
            if (e.getKey().equals(targetName) || e.getKey().startsWith(targetName + "/")) {
                existingTime = e.getValue();
                break;
            }
        }
        switch (updateMode) {
            case UPDATE_MODE_SKIP_ALL:
                return existingTime == null;
            case UPDATE_MODE_UPDATE_AND_REPLACE:
                if (existingTime != null && sourceModified <= existingTime) return false;
                markExistingForRemoval(existingEntries.keySet(), targetName, toRemove);
                return true;
            default:
                markExistingForRemoval(existingEntries.keySet(), targetName, toRemove);
                return true;
        }
    }

    private void markExistingForRemoval(Set<String> existingNames, String targetName, Set<String> toRemove) {
        for (String name : existingNames)
            if (name.equals(targetName) || name.startsWith(targetName + "/")) toRemove.add(name);
    }

    private String summarizeItems(List<?> items) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(items.size(), 3);
        for (int i = 0; i < limit; i++) {
            Object o = items.get(i);
            sb.append(o instanceof File ? ((File) o).getName() : ((ZipEntryInfo) o).getName());
            if (i < limit - 1) sb.append(", ");
        }
        if (items.size() > limit) sb.append(" ").append(context.rss.getString(R.string.plus_n_more, items.size() - limit));
        return sb.toString();
    }

    private boolean copyFromZip(List<Object> items) throws IOException {
        File destinationFolder = adapter.pane1 ? context.pane2Folder : context.pane1Folder;
        RecyclerView.Adapter<?> other = ((RecyclerView) context
                .findViewById(adapter.pane1 ? R.id.listViewPane2 : R.id.listViewPane1)).getAdapter();
        if (other instanceof FtpFilesArrayAdapter) throw new IOException("Extract archive entries to a local folder before uploading.");
        MainFilesArrayAdapter otherPaneAdapter = other instanceof MainFilesArrayAdapter ? (MainFilesArrayAdapter) other : null;
        boolean destIsZip = otherPaneAdapter != null && otherPaneAdapter.isInZip;
        if (destIsZip) {
            return copyToZip(items, destinationFolder, otherPaneAdapter.currentZipPath);
        } else {
            return copyToRegularFolder(items, destinationFolder);
        }
    }

    public void extractZipEntry(ZipEntryInfo zipEntry, File destinationFolder) throws IOException {
        String destinationPath = destinationFolder.getPath();
        String zipEntryPath = zipEntry.getFullPath();
        if (zipEntryPath == null) return;
        SafeFileOperations.validateName(zipEntry.getName());
        File target = FileUtils.getUnusedFile(destinationFolder, zipEntry.getName());
        try (ZipFile zf = io.github.abdurazaaqmohammed.domain.files.ArchivePasswords.open(zipEntry.getZipFile())) {
            // zip4j preserves the entry's internal path when extracting, so a file inside
            // "docs/" would land in destination/docs/. Pass an explicit name to avoid that.
            if(zipEntry.isDirectory()) {
                if (!target.mkdirs()) throw new IOException("Cannot create extraction directory: " + target);
                io.github.abdurazaaqmohammed.domain.files.FileWorkProgress.advance(1);
                String prefix = zipEntryPath.endsWith("/") ? zipEntryPath : zipEntryPath + "/";
                for(FileHeader fh : zf.getFileHeaders()) {
                    checkOperationCancelled();
                    String name = fh.getFileName().replace('\\', '/');
                    if(!name.startsWith(prefix)) continue;
                    String relative = name.substring(prefix.length());
                    File child = new File(target, relative);
                    if (!child.getCanonicalPath().startsWith(target.getCanonicalPath() + File.separator)) {
                        if (relative.isEmpty()) continue;
                        throw new IOException("Invalid archive path: " + name);
                    }
                    if (fh.isDirectory()) {
                        if (!child.isDirectory() && !child.mkdirs()) throw new IOException("Cannot create: " + child);
                        io.github.abdurazaaqmohammed.domain.files.FileWorkProgress.advance(1);
                    } else io.github.abdurazaaqmohammed.utils.ZipTaskProgress.run(zf,
                            () -> zf.extractFile(fh, target.getPath(), relative), Math.max(0, fh.getUncompressedSize()) + 1);
                }
            } else io.github.abdurazaaqmohammed.utils.ZipTaskProgress.run(zf,
                    () -> zf.extractFile(zf.getFileHeader(zipEntryPath), destinationPath, target.getName()),
                    Math.max(0, zipEntry.getSize()) + 1);
        }
    }

    private void openZipFile(File zipFile, String path) {
        ZipArchiveCache.invalidate(zipFile);
        context.loadZipFolderInPane(zipFile, path != null ? path : "", !adapter.pane1, false);
    }

    public void deleteZipEntry(ZipEntryInfo... entryToDelete) throws IOException {
        File f = entryToDelete[0].getZipFile();
        List<String> toDelete = new ArrayList<>();
        try(ZipFile zf = io.github.abdurazaaqmohammed.domain.files.ArchivePasswords.open(f)) {
            for(FileHeader fh : zf.getFileHeaders()) {
                String name = fh.getFileName().replace('\\', '/');
                for (ZipEntryInfo info : entryToDelete) {
                    String target = info.getFullPath();
                    if (target == null) continue;
                    if (info.isDirectory() && !target.endsWith("/")) target += "/";
                    if (name.equals(target) || (info.isDirectory() && name.startsWith(target))) {
                        toDelete.add(name);
                        break;
                    }
                }
            }
            io.github.abdurazaaqmohammed.utils.ZipTaskProgress.run(zf,
                    () -> zf.removeFiles(toDelete), Math.max(1, f.length()));
        }
        ZipArchiveCache.invalidate(f);
        context.loadZipFolderInPane(f, adapter.currentZipPath, adapter.pane1, false);
    }

    public void extractArchive(File archive) {
        adapter.withReadableCopy(archive, readable -> extractArchiveUnlocked(readable, archive));
    }

    private void extractArchiveUnlocked(File archive, File original) {
        File parent = original.getParentFile();
        String baseName = archive.getName();
        String folderName = baseName;
        if (baseName.endsWith(".tar.gz")) folderName = baseName.substring(0, baseName.length() - ".tar.gz".length());
        else if (baseName.endsWith(".tar.bz2")) folderName = baseName.substring(0, baseName.length() - ".tar.bz2".length());
        else if (baseName.endsWith(".tar.xz")) folderName = baseName.substring(0, baseName.length() - ".tar.xz".length());
        else {
            int dot = baseName.lastIndexOf('.');
            folderName = dot > 0 ? baseName.substring(0, dot) : baseName + "_extracted";
        }
        File destDir = FileUtils.getUnusedFile(new File(parent, folderName));
        ProgressManager pm = new ProgressManager(context, true);
        pm.setText(context.rss.getString(R.string.extracting_to_folder, destDir.getName()));
        pm.show();
        pm.startWorker(() -> {
            boolean created = false;
            try {
                pm.checkCancelled();
                if (!destDir.mkdirs()) {
                    throw new IOException("Cannot create extraction folder: " + destDir);
                }
                created = true;
                // Root-only archives are unreadable to zip4j/tar readers:
                // stage a copy into cache first (binary-safe).
                File readable = archive;
                File staged = null;
                if (RootStaging.needsStaging(context, archive)) {
                    staged = RootStaging.stageForRead(
                            context, archive.getAbsolutePath());
                    readable = staged;
                }
                try {
                    pm.checkCancelled();
                    boolean keepTime = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("preserve_mtime", true);
                    ArchiveUtil.extract(readable, destDir, keepTime, pm);
                } finally {
                    if (staged != null) {
                        //noinspection ResultOfMethodCallIgnored
                        staged.delete();
                    }
                }
            } catch (Exception e) {
                if (created && pm.isCancelled()) {
                    // Only this task's newly created output is eligible for removal.
                    boolean interrupted = Thread.interrupted();
                    try { SafeFileOperations.delete(destDir); }
                    catch (IOException cleanup) { new ErrorUtil(context).showError(cleanup); }
                    finally { if (interrupted) Thread.currentThread().interrupt(); }
                }
                pm.reportError(e);
            } finally {
                pm.dismiss();
                context.handler.post(() -> context.loadFolderInPane(parent, adapter.pane1));
            }
        });
    }

}
