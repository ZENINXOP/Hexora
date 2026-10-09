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

    /** Call on a worker thread. Never holds the cache lock while parsing an APK. */
    public static ApkMetadata load(Context context, File file) {
        String key = key(file);
        ApkMetadata cached = CACHE.get(key);
        if (cached != null) return cached;
        PackageManager pm = context.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(file.getPath(), 0);
        if (info == null || info.applicationInfo == null) return null;
        info.applicationInfo.sourceDir = file.getPath();
        info.applicationInfo.publicSourceDir = file.getPath();
        String label = file.getName();
        Drawable icon = null;
        try { label = info.applicationInfo.loadLabel(pm).toString(); } catch (Exception ignored) { }
        try { icon = info.applicationInfo.loadIcon(pm); } catch (Exception ignored) { }
        ApkMetadata result = new ApkMetadata(info, label, icon);
        CACHE.put(key, result);
        return result;
    }
}
