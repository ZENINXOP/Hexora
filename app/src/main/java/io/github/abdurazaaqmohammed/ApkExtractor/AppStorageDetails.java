package io.github.abdurazaaqmohammed.ApkExtractor;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.FileSize;

/** Paths come from the installed application's metadata, never a guessed /data/data path. */
final class AppStorageDetails {
    private AppStorageDetails() { }

    static void show(APKExtractorActivity activity, AppInfo app) {
        ApplicationInfo info = app.appInfo;
        if (info == null) {
            try { info = activity.getPackageManager().getApplicationInfo(app.packageName, 0); }
            catch (Exception e) { new io.github.abdurazaaqmohammed.utils.ErrorUtil(activity).showError(e); return; }
        }
        final ApplicationInfo installed = info;
        TextView details = new TextView(activity);
        int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        details.setPadding(pad, pad, pad, pad);
        details.setTextIsSelectable(true);
        details.setText(R.string.loading);
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(details);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle(app.name)
                .setView(scroll).setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.app_storage_directories, (d, w) -> showDirectories(activity, installed))
                .setNegativeButton(R.string.app_storage_usage_access, (d, w) -> {
                    try { activity.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
                    catch (Exception e) { new io.github.abdurazaaqmohammed.utils.ErrorUtil(activity).showError(e); }
                }).show();
        new Thread(() -> {
            long code = 0, data = -1, cache = -1;
            Set<String> apkPaths = new HashSet<>();
            if (installed.sourceDir != null) apkPaths.add(installed.sourceDir);
            if (installed.splitSourceDirs != null) java.util.Collections.addAll(apkPaths, installed.splitSourceDirs);
            boolean codeKnown = !apkPaths.isEmpty();
            for (String path : apkPaths) {
                long bytes = new File(path).isFile() ? new File(path).length() : AccessManager.getSize(activity, path);
                if (bytes < 0) codeKnown = false;
                else code += bytes;
            }
            if (!codeKnown) code = -1;
            if (Build.VERSION.SDK_INT >= 26) try {
                android.app.usage.StorageStatsManager manager = activity.getSystemService(android.app.usage.StorageStatsManager.class);
                if (manager != null) {
                    android.app.usage.StorageStats stats = manager.queryStatsForPackage(installed.storageUuid, app.packageName, Process.myUserHandle());
                    code = stats.getAppBytes();
                    data = Math.max(0, stats.getDataBytes() - stats.getCacheBytes());
                    cache = stats.getCacheBytes();
                }
            } catch (Exception ignored) { }
            if (data < 0 && installed.dataDir != null) {
                long total = directorySize(activity, new File(installed.dataDir));
                cache = directorySize(activity, new File(installed.dataDir, "cache"));
                data = total < 0 || cache < 0 ? -1 : Math.max(0, total - cache);
            }
            StringBuilder text = new StringBuilder(app.packageName).append("\n\n")
                    .append(activity.getString(R.string.app_storage_package_size)).append(": ").append(size(activity, code)).append('\n')
                    .append(activity.getString(R.string.app_storage_data_size)).append(": ").append(size(activity, data)).append('\n')
                    .append(activity.getString(R.string.app_storage_cache_size)).append(": ").append(size(activity, cache)).append("\n\n")
                    .append(activity.getString(R.string.app_storage_package_directory)).append("\n")
                    .append(installed.sourceDir == null ? activity.getString(R.string.app_storage_unavailable) : new File(installed.sourceDir).getParent())
                    .append("\n\n")
                    .append(activity.getString(R.string.app_storage_data_directory)).append("\n").append(installed.dataDir);
            if (Build.VERSION.SDK_INT >= 24 && installed.deviceProtectedDataDir != null)
                text.append("\n\n").append(activity.getString(R.string.app_storage_device_data)).append("\n").append(installed.deviceProtectedDataDir);
            if (data < 0) text.append("\n\n").append(activity.getString(R.string.app_storage_access_needed));
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed() && dialog.isShowing()) details.setText(text);
            });
        }, "hexora-app-storage").start();
    }

    private static String size(APKExtractorActivity activity, long size) {
        return size < 0 ? activity.getString(R.string.app_storage_unavailable) : FileSize.getHumanReadableFileSize(size);
    }

    private static long directorySize(APKExtractorActivity activity, File directory) {
        if (AccessManager.fileOpsOn(activity)) {
            long size = AccessManager.dirSize(activity, directory.getAbsolutePath());
            if (size >= 0) return size;
        }
        try { return measure(directory, new HashSet<>()); }
        catch (IOException e) { return -1; }
    }

    private static long measure(File file, Set<String> visited) throws IOException {
        if (Thread.currentThread().isInterrupted() || !file.canRead()) throw new IOException("Cannot read app storage.");
        if (file.isFile()) return file.length();
        if (!visited.add(file.getCanonicalPath())) throw new IOException("Directory cycle.");
        File[] children = file.listFiles();
        if (children == null) throw new IOException("Cannot list app storage.");
        long size = 0;
        for (File child : children) size = Math.addExact(size, measure(child, visited));
        return size;
    }

    private static void showDirectories(APKExtractorActivity activity, ApplicationInfo info) {
        java.util.List<String> labels = new java.util.ArrayList<>(), paths = new java.util.ArrayList<>();
        if (info.sourceDir != null) { labels.add(activity.getString(R.string.app_storage_package_directory)); paths.add(new File(info.sourceDir).getParent()); }
        if (info.dataDir != null) { labels.add(activity.getString(R.string.app_storage_data_directory)); paths.add(info.dataDir); }
        if (Build.VERSION.SDK_INT >= 24 && info.deviceProtectedDataDir != null) {
            labels.add(activity.getString(R.string.app_storage_device_data)); paths.add(info.deviceProtectedDataDir);
        }
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.app_storage_directories)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> activity.startActivity(
                        new Intent(activity, MainActivity.class).putExtra("locatePath", paths.get(which))
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)))
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}
