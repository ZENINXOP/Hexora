package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import java.io.File;

/** Small, file-version-aware cache shared by APK rows and the info dialog. */
public final class ApkMetadata {
    private static final LruCache<String, ApkMetadata> CACHE = new LruCache<>(32);
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.FutureTask<ApkMetadata>> LOADING =
            new java.util.concurrent.ConcurrentHashMap<>();
    public final PackageInfo packageInfo;
    public final String label;
    public final Drawable icon;

    private ApkMetadata(PackageInfo info, String label, Drawable icon) {
        this.packageInfo = info;
        this.label = label;
        this.icon = icon;
    }

    public Drawable newIcon(Resources resources) {
        if (icon == null) return null;
        Drawable.ConstantState state = icon.getConstantState();
        return state == null ? icon : state.newDrawable(resources).mutate();
    }

    public static String key(File file) {
        return file.getAbsolutePath() + ':' + file.lastModified() + ':' + file.length();
    }

    public static void clearCache() {
        CACHE.evictAll();
    }

    /** Call on a worker thread. Never holds the cache lock while parsing an APK. */
    public static ApkMetadata load(Context context, File file) {
        String key = key(file);
        ApkMetadata cached = CACHE.get(key);
        if (cached != null) return cached;
        Context app = context.getApplicationContext();
        java.util.concurrent.FutureTask<ApkMetadata> created = new java.util.concurrent.FutureTask<>(() -> loadUncached(app, file));
        java.util.concurrent.FutureTask<ApkMetadata> task = LOADING.putIfAbsent(key, created);
        if (task == null) task = created;
        try {
            // Both panes and the details dialog share one parse/extraction.
            task.run();
            return task.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return null;
        } catch (java.util.concurrent.ExecutionException error) {
            return null;
        } finally {
            if (task.isDone()) LOADING.remove(key, task);
        }
    }

    private static ApkMetadata loadUncached(Context context, File file) {
        String key = key(file);
        ApkMetadata cached = CACHE.get(key);
        if (cached != null) return cached;
        String lower = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".apks") || lower.endsWith(".xapk") || lower.endsWith(".apkm") || lower.endsWith(".aspk")) {
            File temporary = null;
            java.util.List<File> resourceSplits = new java.util.ArrayList<>();
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file)) {
                java.util.zip.ZipEntry selected = null;
                int best = -1;
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    java.util.zip.ZipEntry entry = entries.nextElement();
                    String name = entry.getName().toLowerCase(java.util.Locale.ROOT);
                    if (entry.isDirectory() || !name.endsWith(".apk") || entry.getSize() > 256L * 1024 * 1024) continue;
                    int rank = name.endsWith("/base.apk") || name.equals("base.apk") ? 4
                            : name.contains("base-master") ? 3 : name.contains("standalone") ? 2
                            : !name.contains("config.") && !name.contains("split_config") ? 1 : 0;
                    if (rank > best) { selected = entry; best = rank; }
                }
                if (selected == null) return null;
                temporary = File.createTempFile("bundle-icon-", ".apk", context.getCacheDir());
                try (java.io.InputStream input = zip.getInputStream(selected);
                     java.io.OutputStream output = new java.io.FileOutputStream(temporary)) {
                    byte[] buffer = new byte[64 * 1024];
                    long total = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > 256L * 1024 * 1024 || Thread.currentThread().isInterrupted()) return null;
                        output.write(buffer, 0, count);
                    }
                }
                ApkMetadata metadata = load(context, temporary);
                if (metadata != null && android.os.Build.VERSION.SDK_INT >= 21) {
                    // Bundletool often puts launcher pixels in a density split, not base-master.
                    String selectedName = selected.getName();
                    String parent = selectedName.substring(0, selectedName.lastIndexOf('/') + 1);
                    java.util.Enumeration<? extends java.util.zip.ZipEntry> splits = zip.entries();
                    long splitBytes = 0;
                    while (splits.hasMoreElements() && resourceSplits.size() < 8) {
                        java.util.zip.ZipEntry entry = splits.nextElement();
                        String name = entry.getName().toLowerCase(java.util.Locale.ROOT);
                        if (entry.isDirectory() || !entry.getName().startsWith(parent)
                                || !name.matches(".*(?:base-|config[._]).*dpi\\.apk")
                                || entry.getSize() < 0 || entry.getSize() > 32L * 1024 * 1024) continue;
                        File split = File.createTempFile("bundle-icon-split-", ".apk", context.getCacheDir());
                        resourceSplits.add(split);
                        try (java.io.InputStream input = zip.getInputStream(entry);
                             java.io.OutputStream output = new java.io.FileOutputStream(split)) {
                            byte[] buffer = new byte[64 * 1024];
                            long bytes = 0;
                            int count;
                            while ((count = input.read(buffer)) != -1) {
                                bytes += count;
                                splitBytes += count;
                                if (bytes > 32L * 1024 * 1024 || splitBytes > 128L * 1024 * 1024
                                        || Thread.currentThread().isInterrupted()) throw new java.io.IOException("Bundle icon limit exceeded.");
                                output.write(buffer, 0, count);
                            }
                        }
                    }
                    if (!resourceSplits.isEmpty()) {
                        android.content.pm.ApplicationInfo app = metadata.packageInfo.applicationInfo;
                        app.splitSourceDirs = resourceSplits.stream().map(File::getAbsolutePath).toArray(String[]::new);
                        app.splitPublicSourceDirs = app.splitSourceDirs;
                        try { metadata = new ApkMetadata(metadata.packageInfo, app.loadLabel(context.getPackageManager()).toString(),
                                app.loadIcon(context.getPackageManager())); }
                        catch (Exception ignored) { }
                    }
                }
                if (metadata != null) {
                    // Detach bitmap pixels before removing the temporary APK resource source.
                    Drawable icon = metadata.icon;
                    if (icon != null) {
                        int size = Math.max(48, Math.min(192, (int) (64 * context.getResources().getDisplayMetrics().density)));
                        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
                        icon.setBounds(0, 0, size, size);
                        icon.draw(new android.graphics.Canvas(bitmap));
                        icon = new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
                    }
                    ApkMetadata result = new ApkMetadata(metadata.packageInfo, metadata.label, icon);
                    CACHE.put(key, result);
                    return result;
                }
                return null;
            } catch (Exception ignored) { return null; }
            finally {
                if (temporary != null) { CACHE.remove(key(temporary)); temporary.delete(); }
                for (File split : resourceSplits) split.delete();
            }
        }
        PackageManager pm = context.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(file.getPath(), 0);
        if (info == null || info.applicationInfo == null) return null;
        info.applicationInfo.sourceDir = file.getPath();
        info.applicationInfo.publicSourceDir = file.getPath();
        String label = file.getName();
        Drawable icon = null;
        try { label = info.applicationInfo.loadLabel(pm).toString(); } catch (Exception ignored) { }
        try {
            Drawable source = info.applicationInfo.loadIcon(pm);
            if (source != null) {
                int size = Math.max(48, Math.min(192, (int) (64 * context.getResources().getDisplayMetrics().density)));
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
                source.setBounds(0, 0, size, size);
                source.draw(new android.graphics.Canvas(bitmap));
                icon = new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
            }
        } catch (Exception | OutOfMemoryError ignored) { }
        ApkMetadata result = new ApkMetadata(info, label, icon);
        CACHE.put(key, result);
        return result;
    }
}
