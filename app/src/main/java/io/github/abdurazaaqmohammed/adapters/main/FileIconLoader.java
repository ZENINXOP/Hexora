package io.github.abdurazaaqmohammed.adapters.main;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Locale;
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
            cachedMusicIcon, cachedArchiveIcon, cachedPdfIcon, cachedTextIcon, cachedFileIcon;
    private static int cachedIconTheme = -1;
    private static int cachedIconBucket = -1;

    private final MainActivity context;
    private final boolean isInZip;

    public FileIconLoader(MainActivity context, boolean isInZip) {
        this.context = context;
        this.isInZip = isInZip;
        ensureCachedIcons(context.getResources(), context.theme);
    }

    public static Drawable getCachedApkIcon() {
        return cachedApkIcon;
    }

    public void setupZipEntryView(ZipEntryInfo zipEntry, ImageView fileIconView, TextView fileDateView) {
        if (zipEntry == null) return;
        if (zipEntry.isDirectory()) {
            fileIconView.setImageDrawable(cachedFolderIcon);
            fileDateView.setVisibility(View.INVISIBLE);
        } else {
            setupNonFolderIconView(zipEntry.getFullPath(), fileIconView);
            fileDateView.setVisibility(View.VISIBLE);
            fileDateView.setTextSize(UiPrefs.dateSize(UiPrefs.getScale(context)));
            fileDateView.setText(new StringBuilder(UiPrefs.formatDate(context, zipEntry.getLastModified())).append(' ').append(FileSize.getHumanReadableFileSize(zipEntry.getSize())));
        }
    }

    public void setupFileView(File file, ImageView fileIconView, TextView fileDateView) {
        if (file.isFile()) {
            fileDateView.setVisibility(View.VISIBLE);
            fileDateView.setTextSize(UiPrefs.dateSize(UiPrefs.getScale(context)));
            setupNonFolderIconView(file.getPath(), fileIconView);
            fileDateView.setText(new StringBuilder(UiPrefs.formatDate(context, file.lastModified())).append(' ').append(FileSize.getHumanReadableFileSize(file.length())));
        } else {
            fileIconView.setImageDrawable(cachedFolderIcon);
            fileDateView.setVisibility(View.INVISIBLE);
        }
    }

    private void setupNonFolderIconView(String path, ImageView fileIconView) {
        if (!isInZip) {
            Drawable cached = iconCache.get(ApkMetadata.key(new File(path)));
            if (cached != null) {
                Drawable.ConstantState state = cached.getConstantState();
                fileIconView.setImageDrawable(state == null ? cached : state.newDrawable(context.getResources()).mutate());
                return;
            }
        }

        String ext = FilenameUtils.getExtension(path);
        if (ext != null) ext = "." + ext.toLowerCase(Locale.ROOT);
        else ext = "";

        if (".apk".equals(ext)) {
            fileIconView.setImageDrawable(cachedApkIcon);
            if (!isInZip) loadApkIconAsync(path, fileIconView);
        } else if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
            fileIconView.setImageDrawable(cachedImageIcon);
            if (!isInZip) loadThumbnailAsync(path, fileIconView, false);
        } else if (FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
            fileIconView.setImageDrawable(cachedVideoIcon);
            if (!isInZip) loadThumbnailAsync(path, fileIconView, true);
        } else if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS)) {
            fileIconView.setImageDrawable(cachedMusicIcon);
        } else if (FileUtils.matchExt(ext, FileUtils.ARCHIVE_EXTS)) {
            fileIconView.setImageDrawable(cachedArchiveIcon);
        } else if (".pdf".equals(ext)) {
            fileIconView.setImageDrawable(cachedPdfIcon);
        } else if(".dex".equals(ext)) {
            fileIconView.setImageDrawable(cachedDexIcon);
        } else if(".arsc".equals(ext)) {
            fileIconView.setImageDrawable(cachedArscIcon);
        } else if (FileUtils.matchExt(ext, FileUtils.TEXT_EXTS)) {
            fileIconView.setImageDrawable(cachedTextIcon);
        } else {
            fileIconView.setImageDrawable(cachedFileIcon);
        }
    }

    private void loadApkIconAsync(String path, ImageView fileIconView) {
        String cacheKey = ApkMetadata.key(new File(path));
        fileIconView.setTag(cacheKey);
        iconLoaderService.execute(() -> {
            if (!cacheKey.equals(fileIconView.getTag())) return;
            try {
                ApkMetadata metadata = ApkMetadata.load(context.getApplicationContext(), new File(path));
                if (metadata != null && metadata.icon != null) {
                    iconCache.put(cacheKey, metadata.icon);
                    context.runOnUiThread(() -> {
                        if (cacheKey.equals(fileIconView.getTag())) fileIconView.setImageDrawable(metadata.newIcon(context.getResources()));
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    private void loadThumbnailAsync(String path, ImageView fileIconView, boolean isVideo) {
        String cacheKey = ApkMetadata.key(new File(path));
        fileIconView.setTag(cacheKey);
        iconLoaderService.execute(() -> {
            if (!cacheKey.equals(fileIconView.getTag())) return;
            Bitmap bitmap = isVideo ? loadVideoThumbnail(path) : loadImageThumbnail(path);
            if (bitmap == null || !cacheKey.equals(fileIconView.getTag())) return;
            Drawable icon = new BitmapDrawable(context.getResources(), bitmap);
            iconCache.put(cacheKey, icon);
            context.runOnUiThread(() -> {
                if (cacheKey.equals(fileIconView.getTag())) fileIconView.setImageDrawable(icon);
            });
        });
    }

    private static void ensureCachedIcons(Resources res, int theme) {
        float density = res.getDisplayMetrics().density;
        int bucket = (int) (density * 4);
        if (cachedIconTheme == theme && cachedIconBucket == bucket) return;
        cachedIconTheme = theme;
        cachedIconBucket = bucket;
        cachedFolderIcon  = ResourcesCompat.getDrawable(res, R.drawable.ic_folder_mt, null);
        cachedApkIcon     = badge(res, density, R.drawable.apk_document_24px, 0xFF33866D, true);
        cachedImageIcon   = badge(res, density, R.drawable.image_24px, 0xFF8665B8, true);
        cachedVideoIcon   = badge(res, density, R.drawable.video_24px, 0xFFD26757, true);
        cachedMusicIcon   = badge(res, density, R.drawable.music_24px, 0xFF438E9A, true);
        cachedArscIcon    = badge(res, density, R.drawable.stacks_24px, 0xFFAE843C, true);
        cachedDexIcon     = badge(res, density, R.drawable.code_24px, 0xFF5987AA, true);
        cachedArchiveIcon = badge(res, density, R.drawable.baseline_folder_zip_24, 0xFFC48C43, true);
        cachedPdfIcon     = badge(res, density, R.drawable.pdf_24px, 0xFFC15F72, true);
        cachedTextIcon    = badge(res, density, R.drawable.baseline_text_snippet_24, 0xFF4D7CB8, true);
        cachedFileIcon    = badge(res, density, R.drawable.baseline_insert_drive_file_24,
                theme == R.style.Theme_MyApp_Light ? 0xFF616161 : 0xFF424242, true);
    }

    private static Drawable badge(Resources res, float density, int glyphId, int bgColor, boolean whiteGlyph) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(7 * density);
        bg.setColor(bgColor);
        Drawable glyph = ResourcesCompat.getDrawable(res, glyphId, null);
        if (glyph != null) {
            glyph = glyph.mutate();
            if (whiteGlyph) DrawableCompat.setTint(glyph, Color.WHITE);
        } else {
            glyph = new ColorDrawable(Color.TRANSPARENT);
        }
        LayerDrawable layer = new LayerDrawable(
                new Drawable[]{bg, glyph});
        int inset = (int) (5 * density + 0.5f);
        layer.setLayerInset(1, inset, inset, inset, inset);
        return layer;
    }

    private static Bitmap loadImageThumbnail(String path) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            int width = options.outWidth;
            int height = options.outHeight;
            int scale = 1;
            while (Math.max(width, height) / 2 >= 192) {
                width /= 2;
                height /= 2;
                scale *= 2;
            }

            options.inSampleSize = scale;
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(path, options);
        } catch (Throwable t) {
            return null;
        }
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
