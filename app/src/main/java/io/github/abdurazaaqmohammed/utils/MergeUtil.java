package io.github.abdurazaaqmohammed.utils;

import io.github.codehasan.colorpicker.extensions.Extensions;

import com.google.android.material.textfield.TextInputLayout;
import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkBundle;
import com.reandroid.apk.ApkModule;
import com.reandroid.apkeditor.Util;
import com.reandroid.apkeditor.common.AndroidManifestHelper;
import com.reandroid.app.AndroidManifest;
import com.reandroid.archive.ArchiveFile;
import com.reandroid.archive.ZipEntryMap;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.container.SpecTypePair;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ResValue;
import com.reandroid.arsc.value.ValueType;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.Deflater;

import io.github.abdurazaaqmohammed.ApkExtractor.APKExtractorActivity;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
public class MergeUtil {
    public static CompressionLevel getPreferredCompressionLevel(Context context) {
        try {
            String s = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("compressLevel", CompressionLevel.NORMAL.name());
            return CompressionLevel.valueOf(s);
        } catch (Exception e) {
            return CompressionLevel.NORMAL;
        }
    }

    public static int toDeflaterLevel(CompressionLevel level) {
        if (level == null) return Deflater.DEFAULT_COMPRESSION;
        return switch (level) {
            case NO_COMPRESSION -> Deflater.NO_COMPRESSION;
            case FASTEST -> Deflater.BEST_SPEED;
            case FASTER -> 2;
            case FAST -> 3;
            case MEDIUM_FAST -> 4;
            case NORMAL -> Deflater.DEFAULT_COMPRESSION;
            case HIGHER -> 7;
            case MAXIMUM -> 8;
            case PRE_ULTRA, ULTRA -> Deflater.BEST_COMPRESSION;
            default -> Deflater.DEFAULT_COMPRESSION;
        };
    }

    public static ZipParameters newPreferredZipParameters(Context context) {
        CompressionLevel level = getPreferredCompressionLevel(context);
        ZipParameters zp = new ZipParameters();
        zp.setCompressionLevel(level);
        zp.setCompressionMethod(level == CompressionLevel.NO_COMPRESSION
                ? CompressionMethod.STORE : CompressionMethod.DEFLATE);
        return zp;
    }

    public static void applyPreferredCompression(ApkBundle bundle, Context context) {
        try {
            bundle.setCompressionLevel(toDeflaterLevel(getPreferredCompressionLevel(context)));
        } catch (Exception ignored) { }
    }


