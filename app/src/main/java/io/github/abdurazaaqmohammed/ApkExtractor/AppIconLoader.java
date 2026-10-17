package io.github.abdurazaaqmohammed.ApkExtractor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.lang.ref.WeakReference;

/** Visible-row icons only; never retain one full drawable per installed app. OpenAI Codex assisted. */
public final class AppIconLoader {
    private AppIconLoader() {}
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.ExecutorService WORKERS = new java.util.concurrent.ThreadPoolExecutor(
            2, 2, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(32),
            new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    public static void clearCache() { CACHE.evictAll(); }

    public static void load(Context context, AppInfo info, ImageView view) {
        String key = info.packageName + ':' + info.lastUpdate;
        Object token = new Object();
        view.setTag(token);
        Bitmap cached = CACHE.get(key);
        if (cached != null) { view.setImageBitmap(cached); return; }
        view.setImageResource(android.R.drawable.sym_def_app_icon);
        Context app = context.getApplicationContext();
        WeakReference<ImageView> reference = new WeakReference<>(view);
        WORKERS.submit(() -> {
            ImageView current = reference.get();
            if (current == null || current.getTag() != token) return;
            try {
                Drawable source = info.appInfo == null ? app.getPackageManager().getApplicationIcon(info.packageName)
                        : info.appInfo.loadIcon(app.getPackageManager());
                Bitmap bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
                source.setBounds(0, 0, 128, 128);
                source.draw(new Canvas(bitmap));
                CACHE.put(key, bitmap);
                MAIN.post(() -> {
                    ImageView target = reference.get();
                    if (target != null && target.getTag() == token) target.setImageBitmap(bitmap);
                });
            } catch (Exception | OutOfMemoryError ignored) { }
        });
    }
}
