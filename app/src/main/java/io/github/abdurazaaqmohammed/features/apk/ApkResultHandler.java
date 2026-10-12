package io.github.abdurazaaqmohammed.features.apk;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.preference.PreferenceManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.domain.files.ArchiveDocumentSaver;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.ZipArchiveCache;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Confirms and publishes every saved archive document returned by an editor. */
public class ApkResultHandler {
    private final MainActivity activity;
    public ApkResultHandler(MainActivity activity) { this.activity = activity; }

    public void handleModifiedFileResult(Uri uri) { handleModifiedFileResult(uri, null, null); }

    public void handleModifiedFileResult(Uri uri, String entryPath, String zipFileExtra) {
        Intent edit = new Intent().setData(uri);
        if (entryPath != null) edit.putExtra("zipEntryPath", entryPath);
        if (zipFileExtra != null) edit.putExtra("zipFilePath", zipFileExtra);
        handleModifiedFileResults(java.util.Collections.singletonList(edit));
    }

    private boolean isPrivateFile(File file) throws IOException {
        String path = file.getCanonicalPath();
        return path.startsWith(activity.getCacheDir().getCanonicalPath() + File.separator)
                || path.startsWith(activity.getFilesDir().getCanonicalPath() + File.separator);
    }

    private static boolean isApk(File archive) { return archive.getName().toLowerCase(Locale.ROOT).endsWith(".apk"); }

    public void handleModifiedFileResults(List<Intent> edits) {
        Map<File, List<ArchiveDocumentSaver.Update>> updates = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>();
        try {
            for (Intent edit : edits) {
                if (edit == null || edit.getData() == null || edit.getData().getPath() == null) continue;
                File saved = new File(edit.getData().getPath());
                if (!isPrivateFile(saved)) continue; // Local documents already saved directly.
                String explicitArchive = edit.getStringExtra("zipFilePath");
                File archive = explicitArchive != null && !explicitArchive.isEmpty() ? new File(explicitArchive)
                        : activity.lastPaneSelected == 1 ? activity.pane1Folder : activity.pane2Folder;
                // Never redirect a missing explicit source into a different open archive.
                if (archive == null || !archive.isFile()) throw new IOException(activity.getString(R.string.archive_no_longer_open));
                archive = archive.getCanonicalFile();
                String entry = edit.getStringExtra("zipEntryPath");
                List<ArchiveDocumentSaver.Update> batch = updates.computeIfAbsent(archive, key -> new ArrayList<>());
                if ((entry == null || entry.isEmpty()) && saved.getName().startsWith("classes") && saved.getName().endsWith(".dex")) {
                    File[] dexFiles = saved.getParentFile().listFiles((dir, name) -> name.startsWith("classes") && name.endsWith(".dex"));
                    if (dexFiles == null || dexFiles.length == 0) throw new IOException("Saved DEX files are unavailable.");
                    for (File dex : dexFiles) batch.add(new ArchiveDocumentSaver.Update(dex.getName(), dex, false));
                } else {
                    batch.add(new ArchiveDocumentSaver.Update(entry == null || entry.isEmpty() ? saved.getName() : entry,
                            saved, entry != null && !entry.isEmpty()));
                }
                labels.add(archive.getName() + ": " + (entry == null ? saved.getName() : entry));
            }
        } catch (Exception error) { new ErrorUtil(activity).showError(error); return; }
        if (updates.isEmpty()) return;
        boolean hasApk = updates.keySet().stream().anyMatch(ApkResultHandler::isApk);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
        LinearLayout content = (LinearLayout) LayoutInflater.from(activity).inflate(R.layout.item_modified_dialog, null);
        content.<TextView>findViewById(R.id.modifiedText).setText(activity.getString(R.string.file_modified_x,
                android.text.TextUtils.join("\n", labels), hasApk ? "APK" : "ZIP"));
        CheckBox autosign = content.findViewById(R.id.autosign);
        boolean[] sign = {hasApk && settings.getBoolean("autosign", true)};
        autosign.setChecked(sign[0]);
        ((View) autosign.getParent()).setVisibility(hasApk ? View.VISIBLE : View.GONE);
        autosign.setOnCheckedChangeListener((button, checked) -> settings.edit().putBoolean("autosign", sign[0] = checked).apply());
        content.findViewById(R.id.sign_settings).setOnClickListener(activity.uiHelper.showSignSettingsDialog());
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.file_modified).setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    SignWrapper[] signer = new SignWrapper[1];
                    Runnable save = () -> {
                        ProgressManager progress = new ProgressManager(activity, true).show();
                        new Thread(() -> {
                            try {
                                for (Map.Entry<File, List<ArchiveDocumentSaver.Update>> batch : updates.entrySet()) {
                                    File archive = batch.getKey();
                                    progress.setText(activity.getString(R.string.adding, archive.getName()));
                                    File[] signedStage = new File[1];
                                    try {
                                        ArchiveDocumentSaver.save(archive, batch.getValue(),
                                                sign[0] && isApk(archive) ? staged -> {
                                                    signedStage[0] = staged;
                                                    signer[0].signApk(staged);
                                                } : null,
                                                (staged, destination) -> {
                                                    if (!staged.renameTo(destination)) throw new IOException("Cannot replace archive; original retained.");
                                                });
                                        if (signedStage[0] != null) signer[0].publishStagedV4Signature(signedStage[0], archive);
                                    } finally {
                                        ZipArchiveCache.invalidate(archive);
                                        if (signedStage[0] != null) signer[0].discardStagedV4Signature(signedStage[0]);
                                    }
                                }
                            } catch (Exception error) { new ErrorUtil(activity).showError(error); }
                            finally { progress.dismiss(); activity.handler.post(activity::refreshAllPanes); }
                        }, "hexora-archive-save").start();
                    };
                    if (sign[0]) SignWrapper.requireAuth(activity, wrapper -> { signer[0] = wrapper; save.run(); });
                    else save.run();
                }).show();
    }
}
