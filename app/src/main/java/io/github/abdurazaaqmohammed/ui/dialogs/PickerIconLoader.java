package io.github.abdurazaaqmohammed.ui.dialogs;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.FileTypeDrawable;
import io.github.abdurazaaqmohammed.domain.files.FileType;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.FileUtils;

public class PickerIconLoader {

    private static final ExecutorService iconLoaderService = new java.util.concurrent.ThreadPoolExecutor(
            2, 2, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(32),
            new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    private static final LruCache<String, Drawable> iconCache = new LruCache<String, Drawable>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Drawable icon) {
            return Math.max(128 * 1024, icon instanceof BitmapDrawable
                    ? ((BitmapDrawable) icon).getBitmap().getAllocationByteCount() : 0);
        }
    };

    private static Drawable cachedFolderIcon, cachedApkIcon, cachedImageIcon, cachedVideoIcon,
            cachedMusicIcon, cachedArchiveIcon, cachedTextIcon, cachedFileIcon;
    private static boolean cachedNight;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yy-MM-dd HH:mm", Locale.US);

    public PickerIconLoader(Context context) {
        this.context = context.getApplicationContext();
        ensureCachedIcons(context.getResources());
    }

    public void setupFileView(File file, ImageView fileIconView, TextView fileDateView) {
        // A completed thumbnail for the previous file must not replace this icon.
        fileIconView.setTag(null);
        if (file.isFile()) {
            fileDateView.setVisibility(View.VISIBLE);
            String formattedDate = dateFormat.format(new Date(file.lastModified()));
            setupNonFolderIconView(file.getPath(), fileIconView);
            fileDateView.setText(formattedDate + ' ' + FileSize.getHumanReadableFileSize(file.length()));
        } else {
            fileIconView.setImageDrawable(copy(cachedFolderIcon));
            fileDateView.setVisibility(View.INVISIBLE);
        }
    }

    private void setupNonFolderIconView(String path, ImageView fileIconView) {
        Drawable cached = iconCache.get(io.github.abdurazaaqmohammed.utils.ApkMetadata.key(new File(path)));
        if (cached != null) {
            fileIconView.setImageDrawable(copy(cached));
            return;
        }
        String ext = FilenameUtils.getExtension(path);
        ext = ext == null ? "" : "." + ext.toLowerCase(Locale.ROOT);

        if (".apk".equals(ext)) {
            fileIconView.setImageDrawable(copy(cachedApkIcon));
            loadApkIconAsync(path, fileIconView);
        } else if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
            fileIconView.setImageDrawable(copy(cachedImageIcon));
            loadThumbnailAsync(path, fileIconView, false);
        } else if (FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
            fileIconView.setImageDrawable(copy(cachedVideoIcon));
            loadThumbnailAsync(path, fileIconView, true);
        } else if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS)) {
            fileIconView.setImageDrawable(copy(cachedMusicIcon));
        } else if (FileUtils.matchExt(ext, FileUtils.ARCHIVE_EXTS)) {
            fileIconView.setImageDrawable(copy(cachedArchiveIcon));
        } else if (".pdf".equals(ext)) {
            fileIconView.setImageDrawable(new FileTypeDrawable(FileType.PDF));
        } else if (FileUtils.matchExt(ext, FileUtils.TEXT_EXTS)) {
            fileIconView.setImageDrawable(copy(cachedTextIcon));
        } else {
            fileIconView.setImageDrawable(copy(cachedFileIcon));
        }
    }

    private void loadApkIconAsync(String path, ImageView fileIconView) {
        String key = io.github.abdurazaaqmohammed.utils.ApkMetadata.key(new File(path));
        fileIconView.setTag(key);
        java.lang.ref.WeakReference<ImageView> reference = new java.lang.ref.WeakReference<>(fileIconView);
        iconLoaderService.execute(() -> {
            ImageView current = reference.get();
            if (current == null || !key.equals(current.getTag())) return;
            try {
                io.github.abdurazaaqmohammed.utils.ApkMetadata metadata =
                        io.github.abdurazaaqmohammed.utils.ApkMetadata.load(context, new File(path));
                if (metadata != null) {
                    Drawable icon = metadata.icon;
                    if (icon != null) {
                        iconCache.put(key, icon);
                        handler.post(() -> {
                            ImageView target = reference.get();
                            if (target != null && key.equals(target.getTag())) target.setImageDrawable(copy(icon));
                        });
                    }
                }
            } catch (Exception | OutOfMemoryError ignored) {
            }
        });
    }

    private void loadThumbnailAsync(String path, ImageView fileIconView, boolean isVideo) {
        String key = io.github.abdurazaaqmohammed.utils.ApkMetadata.key(new File(path));
        fileIconView.setTag(key);
        java.lang.ref.WeakReference<ImageView> reference = new java.lang.ref.WeakReference<>(fileIconView);
        iconLoaderService.execute(() -> {
            ImageView current = reference.get();
            if (current == null || !key.equals(current.getTag())) return;
            Bitmap bitmap = isVideo ? loadVideoThumbnail(path) : loadImageThumbnail(path);
            if (bitmap == null) return;
            Drawable icon = new BitmapDrawable(context.getResources(), bitmap);
            iconCache.put(key, icon);
            handler.post(() -> {
                ImageView target = reference.get();
                if (target != null && key.equals(target.getTag())) target.setImageDrawable(copy(icon));
            });
        });
    }

    private static void ensureCachedIcons(Resources res) {
        boolean night = (res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (cachedNight == night && cachedFolderIcon != null) return;
        cachedNight = night;
        int color = night ? Color.WHITE : Color.BLACK;
        cachedFolderIcon  = tintAndCache(res, R.drawable.folder_24px, color);
        cachedApkIcon     = tintAndCache(res, R.drawable.apk_document_24px, color);
        cachedImageIcon   = tintAndCache(res, R.drawable.image_24px, color);
        cachedVideoIcon   = tintAndCache(res, R.drawable.video_24px, color);
        cachedMusicIcon   = tintAndCache(res, R.drawable.music_24px, color);
        cachedArchiveIcon = tintAndCache(res, R.drawable.baseline_folder_zip_24, color);
        cachedTextIcon    = tintAndCache(res, R.drawable.baseline_text_snippet_24, color);
        cachedFileIcon    = tintAndCache(res, R.drawable.baseline_insert_drive_file_24, color);
    }

    private static Drawable tintAndCache(Resources res, int id, int color) {
        Drawable d = ResourcesCompat.getDrawable(res, id, null);
        if (d == null) return null;
        d = d.mutate();
        DrawableCompat.setTint(d, color);
        return d;
    }

    private static Bitmap loadImageThumbnail(String path) {
        return io.github.abdurazaaqmohammed.utils.BitmapPreview.decode(path, 192, false);
    }

    public static void clearPreviewCache() {
        iconCache.evictAll();
    }

    private static Drawable copy(Drawable icon) {
        Drawable.ConstantState state = icon == null ? null : icon.getConstantState();
        return state == null ? icon : state.newDrawable().mutate();
    }

    private static Bitmap loadVideoThumbnail(String path) {
        try {
            return ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
        } catch (Throwable t) {
            return null;
        }
    }
}
