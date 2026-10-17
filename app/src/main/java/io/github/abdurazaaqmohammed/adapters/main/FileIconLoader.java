package io.github.abdurazaaqmohammed.adapters.main;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Locale;
import io.github.abdurazaaqmohammed.domain.files.FileType;
import io.github.abdurazaaqmohammed.ui.GlyphTileDrawable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import io.github.abdurazaaqmohammed.utils.ApkMetadata;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.UiPrefs;

public class FileIconLoader {

    private static final ExecutorService iconLoaderService = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(48), new ThreadPoolExecutor.DiscardOldestPolicy());
    private static final LruCache<String, Drawable> iconCache = new LruCache<String, Drawable>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Drawable value) {
            int bytes = value instanceof BitmapDrawable
                    ? ((BitmapDrawable) value).getBitmap().getByteCount() : 0;
            // Bound memory as well as entry count when previews vary in size.
            return Math.max(128 * 1024, bytes);
        }
    };

    private static Drawable cachedFolderIcon, cachedApkIcon, cachedImageIcon, cachedVideoIcon,
            cachedDexIcon, cachedArscIcon,
            cachedMusicIcon, cachedArchiveIcon, cachedTextIcon, cachedFileIcon;
    private static int cachedIconTheme = -1;
    private static int cachedIconBucket = -1;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    private final MainActivity context;
    private final boolean isInZip;
    private final java.text.SimpleDateFormat dateFormat;
    private final float dateSize;

    public FileIconLoader(MainActivity context, boolean isInZip) {
        this.context = context;
        this.isInZip = isInZip;
        dateFormat = new java.text.SimpleDateFormat(UiPrefs.getDatePattern(context), Locale.getDefault());
        dateSize = UiPrefs.dateSize(UiPrefs.getScale(context));
        ensureCachedIcons(context);
    }

    public static Drawable getCachedApkIcon() {
        return cachedApkIcon;
    }

    public static void clearPreviewCache() {
        iconCache.evictAll();
    }

    public void setupZipEntryView(ZipEntryInfo zipEntry, ImageView fileIconView, TextView fileDateView) {
        if (zipEntry == null) return;
        fileIconView.setTag(null);
        if (zipEntry.isDirectory()) {
            fileIconView.setImageDrawable(copyIcon(cachedFolderIcon));
        } else {
            setupNonFolderIconView(zipEntry.getFullPath(), fileIconView, null);
        }
        bindDetails(fileDateView, zipEntry.isDirectory(), zipEntry.getLastModified(), zipEntry.getSize());
    }

    public void setupFileView(File file, ImageView fileIconView, TextView fileDateView) {
        // Invalidate thumbnail work from the row's previous file before rebinding.
        fileIconView.setTag(null);
        if (file.isFile()) {
            long modified = file.lastModified(), size = file.length();
            String key = isInZip ? null : file.getAbsolutePath() + ':' + modified + ':' + size;
            setupNonFolderIconView(file.getPath(), fileIconView, key);
            bindDetails(fileDateView, false, modified, size);
        } else {
            fileIconView.setImageDrawable(copyIcon(cachedFolderIcon));
            bindDetails(fileDateView, true, file.lastModified(), 0L);
            if (!isInZip && isAndroidPackageFolder(file)) loadPackageFolderIconAsync(file, fileIconView);
        }
    }

    private void bindDetails(TextView details, boolean directory, long modified, long size) {
        String date = modified > 0L ? dateFormat.format(new java.util.Date(modified)) : "";
        // Some virtual/archive folders have no recorded timestamp. Do not show 1970.
        if (directory && !isInZip) details.setText(date.isEmpty() ? context.getString(R.string.file_date_unavailable) : date);
        else details.setText((date.isEmpty() ? "" : date + " ") + FileSize.getHumanReadableFileSize(size));
        details.setTextSize(dateSize);
        details.setVisibility(View.VISIBLE);
    }

    /** Only package directories immediately inside Android/data, obb, or media. */
    private static boolean isAndroidPackageFolder(File folder) {
        File parent = folder.getParentFile();
        if (parent == null || parent.getParentFile() == null
                || !"Android".equals(parent.getParentFile().getName())) return false;
        String category = parent.getName();
        return ("data".equals(category) || "obb".equals(category) || "media".equals(category))
                && folder.getName().contains(".");
    }

    private void loadPackageFolderIconAsync(File folder, ImageView view) {
        String rowKey = "package-folder:" + folder.getAbsolutePath();
        view.setTag(rowKey);
        java.lang.ref.WeakReference<ImageView> reference = new java.lang.ref.WeakReference<>(view);
        android.content.pm.PackageManager manager = context.getApplicationContext().getPackageManager();
        iconLoaderService.execute(() -> {
            ImageView current = reference.get();
            if (current == null || !rowKey.equals(current.getTag())) return;
            try {
                android.content.pm.PackageInfo info = manager.getPackageInfo(folder.getName(), 0);
                if (info.applicationInfo == null) return;
                String cacheKey = "installed-app:" + folder.getName() + ':' + info.lastUpdateTime
                        + ':' + info.applicationInfo.sourceDir;
                Drawable icon = iconCache.get(cacheKey);
                if (icon == null) {
                    Drawable source = manager.getApplicationIcon(info.applicationInfo);
                    Bitmap pixels = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
                    source.setBounds(0, 0, 192, 192);
                    source.draw(new android.graphics.Canvas(pixels));
                    icon = new BitmapDrawable(android.content.res.Resources.getSystem(), pixels);
                    iconCache.put(cacheKey, icon);
                }
                final Drawable loaded = icon;
                MAIN.post(() -> {
                    ImageView target = reference.get();
                    if (target != null && rowKey.equals(target.getTag())) target.setImageDrawable(new FolderAppBadgeDrawable(
                            copyIcon(cachedFolderIcon, target.getResources()), copyIcon(loaded, target.getResources()),
                            com.google.android.material.color.MaterialColors.getColor(target,
                                    com.google.android.material.R.attr.colorSurface)));
                });
            } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException | OutOfMemoryError ignored) {
                // An uninstalled or inaccessible app keeps the regular folder icon.
            }
        });
    }

    private void setupNonFolderIconView(String path, ImageView fileIconView, String cacheKey) {
        if (cacheKey != null) {
            Drawable cached = iconCache.get(cacheKey);
            if (cached != null) {
                Drawable.ConstantState state = cached.getConstantState();
                fileIconView.setImageDrawable(state == null ? cached : state.newDrawable(context.getResources()).mutate());
                return;
            }
        }

        String ext = FilenameUtils.getExtension(path);
        if (ext != null) ext = "." + ext.toLowerCase(Locale.ROOT);
        else ext = "";

        FileType fileType = FileType.forPath(path);
        if (fileType != null) {
            // A drawable belongs to one ImageView: sharing its bounds between rows
            // causes incorrect rendering when users change the list scale.
            fileIconView.setImageDrawable(new FileTypeDrawable(fileType));
        } else if (".apk".equals(ext) || ".apks".equals(ext) || ".xapk".equals(ext)
                || ".apkm".equals(ext) || ".aspk".equals(ext)) {
            fileIconView.setImageDrawable(copyIcon(cachedApkIcon));
            if (cacheKey != null) loadApkIconAsync(path, fileIconView, cacheKey);
        } else if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
            fileIconView.setImageDrawable(copyIcon(cachedImageIcon));
            if (cacheKey != null) loadThumbnailAsync(path, fileIconView, false, cacheKey);
        } else if (FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
            fileIconView.setImageDrawable(copyIcon(cachedVideoIcon));
            if (cacheKey != null) loadThumbnailAsync(path, fileIconView, true, cacheKey);
        } else if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS)) {
            fileIconView.setImageDrawable(copyIcon(cachedMusicIcon));
        } else if (FileUtils.matchExt(ext, FileUtils.ARCHIVE_EXTS)) {
            fileIconView.setImageDrawable(copyIcon(cachedArchiveIcon));
        } else if(".dex".equals(ext)) {
            fileIconView.setImageDrawable(copyIcon(cachedDexIcon));
        } else if(".arsc".equals(ext)) {
            fileIconView.setImageDrawable(copyIcon(cachedArscIcon));
        } else if (FileUtils.matchExt(ext, FileUtils.TEXT_EXTS)) {
            fileIconView.setImageDrawable(copyIcon(cachedTextIcon));
        } else {
            fileIconView.setImageDrawable(copyIcon(cachedFileIcon));
        }
    }

    private void loadApkIconAsync(String path, ImageView fileIconView, String cacheKey) {
        fileIconView.setTag(cacheKey);
        java.lang.ref.WeakReference<ImageView> reference = new java.lang.ref.WeakReference<>(fileIconView);
        android.content.Context app = context.getApplicationContext();
        iconLoaderService.execute(() -> {
            ImageView current = reference.get();
            if (current == null || !cacheKey.equals(current.getTag())) return;
            try {
                ApkMetadata metadata = ApkMetadata.load(app, new File(path));
                if (metadata != null && metadata.icon != null) {
                    iconCache.put(cacheKey, metadata.icon);
                    MAIN.post(() -> {
                        ImageView target = reference.get();
                        if (target != null && cacheKey.equals(target.getTag())) target.setImageDrawable(metadata.newIcon(target.getResources()));
                    });
                }
            } catch (Exception | OutOfMemoryError ignored) {}
        });
    }

    private void loadThumbnailAsync(String path, ImageView fileIconView, boolean isVideo, String cacheKey) {
        fileIconView.setTag(cacheKey);
        java.lang.ref.WeakReference<ImageView> reference = new java.lang.ref.WeakReference<>(fileIconView);
        Resources resources = context.getApplicationContext().getResources();
        iconLoaderService.execute(() -> {
            ImageView current = reference.get();
            if (current == null || !cacheKey.equals(current.getTag())) return;
            Bitmap bitmap = isVideo ? loadVideoThumbnail(path) : loadImageThumbnail(path);
            if (bitmap == null) return;
            Drawable icon = new BitmapDrawable(resources, bitmap);
            iconCache.put(cacheKey, icon);
            MAIN.post(() -> {
                ImageView target = reference.get();
                if (target != null && cacheKey.equals(target.getTag())) target.setImageDrawable(copyIcon(icon, target.getResources()));
            });
        });
    }

    private static void ensureCachedIcons(MainActivity context) {
        Resources res = context.getResources();
        int theme = context.theme;
        float density = res.getDisplayMetrics().density;
        int bucket = (int) (density * 4);
        if (cachedIconTheme == theme && cachedIconBucket == bucket) return;
        cachedIconTheme = theme;
        cachedIconBucket = bucket;
        cachedFolderIcon  = ResourcesCompat.getDrawable(res, R.drawable.ic_folder_mt, null);
        cachedApkIcon     = badge(context, R.drawable.ic_open_android, 0xFF15930C);
        cachedImageIcon   = badge(context, R.drawable.image_24px, 0xFF008C99);
        cachedVideoIcon   = badge(context, R.drawable.video_24px, 0xFFE45B00);
        cachedMusicIcon   = badge(context, R.drawable.music_24px, 0xFFD60935);
        cachedArscIcon    = badge(context, R.drawable.stacks_24px, 0xFFB87900);
        cachedDexIcon     = badge(context, R.drawable.ic_open_binary, 0xFF008E87);
        cachedArchiveIcon = badge(context, R.drawable.ic_open_archive, 0xFF8A563F);
        cachedTextIcon    = badge(context, R.drawable.baseline_text_snippet_24, 0xFF315CBC);
        cachedFileIcon    = badge(context, R.drawable.baseline_insert_drive_file_24,
                theme == R.style.Theme_MyApp_Light ? 0xFF616161 : 0xFF424242);
    }

    private static Drawable badge(MainActivity context, int glyphId, int bgColor) {
        return new GlyphTileDrawable(context, glyphId, bgColor);
    }

    private Drawable copyIcon(Drawable icon) {
        return copyIcon(icon, context.getResources());
    }

    private static Drawable copyIcon(Drawable icon, Resources resources) {
        Drawable.ConstantState state = icon == null ? null : icon.getConstantState();
        return state == null ? icon : state.newDrawable(resources).mutate();
    }

    private static Bitmap loadImageThumbnail(String path) {
        return io.github.abdurazaaqmohammed.utils.BitmapPreview.decode(path, 192, false);
    }

    private static Bitmap loadVideoThumbnail(String path) {
        try {
            Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
            if (bitmap == null) return null;
            int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (longest <= 192) return bitmap;
            float scale = 192f / longest;
            Bitmap preview = Bitmap.createScaledBitmap(bitmap,
                    Math.max(1, Math.round(bitmap.getWidth() * scale)),
                    Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
            if (preview != bitmap) bitmap.recycle();
            return preview;
        } catch (Throwable t) {
            return null;
        }
    }
}
