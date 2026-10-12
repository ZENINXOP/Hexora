package io.github.abdurazaaqmohammed.features.files;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apk.axml.aXMLDecoder;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.utils.ZipArchiveCache;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Document;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.ApkToolsHandler;
import io.github.abdurazaaqmohammed.adapters.main.ChecksumDialogs;
import io.github.abdurazaaqmohammed.adapters.main.FileOperationsHelper;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.domain.files.FileType;
import io.github.abdurazaaqmohammed.arsc.ArscEditorPlusActivity;
import io.github.abdurazaaqmohammed.arsc.ArscEditorActivity;
import io.github.abdurazaaqmohammed.ui.activities.HexEditorActivity;
import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.ui.OpenWithDialog;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.HashUtil;
import io.github.abdurazaaqmohammed.utils.InstallUtil;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;
import io.github.abdurazaaqmohammed.utils.MergeUtil;
import io.github.abdurazaaqmohammed.utils.MimeUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.RootStaging;
import io.github.abdurazaaqmohammed.utils.SignatureKeyDialog;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * File-open workflows extracted from MainFilesArrayAdapter:
 * click dispatch, open-with, font/XML/signature viewers, root-aware
 * staging, split-APK and ARSC menus.
 */
public class FileOpener {

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final boolean pane1;
    private final ApkToolsHandler apkTools;
    private final ChecksumDialogs checksumDialogs;
    private final FileOperationsHelper fileOps;

