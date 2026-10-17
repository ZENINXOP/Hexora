package io.github.abdurazaaqmohammed.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;

/** Shared bounded artwork reads for the service, player and mini-player. OpenAI Codex assisted. */
public final class MediaArtwork {
    private MediaArtwork() {}
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.ExecutorService WORKERS = new java.util.concurrent.ThreadPoolExecutor(
            2, 2, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(16),
            new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private static final ConcurrentHashMap<String, FutureTask<Bitmap>> LOADING = new ConcurrentHashMap<>();

    public static void clearCache() { CACHE.evictAll(); }

    public static void loadInto(Context context, MediaItem item, ImageView view) {
        Object token = new Object();
        view.setTag(token);
        view.setImageResource(android.R.drawable.ic_menu_gallery);
        Context app = context.getApplicationContext();
        WeakReference<ImageView> reference = new WeakReference<>(view);
        WORKERS.submit(() -> {
            ImageView current = reference.get();
            if (current == null || current.getTag() != token) return;
            Bitmap artwork = read(app, item);
            if (artwork == null) return;
            MAIN.post(() -> {
                ImageView target = reference.get();
                if (target != null && target.getTag() == token) target.setImageBitmap(artwork);
            });
        });
    }

    /** Worker-only. Concurrent requests share one retriever and one bitmap. */
    public static Bitmap read(Context context, MediaItem item) {
        if (item == null || item.uri == null || Thread.currentThread().isInterrupted()) return null;
        String key = item.uri.toString();
        if (item.path != null) {
            File file = new File(item.path);
            key += ':' + Long.toString(file.lastModified()) + ':' + file.length();
        }
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;
        FutureTask<Bitmap> created = new FutureTask<>(() -> decode(context, item));
        FutureTask<Bitmap> task = LOADING.putIfAbsent(key, created);
        if (task == null) task = created;
        try {
            task.run();
            Bitmap bitmap = task.get();
            if (bitmap != null) CACHE.put(key, bitmap);
            return bitmap;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return null;
        } catch (java.util.concurrent.ExecutionException error) {
            return null;
        } finally { if (task.isDone()) LOADING.remove(key, task); }
    }

    private static Bitmap decode(Context context, MediaItem item) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, item.uri);
            byte[] bytes = retriever.getEmbeddedPicture();
            if (bytes == null || bytes.length > 8 * 1024 * 1024 || Thread.currentThread().isInterrupted()) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            options.inSampleSize = io.github.abdurazaaqmohammed.utils.BitmapPreview.sampleSize(options.outWidth, options.outHeight, 512);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (Exception | OutOfMemoryError ignored) {
            return null;
        } finally { if (retriever != null) try { retriever.release(); } catch (Exception ignored) {} }
    }
}