    public static AutoCompleteTextView createCompressionDropdown(Context context) {
        AutoCompleteTextView tv = new AutoCompleteTextView(context);
        tv.setInputType(InputType.TYPE_NULL);
        tv.setSingleLine(false);
        List<String> levels = new ArrayList<>();
        for (CompressionLevel cl : CompressionLevel.values()) levels.add(cl.name());
        String current;
        try {
            current = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("compressLevel", CompressionLevel.NORMAL.name());
        } catch (Exception e) {
            current = CompressionLevel.NORMAL.name();
        }
        tv.setText(current, false);
        tv.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, levels));
        tv.setOnItemClickListener((p, v, pos, id) -> {
            try {
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putString("compressLevel", levels.get(pos)).apply();
            } catch (Exception ignored) { }
        });
        return tv;
    }

    /** Same dropdown wrapped in a labeled Material container for dialog rows. */
    public static TextInputLayout createCompressionDropdownLayout(Context context) {
        TextInputLayout layout =
                new TextInputLayout(context);
        try {
            layout.setHint(context.getString(R.string.compression_level));
        } catch (Exception ignored) {
            layout.setHint("Compression level");
        }
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.addView(createCompressionDropdown(context),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    public static class Options {
        public boolean autosign = true;
        public boolean deviceOnly = true;
        public boolean extractNativeLibs = true;
        public List<String> splitNames = null;
        public File outputDirectory;
        public boolean temporaryInput;
        public String outputName;
    }

    public static void mergeSplitApk(File file, MainActivity context) {
        Options opts = new Options();
        try {
            opts.autosign = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("autosign", true);
            opts.deviceOnly = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("antisplit_device_only", true);
            opts.extractNativeLibs = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("antisplit_extract_native_libs", true);
        } catch (Exception ignored) {
        }
        mergeSplitApk(file, context, opts);
    }

    public static void showAntisplitDialog(File file, MainActivity context) {
        showAntisplitDialog(file, context, false);
    }

    private static void showAntisplitDialog(File file, MainActivity context, boolean temporaryInput) {
        new Thread(() -> {
            List<FileHeader> splits = new ArrayList<>();
            try (ZipFile zf = new ZipFile(file)) {
                for (FileHeader fh : zf.getFileHeaders()) {
                    if (!fh.isDirectory() && fh.getFileName().toLowerCase(Locale.US).endsWith(".apk")) {
                        splits.add(fh);
                    }
                }
            } catch (Exception e) {
                if (temporaryInput) file.delete();
                context.handler.post(() -> new ErrorUtil(context).showError(e));
                return;
            }
            if (splits.isEmpty()) {
                if (temporaryInput) file.delete();
                context.handler.post(() -> Extensions.showMessage(context, context.getString(R.string.antisplit_no_splits)));
                return;
            }
            String baseName = findBaseSplit(splits);
            context.handler.post(() -> showAntisplitDialogInner(file, context, splits, baseName, temporaryInput));
        }).start();
    }

    private static String findBaseSplit(List<FileHeader> splits) {
        for (FileHeader fh : splits) {
            String name = new File(fh.getFileName()).getName();
            if (name.equalsIgnoreCase("base.apk")) return fh.getFileName();
        }
        String fallback = null;
        long biggest = -1;
        for (FileHeader fh : splits) {
            String name = new File(fh.getFileName()).getName().toLowerCase(Locale.US);
            if (!name.contains("split") && !name.startsWith("config.")) return fh.getFileName();
            try {
                long size = fh.getUncompressedSize();
                if (size > biggest) {
                    biggest = size;
                    fallback = fh.getFileName();
                }
            } catch (Exception ignored) {
            }
        }
        return fallback != null ? fallback : splits.get(0).getFileName();
    }

    private static boolean matchesDevice(String entryName, MainActivity context) {
        String lower = entryName.toLowerCase(Locale.US);
        String shortName = new File(lower).getName();
        if (shortName.equals("base.apk")) return true;
        boolean looksLikeConfig = shortName.contains("config.")
                || shortName.contains("split_config")
                || shortName.contains("density")
                || shortName.contains("language")
                || shortName.contains("dpi")
                || shortName.contains("arm")
                || shortName.contains("x86")
                || shortName.contains("mips");
        if (!looksLikeConfig) return true;
        int dpi = 320;
        try {
            dpi = context.getResources().getDisplayMetrics().densityDpi;
        } catch (Exception ignored) {
        }
        String bucket = dpi <= 120 ? "ldpi" : dpi <= 160 ? "mdpi" : dpi <= 213 ? "tvdpi"
                : dpi <= 320 ? "xhdpi" : dpi <= 480 ? "xxhdpi" : "xxxhdpi";
        String normalized = lower.replace('_', '-');
        String bucketDash = bucket.replace('_', '-');
        if (normalized.contains(bucketDash) || lower.contains("nodpi")) return true;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (String abi : Build.SUPPORTED_ABIS) {
                    if (abi == null) continue;
                    String a = abi.toLowerCase(Locale.US);
                    String alt = a.replace('-', '_');
                    String alt2 = a.replace('_', '-');
                    if (lower.contains(a) || lower.contains(alt) || normalized.contains(alt2)) return true;
                }
            } else {
                String abi = Build.CPU_ABI;
                if (abi != null) {
                    String a = abi.toLowerCase(Locale.US);
                    if (lower.contains(a) || lower.contains(a.replace('-', '_'))) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            String lang = Locale.getDefault().getLanguage();
            if (!TextUtils.isEmpty(lang)) {
                String l = lang.toLowerCase(Locale.US);
                if (lower.contains("config." + l) || lower.contains("config_" + l)
                        || lower.contains("-" + l + ".") || lower.contains("_" + l + ".")) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void showAntisplitDialogInner(File file, MainActivity context, List<FileHeader> splits, String baseName, boolean temporaryInput) {
        float density = context.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);

        TextView splitsTitle = new TextView(context);
        splitsTitle.setText(context.getString(R.string.antisplit_splits, splits.size()));
        splitsTitle.setTypeface(null, Typeface.BOLD);
        root.addView(splitsTitle);

        LinearLayout checkBoxHolder = new LinearLayout(context);
        checkBoxHolder.setOrientation(LinearLayout.VERTICAL);
        List<CheckBox> boxes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (FileHeader fh : splits) {
            String fullName = fh.getFileName();
            String shortName = new File(fullName).getName();
            names.add(fullName);
            CheckBox cb = new MaterialCheckBox(context);
            boolean isBase = fullName.equals(baseName);
            cb.setText(isBase ? context.getString(R.string.antisplit_base_suffix, shortName) : shortName);
            cb.setChecked(true);
            cb.setEnabled(!isBase);
            cb.setTag(fullName);
            checkBoxHolder.addView(cb);
            boxes.add(cb);
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(checkBoxHolder);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (200 * density)));

        boolean deviceOnlySaved = true;
        boolean extractSaved = true;
        boolean autosignSaved = true;
        try {
            deviceOnlySaved = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("antisplit_device_only", true);
            extractSaved = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("antisplit_extract_native_libs", true);
            autosignSaved = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("autosign", true);
        } catch (Exception ignored) {
        }

        Runnable applyDeviceSelection = () -> {
            for (CheckBox cb : boxes) {
                String fullName = (String) cb.getTag();
                if (fullName.equals(baseName)) {
                    cb.setChecked(true);
                } else {
                    cb.setChecked(matchesDevice(fullName, context));
                }
            }
        };

        MaterialSwitch deviceOnlySwitch = new MaterialSwitch(context);
        deviceOnlySwitch.setText(context.getString(R.string.antisplit_device_only));
        deviceOnlySwitch.setChecked(deviceOnlySaved);
        root.addView(deviceOnlySwitch);

        MaterialSwitch extractSwitch = new MaterialSwitch(context);
        extractSwitch.setText(context.getString(R.string.set_extract_native_libs));
        extractSwitch.setChecked(extractSaved);
        root.addView(extractSwitch);

        root.addView(createCompressionDropdownLayout(context));

        LinearLayout signRow = new LinearLayout(context);
        signRow.setOrientation(LinearLayout.HORIZONTAL);
        signRow.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox autosignBox = new MaterialCheckBox(context);
        autosignBox.setText(R.string.auto_sign);
        autosignBox.setChecked(autosignSaved);
        LinearLayout.LayoutParams signParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        signRow.addView(autosignBox, signParams);
        MaterialButton signSettings = new MaterialButton(context);
        signSettings.setText(R.string.sign_set);
        signSettings.setOnClickListener(context.uiHelper.showSignSettingsDialog());
        signRow.addView(signSettings);
        root.addView(signRow);

        if (deviceOnlySaved) applyDeviceSelection.run();
        deviceOnlySwitch.setOnCheckedChangeListener((b, checked) -> {
            if (checked) applyDeviceSelection.run();
            else for (CheckBox cb : boxes) cb.setChecked(true);
        });

        final File[] outputDirectory = {defaultOutputDirectory(context, file)};
        TextView outputLabel = new TextView(context);
        outputLabel.setText(context.getString(R.string.merge_output_folder, outputDirectory[0].getAbsolutePath()));
        root.addView(outputLabel);
        MaterialButton chooseOutput = new MaterialButton(context);
        chooseOutput.setText(R.string.merge_choose_output);
        chooseOutput.setOnClickListener(v -> {
            io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog.Properties props =
                    new io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog.Properties();
            props.selection_type = io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog.DIR_SELECT;
            props.offset = outputDirectory[0];
            io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog picker =
                    new io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog(context, props);
            picker.setDialogSelectionListener(paths -> {
                if (paths != null && paths.length > 0) {
                    outputDirectory[0] = new File(paths[0]);
                    outputLabel.setText(context.getString(R.string.merge_output_folder, paths[0]));
                }
            });
            picker.show();
        });
        root.addView(chooseOutput);
        TextView expansionNote = new TextView(context);
        expansionNote.setText(R.string.merge_obb_separate);
        root.addView(expansionNote);
        ScrollView optionsScroll = new ScrollView(context);
        optionsScroll.addView(root);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.merge_single_apk)
                .setView(optionsScroll)
                .setOnCancelListener(d -> { if (temporaryInput) file.delete(); })
                .setNegativeButton(android.R.string.cancel, (d, w) -> { if (temporaryInput) file.delete(); })
                .setPositiveButton(context.getString(R.string.antisplit_merge), (d, w) -> {
                    boolean deviceOnly = deviceOnlySwitch.isChecked();
                    boolean extractLibs = extractSwitch.isChecked();
                    boolean autosign = autosignBox.isChecked();
                    try {
                        PreferenceManager.getDefaultSharedPreferences(context).edit()
                                .putBoolean("antisplit_device_only", deviceOnly)
                                .putBoolean("antisplit_extract_native_libs", extractLibs)
                                .putBoolean("autosign", autosign)
                                .apply();
                    } catch (Exception ignored) {
                    }
                    Options opts = new Options();
                    opts.autosign = autosign;
                    opts.deviceOnly = deviceOnly;
                    opts.extractNativeLibs = extractLibs;
                    opts.outputDirectory = outputDirectory[0];
                    opts.temporaryInput = temporaryInput;
                    if (temporaryInput) opts.outputName = "split-files_merged.apk";
                    List<String> selected = new ArrayList<>();
                    for (CheckBox cb : boxes) {
                        String fullName = (String) cb.getTag();
                        if (fullName.equals(baseName)) {
                            selected.add(fullName);
                        } else if (cb.isChecked()) {
                            selected.add(fullName);
                        }
                    }
                    if (!selected.contains(baseName)) selected.add(0, baseName);
                    opts.splitNames = selected;
                    mergeSplitApk(file, context, opts);
                }).show();
    }

    public static boolean isSplitBundle(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xapk") || lower.endsWith(".apks")
                || lower.endsWith(".apkm") || lower.endsWith(".aspk");
    }

    private static File defaultOutputDirectory(Context context, File file) {
        File parent = file.getParentFile();
        String data = context.getApplicationInfo().dataDir + File.separator;
        if (parent != null && parent.canWrite() && !parent.getAbsolutePath().startsWith(data)) return parent;
        return new File(android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS), "Hexora");
    }

    public static void showMergeFilesDialog(List<File> files, MainActivity context) {
        ProgressManager progress = new ProgressManager(context, true).show();
        new Thread(() -> {
            File archive = null;
            try {
                if (files == null || files.isEmpty()) throw new IOException(context.getString(R.string.antisplit_no_splits));
                archive = File.createTempFile("split-files-", ".apks", context.getCacheDir());
                java.util.Set<String> names = new java.util.HashSet<>();
                try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(archive))) {
                    for (File file : files) {
                        if (file == null || file.isDirectory() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                            throw new IOException("Select APK files belonging to the same split app.");
                        }
                        String name = file.getName();
                        if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate split filename: " + name);
                        zip.putNextEntry(new java.util.zip.ZipEntry(name));
                        try (java.io.InputStream input = FileUtils.getInputStream(file)) {
                            byte[] buffer = new byte[65536];
                            int count;
                            while ((count = input.read(buffer)) != -1) zip.write(buffer, 0, count);
                        }
                        zip.closeEntry();
                    }
                }
                File prepared = archive;
                context.handler.post(() -> { progress.dismiss(); showAntisplitDialog(prepared, context, true); });
            } catch (Exception e) {
                if (archive != null) archive.delete();
                context.handler.post(() -> { progress.dismiss(); new ErrorUtil(context).showError(e); });
            }
        }, "hexora-split-inputs").start();
    }

    public static void showMergeFolderDialog(File folder, MainActivity context) {
        new Thread(() -> {
            File[] entries = folder.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".apk"));
            context.handler.post(() -> {
                if (entries == null || entries.length == 0) Extensions.showMessage(context, R.string.antisplit_no_splits);
                else showMergeFilesDialog(java.util.Arrays.asList(entries), context);
            });
        }, "hexora-split-folder").start();
    }

    public static void mergeSplitApk(File file, MainActivity context, Options opts) {
        final Options options = opts == null ? new Options() : opts;
        ProgressManager progress = new ProgressManager(context, true).show();
        new Thread(() -> {
            File directory = new File(context.getCacheDir(), "merge-" + UUID.randomUUID());
            File pending = null;
            try (ApkBundle bundle = new ApkBundle()) {
                bundle.setAPKLogger(progress.getLogger());
                applyPreferredCompression(bundle, context);
                io.github.abdurazaaqmohammed.domain.files.SplitApkInputs.extract(file, directory, options.splitNames);
                bundle.loadApkDirectory(directory, false);
                validateSplitModules(bundle);
                for (ApkModule module : bundle.getApkModuleList()) {
                    String protect = Util.isProtected(module);
                    if (protect != null) throw new IOException(protect);
                }
                File outputDirectory = options.outputDirectory == null ? defaultOutputDirectory(context, file) : options.outputDirectory;
                if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs() && !outputDirectory.isDirectory()) {
                    throw new IOException("Cannot create output folder.");
                }
                String name = options.outputName;
                if (name == null) {
                    name = file.getName().replaceFirst("(?i)\\.(xapk|aspk|apks|apkm)$", "_merged.apk");
                    if (name.equals(file.getName())) name = file.getName() + "_merged.apk";
                }
                File output = FileUtils.getUnusedFile(new File(outputDirectory, name));
                pending = File.createTempFile(".hexora-merge-", ".apk", outputDirectory);
                try (ApkModule merged = bundle.mergeModules(false)) {
                    sanitizeManifest(merged);
                    merged.setExtractNativeLibs(options.extractNativeLibs);
                    merged.refreshTable();
                    merged.refreshManifest();
                    merged.writeApk(pending);
                }
                File completed = pending;
                context.handler.post(() -> {
                    progress.dismiss();
                    if (options.autosign) {
                        SignWrapper.requireAuth(context, signer -> {
                            ProgressManager signing = new ProgressManager(context, true).setText(context.getString(R.string.signing, output.getName())).show();
                            new Thread(() -> {
                                try {
                                    signer.signApk(completed);
                                    File saved = publishMerge(completed, output, context);
                                    signer.publishStagedV4Signature(completed, saved);
                                    signer.discardStagedV4Signature(completed);
                                }
                                catch (Exception e) {
                                    completed.delete(); signer.discardStagedV4Signature(completed);
                                    context.handler.post(() -> new ErrorUtil(context).showError(e));
                                }
                                finally { signing.dismiss(); }
                            }, "hexora-sign-merged").start();
                        }, () -> {
                            completed.delete();
                            // Only this job's staged sidecar, never an existing output.
                            new File(completed.getParentFile(), completed.getName().replaceFirst("(?i)\\.apk$", ".idsig")).delete();
                        });
                    } else {
                        try { publishMerge(completed, output, context); }
                        catch (Exception e) { completed.delete(); new ErrorUtil(context).showError(e); }
                    }
                });
                pending = null; // The UI/signing continuation now owns this completed copy.
            } catch (Exception e) {
                if (pending != null) pending.delete();
                context.handler.post(() -> { progress.dismiss(); new ErrorUtil(context).showError(e); });
            } finally {
                Util.deleteDir(directory);
                if (options.temporaryInput) file.delete();
            }
        }, "hexora-merge-splits").start();
    }

    private static void validateSplitModules(ApkBundle bundle) throws IOException {
        String packageName = null;
        Integer version = null;
        int baseCount = 0;
        for (ApkModule module : bundle.getApkModuleList()) {
            String name = module.getPackageName();
            if (name == null || name.isEmpty()) throw new IOException("An APK has no package name.");
            if (packageName == null) { packageName = name; version = module.getVersionCode(); }
            if (!packageName.equals(name) || version != module.getVersionCode()) {
                throw new IOException("Select splits of the same app and version.");
            }
            if (!module.getAndroidManifest().isSplit()) baseCount++;
        }
        if (baseCount != 1) throw new IOException("Select exactly one base APK and its matching split APKs.");
    }

    private static File publishMerge(File completed, File requested, MainActivity context) throws IOException {
        // A concurrent job must not replace a file that appeared while this merge was running.
        File target;
        synchronized (MergeUtil.class) {
            target = FileUtils.getUnusedFile(requested);
            if (!completed.renameTo(target)) throw new IOException("Cannot save the merged APK.");
        }
        File saved = target;
        context.handler.post(() -> {
            Extensions.showMessage(context, context.getString(R.string.logger_saved_to, saved.getName()));
            context.loadFolderInPane(saved.getParentFile(), context.lastPaneSelected == 1);
        });
        return saved;
    }

    public static File mergeBundle(ApkBundle bundle) throws IOException {
        return mergeBundle(bundle, null);
    }

    public static File mergeBundle(ApkBundle bundle, Context context) throws IOException {
        for (ApkModule apkModule : bundle.getApkModuleList()) {
            String protect = Util.isProtected(apkModule);
            if (protect != null) {

            }
        }
        try(ApkModule mergedModule = bundle.mergeModules(false)) {
            sanitizeManifest(mergedModule);
            if (context != null) {
                try { mergedModule.setCompressionLevel(toDeflaterLevel(getPreferredCompressionLevel(context))); }
                catch (Exception ignored) { }
            }
            mergedModule.refreshTable();
            mergedModule.refreshManifest();
            File outputFile = FileUtils.getUnusedFile(APKExtractorActivity.getAppFolder(), mergedModule.getPackageName() + ".apk");
            mergedModule.writeApk(outputFile);
            return outputFile;
        }
    }

    private static void sanitizeManifest(ApkModule apkModule) {
        if(!apkModule.hasAndroidManifest()){
            return;
        }
        AndroidManifestBlock manifest = apkModule.getAndroidManifest();
        //logMessage("Sanitizing manifest ...");

        AndroidManifestHelper.removeAttributeFromManifestById(manifest,
                AndroidManifest.ID_requiredSplitTypes, null);
        AndroidManifestHelper.removeAttributeFromManifestById(manifest,
                AndroidManifest.ID_splitTypes, null);
        AndroidManifestHelper.removeAttributeFromManifestByName(manifest,
                AndroidManifest.NAME_splitTypes, null);

        AndroidManifestHelper.removeAttributeFromManifestByName(manifest,
                AndroidManifest.NAME_requiredSplitTypes, null);
        AndroidManifestHelper.removeAttributeFromManifestByName(manifest,
                AndroidManifest.NAME_splitTypes, null);
        AndroidManifestHelper.removeAttributeFromManifestAndApplication(manifest,
                AndroidManifest.ID_extractNativeLibs,
                null, AndroidManifest.NAME_extractNativeLibs);
        AndroidManifestHelper.removeAttributeFromManifestAndApplication(manifest,
                AndroidManifest.ID_isSplitRequired,
                null, AndroidManifest.NAME_isSplitRequired);
        ResXmlElement application = manifest.getApplicationElement();
        List<ResXmlElement> splitMetaDataElements =
                AndroidManifestHelper.listSplitRequired(application);
        boolean splits_removed = false;
        for(ResXmlElement meta : splitMetaDataElements){
            if(!splits_removed){
                splits_removed = removeSplitsTableEntry(meta, apkModule);
            }
            // logMessage("Removed-element : <" + meta.getName() + "> name=\""                    + AndroidManifestBlock.getAndroidNameValue(meta) + "\"");
            application.remove(meta);
        }
        manifest.refresh();
    }
    private static boolean removeSplitsTableEntry(ResXmlElement metaElement, ApkModule apkModule) {
        ResXmlAttribute nameAttribute = metaElement.searchAttributeByResourceId(AndroidManifest.ID_name);
        if(nameAttribute == null){
            return false;
        }
        if(!"com.android.vending.splits".equals(nameAttribute.getValueAsString())){
            return false;
        }
        ResXmlAttribute valueAttribute=metaElement.searchAttributeByResourceId(
                AndroidManifest.ID_value);
        if(valueAttribute==null){
            valueAttribute=metaElement.searchAttributeByResourceId(
                    AndroidManifest.ID_resource);
        }
        if(valueAttribute == null
                || valueAttribute.getValueType() != ValueType.REFERENCE){
            return false;
        }
        if(!apkModule.hasTableBlock()){
            return false;
        }
        TableBlock tableBlock = apkModule.getTableBlock();
        ResourceEntry resourceEntry = tableBlock.getResource(valueAttribute.getData());
        if(resourceEntry == null){
            return false;
        }
        ZipEntryMap zipEntryMap = apkModule.getZipEntryMap();
        for(Entry entry : resourceEntry){
            if(entry == null){
                continue;
            }
            ResValue resValue = entry.getResValue();
            if(resValue == null){
                continue;
            }
            String path = resValue.getValueAsString();
            zipEntryMap.remove(path);
            entry.setNull(true);
            SpecTypePair specTypePair = entry.getTypeBlock()
                    .getParentSpecTypePair();
            specTypePair.removeNullEntries(entry.getId());
        }
        return true;
    }
}