    public FileOpener(MainActivity context,
                      DialogUtil dialogUtil, boolean pane1,
                      ApkToolsHandler apkTools,
                      ChecksumDialogs checksumDialogs,
                      FileOperationsHelper fileOps) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.pane1 = pane1;
        this.apkTools = apkTools;
        this.checksumDialogs = checksumDialogs;
        this.fileOps = fileOps;
    }

    public interface ReadableCallback {
        void onReady(File readable) throws Exception;
    }

    public void openWithForFile(File file, String fileName) {
        showOpenWithDialog(file, fileName);
    }

    public void openWithForFile(File file, String fileName, File zipFile, String zipEntryPath) {
        showOpenWithDialog(file, fileName, zipFile, zipEntryPath);
    }

    public void showOpenWithDialog(File file, String fileName) {
        showOpenWithDialog(file, fileName, null, null);
    }

    /**
     * @param zipFile       the archive a {@code file} was staged from, or null for normal files
     * @param zipEntryPath  the entry path inside {@code zipFile}, or null
     */
    public void showOpenWithDialog(File file, String fileName, File zipFile, String zipEntryPath) {
        if (file == null) {
            Extensions.showMessage(context, R.string.cannot_open_item);
            return;
        }
        List<OpenWithDialog.Action> actions = new ArrayList<>();
        actions.add(new OpenWithDialog.Action(context.getString(R.string.text_editor),
                R.drawable.baseline_text_snippet_24, 0xFF315CBC, () -> openTextEditor(file, zipFile, zipEntryPath)));
        actions.add(new OpenWithDialog.Action(context.getString(R.string.archive_viewer),
                R.drawable.ic_open_archive, 0xFF8A563F, () -> {
                    String lowerName = fileName.toLowerCase(Locale.ROOT);
                    boolean zipBased = lowerName.endsWith(".zip") || lowerName.endsWith(".apk")
                            || lowerName.endsWith(".jar") || lowerName.endsWith(".aar") || lowerName.endsWith(".war") || lowerName.endsWith(".apks") || lowerName.endsWith(".xapk");
                    if (!file.isFile() || !zipBased) {
                        Extensions.showMessage(context, R.string.not_supported_archive);
                        return;
                    }
                    withReadableCopy(file, readable -> context.openArchiveInPane(readable, file, zipFile, zipEntryPath, pane1));
                }));
        actions.add(new OpenWithDialog.Action(context.getString(R.string.image_viewer),
                R.drawable.image_24px, 0xFF008C99,
                () -> withReadableCopy(file, readable -> context.openImageViewer(readable.getAbsolutePath()))));
        actions.add(new OpenWithDialog.Action(context.getString(R.string.hex_editor),
                R.drawable.ic_open_binary, 0xFF416B7D, () -> openHexEditor(file, zipFile, zipEntryPath)));
        actions.add(new OpenWithDialog.Action(context.getString(R.string.open_with_music_player),
                R.drawable.music_24px, 0xFFD60935,
                () -> withReadableCopy(file, readable -> context.playMediaFile(readable.getAbsolutePath()))));
        actions.add(new OpenWithDialog.Action(context.getString(R.string.open_with_video_player),
                R.drawable.video_24px, 0xFFE45B00,
                () -> withReadableCopy(file, readable -> context.playMediaFile(readable.getAbsolutePath()))));
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (MergeUtil.isSplitBundle(fileName)) {
            actions.add(new OpenWithDialog.Action(context.getString(R.string.merge_single_apk),
                    R.drawable.apk_document_24px, 0xFF15930C,
                    () -> withReadableCopy(file, readable -> MergeUtil.showAntisplitDialog(readable, context))));
        }
        if (lower.endsWith(".xml")) {
            actions.add(new OpenWithDialog.Action(context.getString(R.string.xml_functions),
                    R.drawable.ic_open_xml, 0xFF315CBC, () -> showXmlFunctions(file, fileName)));
        }
        if (lower.endsWith(".class")) {
            actions.add(new OpenWithDialog.Action(context.getString(R.string.class_viewer),
                    R.drawable.ic_java_mt, 0xFF855A47, () -> openClassViewer(file, zipFile, zipEntryPath)));
        }
        if (lower.endsWith(".dex")) {
            actions.add(new OpenWithDialog.Action(context.getString(R.string.open_with_dex_functions),
                    R.drawable.ic_open_dex, 0xFF008E87,
                    () -> withReadableCopy(file, readable -> fileOps.showDexOptionsDialog(readable, zipFile, zipEntryPath, fileName))));
        }
        if (lower.endsWith(".arsc")) {
            actions.add(new OpenWithDialog.Action(context.getString(R.string.arsc_functions),
                    R.drawable.stacks_24px, 0xFFB87900,
                    () -> withReadableCopy(file, readable -> showArscOpenWith(readable, zipFile,
                            zipEntryPath == null ? "resources.arsc" : zipEntryPath))));
        }
        actions.add(new OpenWithDialog.Action(context.getString(R.string.apk_info),
                R.drawable.ic_open_android, 0xFF15930C, () -> {
                    if (!lower.endsWith(".apk") && !lower.endsWith(".apks") && !lower.endsWith(".xapk")) {
                        Extensions.showMessage(context, R.string.not_an_apk);
                        return;
                    }
                    withReadableCopy(file, readable -> apkTools.showApkInfoDialog(readable, fileName));
                }));
        if (lower.endsWith(".sh")) {
            actions.add(new OpenWithDialog.Action(context.getString(R.string.open_with_script_executor),
                    R.drawable.terminal_24px, 0xFF315CBC,
                    () -> withReadableCopy(file, readable -> showShellScriptDialog(readable, file, fileName, zipFile, zipEntryPath))));
        }
        if (lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".woff") || lower.endsWith(".woff2")) {
            actions.add(new OpenWithDialog.Action(context.getString(R.string.font_preview),
                    R.drawable.ic_open_font, 0xFF6552C9, () -> showFontPreview(file, fileName)));
        }
        String keyExt = FilenameUtils.getExtension(fileName).toLowerCase(Locale.ROOT);
        if (keyExt.equals("jks") || keyExt.equals("keystore") || keyExt.equals("p12")
                || keyExt.equals("pfx") || keyExt.equals("pk8") || keyExt.equals("pem")) {
            actions.add(new OpenWithDialog.Action(context.getString(R.string.import_signature),
                    R.drawable.ic_open_key, 0xFF416B7D, () -> importSignature(file, fileName)));
        }
        new OpenWithDialog(context, fileName, actions, type -> appsForOpenWithType(file, type), () -> {
            String reported = MimeUtil.getReportedMimeType(context, file);
            String actual = MimeUtil.getRealMimeType(file);
            return context.getString(R.string.reported_mime) + ": " + (reported == null ? "—" : reported)
                    + "\n" + context.getString(R.string.real_mime) + ": " + (actual == null ? "—" : actual);
        }, () -> withReadableCopy(file, readable -> showAppsForMime(readable, fileName,
                PreferenceManager.getDefaultSharedPreferences(context).getBoolean("fix_mime_type", false)))).show();
    }

    /** The explicit Type menu queries installed apps without reading an entire file or blocking rendering. */
    private List<OpenWithDialog.Action> appsForOpenWithType(File file, OpenWithDialog.Type type) {
        List<OpenWithDialog.Action> actions = new ArrayList<>();
        try {
            File readable = file;
            if (!file.canRead() && AccessManager.fileOpsOn(context)) {
                readable = RootStaging.stageForRead(context, file.getAbsolutePath());
            }
            if (Thread.currentThread().isInterrupted()) return actions;
            Uri uri = FileProvider.getUriForFile(context,
                    io.github.abdurazaaqmohammed.MPManager.BuildConfig.APPLICATION_ID + ".provider", readable);
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> apps = pm.queryIntentActivities(
                    new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type.mime), PackageManager.MATCH_DEFAULT_ONLY);
            apps.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(a.loadLabel(pm)), String.valueOf(b.loadLabel(pm))));
            Set<String> components = new HashSet<>();
            for (ResolveInfo info : apps) {
                if (Thread.currentThread().isInterrupted()) break;
                if (info.activityInfo == null) continue;
                String component = info.activityInfo.packageName + "/" + info.activityInfo.name;
                if (!components.add(component)) continue;
                android.graphics.drawable.Drawable icon = null;
                try { icon = info.loadIcon(pm); } catch (Exception ignored) { }
                actions.add(new OpenWithDialog.Action(String.valueOf(info.loadLabel(pm)), icon,
                        () -> launchAppForMime(info, uri, type.mime), null));
            }
        } catch (Exception ignored) { }
        return actions;
    }

    private static String defaultAppKey(String mime) {
        return "openwith_default_" + mime.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
    }

    private void launchAppForMime(ResolveInfo info, Uri uri, String mime) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClassName(info.activityInfo.packageName, info.activityInfo.name);
        context.startActivity(intent);
    }

    private boolean tryLaunchDefaultApp(Uri uri, String mime) {
        if (mime == null) return false;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String def = prefs.getString(defaultAppKey(mime), null);
        if (def == null) return false;
        ComponentName cn = ComponentName.unflattenFromString(def);
        if (cn == null) return false;
        PackageManager pm = context.getPackageManager();
        try {
            pm.getActivityInfo(cn, 0);
            Intent probe = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime);
            probe.setComponent(cn);
            List<ResolveInfo> stillThere = pm.queryIntentActivities(probe, 0);
            if (!stillThere.isEmpty()) {
                probe.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(probe);
                return true;
            }
        } catch (Exception ignored) {
        }
        prefs.edit().remove(defaultAppKey(mime)).apply();
        return false;
    }

    private void openWithDefaultOrDialog(File file, String fileName) {
        openWithDefaultOrDialog(file, fileName, null);
    }

    private void openWithDefaultOrDialog(File file, String fileName, String knownMime) {
        withReadableCopy(file, readable -> {
            Uri uri;
            try {
                uri = FileProvider.getUriForFile(context, io.github.abdurazaaqmohammed.MPManager.BuildConfig.APPLICATION_ID + ".provider", readable);
            } catch (Exception e) {
                showOpenWithDialog(readable, fileName);
                return;
            }
            String actionMime = knownMime != null ? knownMime : MimeUtil.getMimeTypeForAction(context, readable);
            if (actionMime == null) actionMime = "application/octet-stream";
            if (tryLaunchDefaultApp(uri, actionMime)) return;
            String real = MimeUtil.getRealMimeType(readable);
            if (knownMime == null && real != null && !real.equals(actionMime) && tryLaunchDefaultApp(uri, real)) return;
            String reported = MimeUtil.getReportedMimeType(context, readable);
            if (knownMime == null && reported != null && !reported.equals(actionMime) && (real == null || !reported.equals(real)) && tryLaunchDefaultApp(uri, reported)) return;
            // Let Android use an installed preferred viewer without presenting our tool grid.
            String mime = knownMime != null ? knownMime
                    : real != null && !"application/octet-stream".equals(real) ? real : actionMime;
            if (!"application/octet-stream".equals(mime)) {
                Intent probe = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime);
                PackageManager pm = context.getPackageManager();
                ResolveInfo preferred = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
                List<ResolveInfo> apps = pm.queryIntentActivities(probe,
                        PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_RESOLVED_FILTER);
                apps.removeIf(app -> context.getPackageName().equals(app.activityInfo.packageName));
                apps.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                        String.valueOf(a.loadLabel(pm)), String.valueOf(b.loadLabel(pm))));
                if (preferred != null && preferred.activityInfo != null) {
                    for (ResolveInfo app : apps) {
                        if (app.activityInfo.packageName.equals(preferred.activityInfo.packageName)
                                && app.activityInfo.name.equals(preferred.activityInfo.name)) {
                            launchAppForMime(app, uri, mime); return;
                        }
                    }
                }
                // Generic file managers also register */*. Without a chosen
                // default, handing a PDF to one just opens another tool chooser.
                apps.removeIf(app -> !explicitlyHandlesMime(app, mime));
                if (!apps.isEmpty()) { launchAppForMime(apps.get(0), uri, mime); return; }
                Extensions.showMessage(context, R.string.no_apps_found);
            } else showOpenWithDialog(readable, fileName);
        });
    }

    private static boolean explicitlyHandlesMime(ResolveInfo app, String mime) {
        if (app.filter == null) return false;
        java.util.Iterator<String> types = app.filter.typesIterator();
        if (types != null) while (types.hasNext()) {
            if (mime.equalsIgnoreCase(types.next())) return true;
        }
        return false;
    }

    /** Shared routing for local files and staged archive entries; manual Open with is separate. */
    public void openAutomatically(File file, String name, File zip, String entry) {
        FileType type = FileType.forPath(name);
        String ext = '.' + FilenameUtils.getExtension(name).toLowerCase(Locale.ROOT);
        if (MergeUtil.isSplitBundle(name)) {
            withReadableCopy(file, readable -> showSplitApkMenu(readable, name));
            return;
        }
        if (".sh".equals(ext)) {
            withReadableCopy(file, readable -> showShellScriptDialog(readable, file, name, zip, entry));
            return;
        }
        if (type != null && type.isText()) { openTextEditor(file, zip, entry); return; }
        if (".dex".equals(ext)) {
            withReadableCopy(file, readable -> fileOps.openDexEditor(readable, zip, entry)); return;
        }
        if (type == FileType.ARSC) {
            withReadableCopy(file, readable -> {
                Intent intent = new Intent(context, ArscEditorPlusActivity.class)
                        .putExtra("path", readable.getPath()).putExtra("arscMode", ArscEditorPlusActivity.MODE_PLUS)
                        .putExtra("apkPath", zip == null ? null : zip.getPath()).putExtra("zipEntryPath", entry);
                if (zip != null) context.startActivityForResult(intent, 757); else context.startActivity(intent);
            }); return;
        }
        if (type == FileType.FONT && (".ttf".equals(ext) || ".otf".equals(ext))) {
            showFontPreview(file, name); return;
        }
        if (type == FileType.PDF || type == FileType.WORD || type == FileType.SHEET
                || type == FileType.SLIDES || type == FileType.FONT) {
            openWithDefaultOrDialog(file, name, MimeUtil.getMimeTypeByName(name)); return;
        }
        if (type == FileType.LIBRARY) {
            // Libraries can be viewed as text or bytes; let the user choose each time.
            showOpenWithDialog(file, name, zip, entry);
            return;
        }
        if (type == FileType.CLASS) { openClassViewer(file, zip, entry); return; }
        if (type == FileType.BINARY
                || type == FileType.PYTHON_BYTECODE || type == FileType.DATABASE || type == FileType.DEX) {
            openHexEditor(file, zip, entry); return;
        }
        if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
            withReadableCopy(file, readable -> context.openImageViewer(readable.getPath())); return;
        }
        if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS) || FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
            withReadableCopy(file, readable -> context.playMediaFile(readable.getPath())); return;
        }
        if (".apk".equals(ext)) { withReadableCopy(file, readable -> apkTools.showApkInfoDialog(readable, name)); return; }
        if (".zip".equals(ext) || ".jar".equals(ext) || ".war".equals(ext) || ".aar".equals(ext)) {
            withReadableCopy(file, readable -> context.openArchiveInPane(readable, file, zip, entry, pane1)); return;
        }
        // A bounded signature sniff also recognizes extensionless or renamed text/image/media.
        withReadableCopy(file, readable -> new Thread(() -> {
            String mime = MimeUtil.getRealMimeType(readable);
            context.handler.post(() -> {
                if (context.isFinishing() || context.isDestroyed()) return;
                if ("text/xml".equals(mime)) openCompiledXml(readable, file, zip, entry);
                else if (mime != null && mime.startsWith("text/")) openTextEditor(file, zip, entry);
                else if (mime != null && mime.startsWith("image/")) context.openImageViewer(readable.getPath());
                else if (mime != null && (mime.startsWith("audio/") || mime.startsWith("video/"))) context.playMediaFile(readable.getPath());
                else if ("application/vnd.android.dex".equals(mime)) fileOps.openDexEditor(readable, zip, entry);
                else if ("application/x-executable".equals(mime) || "application/x-msdownload".equals(mime)) openHexEditor(file, zip, entry);
                else if ("application/zip".equals(mime)) context.openArchiveInPane(readable, file, zip, entry, pane1);
                else openWithDefaultOrDialog(file, name);
            });
        }, "hexora-file-type").start());
    }

    private void showAppsForMime(File file, String fileName, boolean useActual) {
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(context, io.github.abdurazaaqmohammed.MPManager.BuildConfig.APPLICATION_ID + ".provider", file);
        } catch (Exception e) {
            new ErrorUtil(context).showError(e);
            return;
        }
        String mime = useActual ? MimeUtil.getRealMimeType(file) : null;
        if (mime == null) mime = MimeUtil.getReportedMimeType(context, file);
        if (mime == null) mime = "application/octet-stream";
        final String chosenMime = mime;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(
                new Intent(Intent.ACTION_VIEW).setDataAndType(uri, chosenMime),
                PackageManager.MATCH_DEFAULT_ONLY);
        if (apps.isEmpty()) {
            Extensions.showMessage(context, R.string.no_apps_found);
            return;
        }
        apps.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                String.valueOf(a.loadLabel(pm)), String.valueOf(b.loadLabel(pm))));
        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle(context.rss.getString(R.string.open_with) + ": " + fileName + " (" + chosenMime + ")")
                .setView(list)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        list.setAdapter(new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(context);
                row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int pad = (int) (12 * context.getResources().getDisplayMetrics().density + 0.5f);
                row.setPadding(pad, pad, pad, pad);
                ImageView icon = new ImageView(context);
                int s = (int) (40 * context.getResources().getDisplayMetrics().density + 0.5f);
                icon.setLayoutParams(new LinearLayout.LayoutParams(s, s));
                LinearLayout texts = new LinearLayout(context);
                texts.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tp.leftMargin = pad;
                TextView name = new TextView(context);
                name.setTextSize(15);
                TextView sub = new TextView(context);
                sub.setTextSize(12);
                texts.addView(name);
                texts.addView(sub);
                row.addView(icon);
                row.addView(texts, tp);
                return new RecyclerView.ViewHolder(row) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ResolveInfo info = apps.get(position);
                LinearLayout row = (LinearLayout) holder.itemView;
                LinearLayout texts = (LinearLayout) row.getChildAt(1);
                ImageView icon = (ImageView) row.getChildAt(0);
                TextView name = (TextView) texts.getChildAt(0);
                TextView sub = (TextView) texts.getChildAt(1);
                try {
                    icon.setImageDrawable(info.loadIcon(pm));
                } catch (Exception ignored) {
                }
                String label = String.valueOf(info.loadLabel(pm));
                name.setText(label);
                String currentDef = prefs.getString(defaultAppKey(chosenMime), null);
                boolean isDef = currentDef != null && currentDef.equals(
                        new ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString());
                sub.setText(isDef ? context.getString(R.string.default_app, label) : info.activityInfo.packageName);
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    try {
                        launchAppForMime(info, uri, chosenMime);
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                });
                row.setOnLongClickListener(v -> {
                    PopupMenu popup = new PopupMenu(context, row);
                    String flat = new ComponentName(
                            info.activityInfo.packageName, info.activityInfo.name).flattenToString();
                    if (isDef) {
                        popup.getMenu().add(context.getString(R.string.clear_default));
                    } else {
                        popup.getMenu().add(context.getString(R.string.set_as_default));
                    }
                    popup.setOnMenuItemClickListener(item -> {
                        if (isDef) prefs.edit().remove(defaultAppKey(chosenMime)).apply();
                        else prefs.edit().putString(defaultAppKey(chosenMime), flat).apply();
                        notifyDataSetChanged();
                        return true;
                    });
                    popup.show();
                    return true;
                });
            }

            @Override
            public int getItemCount() {
                return apps.size();
            }
        });
        dialogUtil.styleAlertDialog(dialog);
    }

    private void showFontPreview(File file, String fileName) {
        withReadableCopy(file, readable -> {
            try {
                Typeface tf = Typeface.createFromFile(readable);
                LinearLayout root = new LinearLayout(context);
                root.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (16 * context.getResources().getDisplayMetrics().density + 0.5f);
                root.setPadding(pad, pad, pad, pad);
                TextView sample = new TextView(context);
                sample.setText("ABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz\n0123456789 !?@#");
                sample.setTypeface(tf);
                sample.setTextSize(22);
                root.addView(sample);
                TextView meta = new TextView(context);
                meta.setText(fileName + " · " + readable.length() + " bytes");
                meta.setTextSize(13);
                root.addView(meta);
                dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                        .setTitle(context.getString(R.string.font_preview) + ": " + fileName)
                        .setView(root)
                        .setPositiveButton(android.R.string.ok, null)
                        .create());
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
        });
    }

    private void showXmlFunctions(File file, String fileName) {
        withReadableCopy(file, readable -> {
            boolean binary = false;
            try (InputStream is = FileUtils.getInputStream(readable)) {
                binary = FileUtils.isAxml(is);
            } catch (Exception ignored) {
            }
            String[] items = binary
                    ? new String[]{context.getString(R.string.open_as_text), context.getString(R.string.decode_open)}
                    : new String[]{context.getString(R.string.open_as_text), context.getString(R.string.format_xml)};
            final boolean isBinary = binary;
            dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                    .setTitle(context.getString(R.string.xml_functions) + ": " + fileName)
                    .setSingleChoiceItems(items, -1, (dialog, which) -> {
                        dialog.dismiss();
                        if (which == 0) {
                            startTextEditor(rootAwareEditorIntent(readable, file)
                                    .putExtra("path", readable.getPath()));
                        } else if (isBinary) {
                            try (InputStream is2 = FileUtils.getInputStream(readable)) {
                                startTextEditor(rootAwareEditorIntent(readable, file)
                                        .putExtra(Intent.EXTRA_TEXT, new aXMLDecoder(is2).decodeAsString().trim())
                                        .putExtra("axml", true)
                                        .putExtra("path", readable.getPath()));
                            } catch (Exception e) {
                                new ErrorUtil(context).showError(e);
                            }
                        } else {
                            formatXmlFile(readable, file, fileName);
                        }
                    }).create());
        });
    }

    private void formatXmlFile(File readable, File original, String fileName) {
        ProgressManager pm = new ProgressManager(context, true);
        pm.show();
        new Thread(() -> {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                Document doc;
                try (InputStream is = FileUtils.getInputStream(readable)) {
                    doc = dbf.newDocumentBuilder().parse(is);
                }
                Transformer transformer =
                        TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                File out = new File(context.getCacheDir(), System.currentTimeMillis() + "_formatted.xml");
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    transformer.transform(new DOMSource(doc),
                            new StreamResult(fos));
                }
                if (!readable.getAbsolutePath().equals(original.getAbsolutePath())) {
                    AccessManager.copyFile(context, out.getAbsolutePath(), original.getAbsolutePath(), true);
                } else {
                    FileUtils.copyFile(out, readable);
                }
                out.delete();
                pm.dismiss();
                context.handler.post(() -> {
                    Extensions.showMessage(context, R.string.xml_formatted);
                    context.loadFolderInPane(original.getParentFile(), pane1);
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void showShellScriptDialog(File readable, File original, String fileName) {
        showShellScriptDialog(readable, original, fileName, null, null);
    }

    private void showShellScriptDialog(File readable, File original, String fileName, File zip, String entry) {
        boolean rootMode = false;
        try {
            rootMode = RootManager.getInstance(context).isRootMode();
        } catch (Exception ignored) {
        }
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, pad, pad, pad);

        TextView warning = new TextView(context);
        warning.setText(context.getString(R.string.sh_warning));
        root.addView(warning);

        RadioGroup modes = new RadioGroup(context);
        RadioButton appMode = new RadioButton(context);
        appMode.setId(View.generateViewId());
        appMode.setText(R.string.sh_run_as_app);
        modes.addView(appMode);
        RadioButton shizukuMode = new RadioButton(context);
        shizukuMode.setId(View.generateViewId());
        shizukuMode.setText(R.string.sh_run_with_shizuku);
        shizukuMode.setEnabled(io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell.isGranted());
        modes.addView(shizukuMode);
        RadioButton rootModeButton = new RadioButton(context);
        rootModeButton.setId(View.generateViewId());
        rootModeButton.setText(R.string.sh_run_as_root);
        if (rootMode) {
            modes.addView(rootModeButton);
        }
        modes.check(AccessManager.active(context) == AccessManager.Backend.SHIZUKU && shizukuMode.isEnabled()
                ? shizukuMode.getId() : appMode.getId());
        root.addView(modes);
        EditText arguments = new EditText(context);
        arguments.setSingleLine(true); arguments.setHint(R.string.sh_arguments);
        root.addView(io.github.abdurazaaqmohammed.ui.UiFields.wrap(context, arguments, null, 0));
        EditText timeout = new EditText(context);
        timeout.setSingleLine(true); timeout.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        timeout.setHint(R.string.sh_timeout); timeout.setText("120");
        root.addView(io.github.abdurazaaqmohammed.ui.UiFields.wrap(context, timeout, null, 0));
        ScrollView settingsScroll = new ScrollView(context); settingsScroll.addView(root);

        AlertDialog actionDialog = dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.sh_select_action) + ": " + fileName)
                .setView(settingsScroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.sh_edit, (dialog, which) -> {
                    Intent intent = rootAwareEditorIntent(readable, original).putExtra("path", readable.getPath());
                    if (zip != null) intent.putExtra("zf", zip.getPath()).putExtra("zipEntryPath", entry);
                    startTextEditor(intent);
                })
                .setPositiveButton(R.string.sh_execute, null).create();
        actionDialog.setOnShowListener(d -> actionDialog.getButton(-1).setOnClickListener(v -> {
            try {
                List<String> values=io.github.abdurazaaqmohammed.domain.files.ShellWords.parse(arguments.getText().toString());
                long seconds=Long.parseLong(timeout.getText().toString());
                if (seconds<0 || seconds>86400) { timeout.setError("Use 0 to 86400 seconds."); return; }
                actionDialog.dismiss();
                io.github.abdurazaaqmohammed.utils.ScriptConsole.show(context, readable, original,
                        modes.getCheckedRadioButtonId()==rootModeButton.getId(),
                        modes.getCheckedRadioButtonId()==shizukuMode.getId(), values, seconds);
            } catch (Exception e) { arguments.setError(e.getMessage()); }
        }));
        dialogUtil.styleAlertDialog(actionDialog);
    }

    private void importSignature(File file, String fileName) {
        File keysDir = new File(Environment.getExternalStorageDirectory()
                + File.separator + "MT2" + File.separator + "keys");
        new Thread(() -> {
            try {
                if (!keysDir.isDirectory() && !keysDir.mkdirs() && !keysDir.isDirectory()) {
                    throw new IOException("Cannot create keys dir");
                }
                File dest = FileUtils.getUnusedFile(new File(keysDir, fileName));
                try (InputStream is = FileUtils.getInputStream(file);
                     FileOutputStream fos = new FileOutputStream(dest)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
                }
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                Set<String> paths = new HashSet<>(prefs.getStringSet("signature_key_paths", new HashSet<>()));
                paths.add(dest.getAbsolutePath());
                prefs.edit().putStringSet("signature_key_paths", paths).putString("keyPath", dest.getAbsolutePath()).apply();
                context.handler.post(() -> Extensions.showMessage(context, R.string.signature_file_set));
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    public void handleFileClick(File file, String fileName) {
        String ext = '.' + FilenameUtils.getExtension(fileName).toLowerCase(java.util.Locale.ROOT);
        if (MergeUtil.isSplitBundle(fileName)) {
            withReadableCopy(file, readable -> showSplitApkMenu(readable, fileName));
        } else if (".sh".equals(ext)) {
            withReadableCopy(file, readable -> showShellScriptDialog(readable, file, fileName));
        } else if (FileUtils.matchExt(ext, FileUtils.TEXT_EXTS) && !".xml".equals(ext)) {
            openTextEditorRootAware(file);
        } else if(HashUtil.isChecksumFile(fileName)) {
            withReadableCopy(file, checksumDialogs::showHashVerifyDialog);
        } else if(".xml".equals(ext)) {
            withReadableCopy(file, readable -> {
                try (InputStream is = FileUtils.getInputStream(readable)) {
                    if (FileUtils.isAxml(is)) openCompiledXml(readable, file, null, null);
                    else startTextEditor(rootAwareEditorIntent(readable, file)
                            .putExtra("path", readable.getPath()));
                } catch (Exception e) {
                    new ErrorUtil(context).showError(e);
                }
            });
        } else if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
            withReadableCopy(file, readable -> context.openImageViewer(readable.getPath()));
        } else if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS) || FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
            withReadableCopy(file, readable -> context.playMediaFile(readable.getPath()));
        } else if ((ext.equals(".apk"))) {
            withReadableCopy(file, readable -> apkTools.showApkInfoDialog(readable, fileName));
        } else {
            String bak = ".bak";
            if(ext.equals(bak)) {
                View et = LayoutInflater.from(context).inflate(R.layout.enter_name, null);
                context.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                EditText tv = et.findViewById(R.id.m_et_edittext);
                String newName = fileName.substring(0, fileName.length() - bak.length());
                tv.setText(newName);
                tv.requestFocus();
                tv.post(() -> {
                    int dot = newName.lastIndexOf('.');
                    tv.setSelection(0, dot > 0 ? dot : newName.length());
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT);
                });
                new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.restore_backup)
                .setView(et)
                .setPositiveButton(R.string.restore, (dialog, which) -> {
                    String name = tv.getText().toString();
                    try {
                        io.github.abdurazaaqmohammed.domain.files.SafeFileOperations.validateName(name);
                        restoreBakRootAware(file, new File(file.getParentFile(), name), fileName);
                    } catch (IOException e) { new ErrorUtil(context).showError(e); }
                })
                .setNegativeButton(android.R.string.cancel, null).show();
            } else if (ext.equals(".zip") || ext.equals(".jar") || ext.equals(".war") || ext.equals(".aar")) {
                withReadableCopy(file, readable -> context.openArchiveInPane(readable, file, null, null, pane1));
            } else if (ext.equals(".arsc")) {
                openAutomatically(file, fileName, null, null);
            } else if (ArchiveUtil.isSupportedArchive(fileName)) {
                withReadableCopy(file, fileOps::extractArchive);
            } else if (ext.equals(".apks") || ext.equals(".xapk") || ext.equals(".aspk") || ext.equals(".apkm")) {
                withReadableCopy(file, readable -> showSplitApkMenu(readable, fileName));
            } else {
                openAutomatically(file, fileName, null, null);
            }
        }
    }

    private File stageZipEntry(ZipEntryInfo zipEntry) throws IOException {
        if (zipEntry == null || zipEntry.isDirectory() || zipEntry.getFullPath() == null) throw new IOException(context.getString(R.string.cannot_open_item));
        File out = new File(context.getCacheDir(), "zip_entry_" + System.currentTimeMillis() + "_" + zipEntry.getName().replaceAll("[^a-zA-Z0-9._-]", "_"));
        ZipArchiveCache.get(zipEntry.getZipFile()).copyEntry(zipEntry.getFullPath(), out);
        return out;
    }

    public void shareZipEntry(Object item, String fileName) {
        if (!(item instanceof ZipEntryInfo)) {
            Extensions.showMessage(context, R.string.cannot_open_item);
            return;
        }
        new Thread(() -> {
            try {
                File staged = stageZipEntry((ZipEntryInfo) item);
                context.handler.post(() -> {
                    try {
                        Uri uri = FileProvider.getUriForFile(context, io.github.abdurazaaqmohammed.MPManager.BuildConfig.APPLICATION_ID + ".provider", staged);
                        String shareMime = MimeUtil.getMimeTypeForAction(context, staged);
                        context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType(shareMime != null ? shareMime : "application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share " + fileName));
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                });
            } catch (Exception e) {
                context.handler.post(() -> new ErrorUtil(context).showError(e));
            }
        }).start();
    }

    public void openWithZipEntry(Object item, String fileName) {
        if (!(item instanceof ZipEntryInfo)) {
            Extensions.showMessage(context, R.string.cannot_open_item);
            return;
        }
        final ZipEntryInfo entry = (ZipEntryInfo) item;
        new Thread(() -> {
            try {
                File staged = stageZipEntry(entry);
                context.handler.post(() -> {
                    try {
                        showOpenWithDialog(staged, fileName, entry.getZipFile(), entry.getFullPath());
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                });
            } catch (Exception e) {
                context.handler.post(() -> new ErrorUtil(context).showError(e));
            }
        }).start();
    }

    public void withReadableCopy(File file, ReadableCallback callback) {
        ReadableCallback cb = readable -> io.github.abdurazaaqmohammed.utils.ArchiveAccess.withAccess(
                context, readable, () -> callback.onReady(readable));
        if (file == null) {
            Extensions.showMessage(context, R.string.cannot_open_item);
            return;
        }
        if (file instanceof io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFile) {
            ProgressManager progress = new ProgressManager(context, true)
                    .setText(context.getString(R.string.reading_with_elevated_access)).show();
            new Thread(() -> {
                try {
                    File staged = ((io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFile) file).materializeTo(context);
                    context.handler.post(() -> {
                        progress.dismiss();
                        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) context.rememberArchiveOrigin(staged, file);
                        try { cb.onReady(staged); } catch (Exception e) { new ErrorUtil(context).showError(e); }
                    });
                } catch (Exception e) {
                    context.handler.post(() -> { progress.dismiss(); new ErrorUtil(context).showError(e); });
                }
            }, "hexora-shizuku-read").start();
            return;
        }
        if (file instanceof io.github.abdurazaaqmohammed.utils.SafAccess.GrantedFile
                || (!file.canRead() && io.github.abdurazaaqmohammed.utils.SafAccess.resolve(file, false) != null)) {
            ProgressManager progress = new ProgressManager(context, true).setText(context.getString(R.string.folder_access_reading)).show();
            new Thread(() -> {
                try {
                    File staged = io.github.abdurazaaqmohammed.utils.SafAccess.stage(context, file);
                    context.handler.post(() -> {
                        progress.dismiss();
                        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) context.rememberArchiveOrigin(staged, file);
                        try { cb.onReady(staged); }
                        catch (Exception e) { new ErrorUtil(context).showError(e); }
                    });
                } catch (Exception e) {
                    progress.dismiss();
                    context.handler.post(() -> new ErrorUtil(context).showError(e));
                }
            }, "hexora-folder-read").start();
            return;
        }
        try {
            if (file != null && file.exists() && file.canRead()) {
                cb.onReady(file);
                return;
            }
        } catch (Exception e) {
            new ErrorUtil(context).showError(e);
            return;
        }
        if (!AccessManager.fileOpsOn(context)) {
            Extensions.showMessage(context, R.string.cannot_open_permission_denied);
            return;
        }
        Extensions.showMessage(context, R.string.reading_with_elevated_access);
        new Thread(() -> {
            try {
                File staged = RootStaging.stageForRead(context, file.getAbsolutePath());
                context.handler.post(() -> {
                    if (file.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) context.rememberArchiveOrigin(staged, file);
                    try {
                        cb.onReady(staged);
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                });
            } catch (Exception e) {
                context.handler.post(() -> new ErrorUtil(context).showError(e));
            }
        }).start();
    }

    private void startTextEditor(Intent intent) {
        // A local or read-only file can resume a minimized session containing
        // saved archive edits. Always retain a result path to this browser.
        context.startActivityForResult(intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), 757);
    }

    private Intent rootAwareEditorIntent(File readable, File original) {
        Intent i = new Intent(context, TextEditorActivity.class);
        if (readable != null && original != null
                && !readable.getAbsolutePath().equals(original.getAbsolutePath())) {
            i.putExtra("rootOriginalPath", original.getAbsolutePath());
            Extensions.showMessage(context, io.github.abdurazaaqmohammed.utils.SafAccess.resolve(original, false) != null
                    ? R.string.opened_with_folder_access : R.string.opened_with_root);
        }
        return i;
    }

    private void openTextEditorRootAware(File file) {
        withReadableCopy(file, readable ->
                startTextEditor(rootAwareEditorIntent(readable, file)
                        .putExtra("path", readable.getAbsolutePath())));
    }

    private void openCompiledXml(File readable, File original, File zip, String entry) {
        // The editor decodes in its document worker and enforces its byte limit.
        // Do not materialize large XML strings on the file browser's UI thread.
        Intent intent = rootAwareEditorIntent(readable, original)
                .putExtra("path", readable.getPath()).putExtra("axml", true);
        if (zip != null) intent.putExtra("zf", zip.getPath()).putExtra("zipEntryPath", entry);
        startTextEditor(intent);
    }

    /**
     * Opens {@code file} in the built-in text editor. When the file was staged out of an
     * archive, the archive and entry path are forwarded (with request code 757) so the
     * editor returns through {@code handleModifiedFileResult} and MainActivity can offer to
     * add the edit back to the archive.
     */
    private void openClassViewer(File file, File zipFile, String entry) {
        withReadableCopy(file, readable -> {
            Intent intent = new Intent(context, TextEditorActivity.class)
                    .putExtra("path", readable.getPath()).putExtra("jvmClass", true);
            if (zipFile != null) intent.putExtra("zf", zipFile.getPath()).putExtra("zipEntryPath", entry);
            startTextEditor(intent);
        });
    }

    private void openTextEditor(File file, File zipFile, String zipEntryPath) {
        if (zipFile != null && zipEntryPath != null && !zipEntryPath.isEmpty()) {
            startTextEditor(new Intent(context, TextEditorActivity.class)
                    .putExtra("zf", zipFile.getPath())
                    .putExtra("zipEntryPath", zipEntryPath)
                    .putExtra("path", file.getAbsolutePath()));
            return;
        }
        openTextEditorRootAware(file);
    }

    private void openHexEditorRootAware(File file) {
        openHexEditor(file, null, null);
    }

    private void openHexEditor(File file, File zip, String entry) {
        withReadableCopy(file, readable -> {
            Intent i = new Intent(context, HexEditorActivity.class)
                    .putExtra("path", readable.getAbsolutePath());
            if (zip != null) i.putExtra("zipFilePath", zip.getPath()).putExtra("zipEntryPath", entry);
            if (!readable.getAbsolutePath().equals(file.getAbsolutePath())) {
                i.putExtra("rootOriginalPath", file.getAbsolutePath());
                Extensions.showMessage(context, io.github.abdurazaaqmohammed.utils.SafAccess.resolve(file, false) != null
                        ? R.string.opened_with_folder_access : R.string.opened_with_root);
            }
            if (zip != null) context.startActivityForResult(i, 757); else context.startActivity(i);
        });
    }

    private void restoreBakRootAware(File bakFile, File origFile, String fileName) {
        try {
            io.github.abdurazaaqmohammed.domain.files.SafeFileOperations.validateDestination(bakFile, origFile);
        } catch (IOException e) { new ErrorUtil(context).showError(e); return; }
        String bakPath = bakFile.getPath();
        String origPath = origFile.getPath();
        boolean useElevated = AccessManager.fileOpsOn(context)
                && (RootStaging.needsStaging(context, bakPath) || RootStaging.needsStaging(context, origPath));
        if (useElevated) {
            new Thread(() -> {
                String previousPath = origPath + ".hexora-restore-" + java.util.UUID.randomUUID() + ".bak";
                boolean preserved = false;
                boolean restored = false;
                try {
                    boolean origExists = AccessManager.exists(context, origPath) || origFile.exists();
                    if (origExists) {
                        AccessManager.rename(context, origPath, previousPath, true);
                        preserved = true;
                    }
                    AccessManager.rename(context, bakPath, origPath, true);
                    restored = true;
                    if (origExists) AccessManager.rename(context, previousPath, bakPath, true);
                    context.handler.post(() -> context.loadFolderInPane(
                            bakFile.getParentFile() != null ? bakFile.getParentFile() : new File("/"), pane1));
                } catch (Exception e) {
                    if (preserved && !restored) {
                        try { AccessManager.rename(context, previousPath, origPath, true); }
                        catch (Exception rollback) { e.addSuppressed(rollback); }
                    }
                    new ErrorUtil(context).showError(new IOException("Restore failed. Recovery paths: "
                            + bakPath + ", " + origPath + ", " + previousPath, e));
                }
            }).start();
            return;
        }
        new Thread(() -> {
            try {
                io.github.abdurazaaqmohammed.domain.files.SafeFileOperations.restoreBackup(bakFile, origFile);
                context.handler.post(() -> context.loadFolderInPane(bakFile.getParentFile(), pane1));
            } catch (IOException e) { new ErrorUtil(context).showError(e); }
        }, "hexora-restore-backup").start();
    }

    private void showSplitApkMenu(File readable, String displayName) {
        String[] items = new String[] { context.rss.getString(R.string.install), context.rss.getString(R.string.view), context.rss.getString(R.string.sign), context.rss.getString(R.string.merge_single_apk) };
        dialogUtil.styleAlertDialog(
                dialogUtil.getDialogBuilder().setSingleChoiceItems(items, -1, (dialog, which) -> {
                    dialog.dismiss();
                    try {
                        switch (which) {
                            case 0:
                                if (LegacyUtils.aboveSdk20) {
                                    new Thread(() -> {
                                        try (ZipFile zf = new ZipFile(readable)) {
                                            List<File> apkFiles = new ArrayList<>();
                                            File tmpDir = new File(context.getCacheDir(), "split_install_" + System.currentTimeMillis());
                                            //noinspection ResultOfMethodCallIgnored
                                            tmpDir.mkdirs();
                                            for (FileHeader fh : zf.getFileHeaders()) {
                                                if (fh.getFileName().endsWith(".apk")) {
                                                    File tmpApk = new File(tmpDir, new File(fh.getFileName()).getName());
                                                    try (InputStream is = zf.getInputStream(fh);
                                                         FileOutputStream fos = new FileOutputStream(tmpApk)) {
                                                        byte[] buf = new byte[65536];
                                                        int n;
                                                        while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
                                                    }
                                                    apkFiles.add(tmpApk);
                                                }
                                            }
                                            if (!apkFiles.isEmpty()) {
                                                InstallUtil.installSplitApksWithDialog(context, apkFiles, displayName);
                                            } else {
                                                context.runOnUiThread(() -> Extensions.showMessage(context, R.string.no_apk_files_found));
                                            }
                                        } catch (Exception e) {
                                            context.runOnUiThread(() -> new ErrorUtil(context).showError(e));
                                        }
                                    }).start();
                                } else {
                                    Extensions.showMessage(context, "Installing split APKs is not supported on this version of Android :(");
                                    context.handler.postDelayed(() -> Extensions.showMessage(context, "You could try merging the APK then installing it"), 1500);
                                }
                                break;
                            case 1:
                                context.loadZipFolderInPane(readable, "", pane1, true);
                                break;
                            case 2:
                                SignatureKeyDialog.show(context, readable, true);
                                break;
                            case 3:
                                MergeUtil.showAntisplitDialog(readable, context);
                                break;
                        }
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                }).create());
    }

    private void showArscOpenWith(File arscFile, File apkFile, String entryPath) {
        String[] options = {context.rss.getString(R.string.arsc_editor_plus), context.rss.getString(R.string.arsc_editor), context.rss.getString(R.string.translation_mode), context.rss.getString(R.string.resource_querier)};
        String[] modes = {
                ArscEditorPlusActivity.MODE_PLUS,
                ArscEditorPlusActivity.MODE_EDITOR,
                ArscEditorPlusActivity.MODE_TRANSLATE,
                ArscEditorPlusActivity.MODE_QUERIER};
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(R.string.open_with)
                .setSingleChoiceItems(options, -1, (dialog, which) -> {
                    dialog.dismiss();
                    Class<?> target = ArscEditorPlusActivity.MODE_EDITOR.equals(modes[which])
                            ? ArscEditorActivity.class
                            : ArscEditorPlusActivity.class;
                    Intent arscIntent = new Intent(context, target)
                            .putExtra("path", arscFile.getAbsolutePath())
                            .putExtra("apkPath", apkFile == null ? null : apkFile.getAbsolutePath())
                            .putExtra("zipEntryPath", entryPath)
                            .putExtra("arscMode", modes[which]);
                    // Inside an archive the editor only edits the extracted copy and
                    // returns it via setResult(757); MainActivity then shows the
                    // "APK/ZIP updated" prompt and injects the file itself.
                    if (apkFile != null) context.startActivityForResult(arscIntent, 757);
                    else context.startActivity(arscIntent);
                }).create());
    }
}
