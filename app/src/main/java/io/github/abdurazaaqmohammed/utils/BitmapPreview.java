package io.github.abdurazaaqmohammed.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/** Bounded preview decoding, including very wide/tall images. OpenAI Codex assisted. */
public final class BitmapPreview {
    private BitmapPreview() {}

    public static int sampleSize(int width, int height, int maxEdge) {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1;
        int sample = 1;
        int longest = Math.max(width, height);
        while ((longest + (long) sample - 1) / sample > maxEdge && sample < (1 << 30)) sample *= 2;
        return sample;
    }

    /** Call from a worker. RGB_565 is optional because thumbnails may have alpha. */
    public static Bitmap decode(String path, int maxEdge, boolean opaque) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            if (options.outWidth <= 0 || options.outHeight <= 0 || Thread.currentThread().isInterrupted()) return null;
            options.inSampleSize = sampleSize(options.outWidth, options.outHeight, maxEdge);
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = opaque ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(path, options);
        } catch (RuntimeException | OutOfMemoryError error) {
            return null;
        }
    }
}
