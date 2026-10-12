package io.github.abdurazaaqmohammed.adapters.main;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Keeps the familiar folder artwork with a small application badge at the lower corner. */
public final class FolderAppBadgeDrawable extends Drawable {
    private final Drawable folder;
    private final Drawable app;
    private final Paint backdrop = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FolderAppBadgeDrawable(Drawable folder, Drawable app, int surfaceColor) {
        this.folder = folder;
        this.app = app;
        backdrop.setColor(surfaceColor);
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int size = Math.min(bounds.width(), bounds.height());
        if (size <= 0) return;
        int left = bounds.left + (bounds.width() - size) / 2;
        int top = bounds.top + (bounds.height() - size) / 2;
        folder.setBounds(left, top, left + size, top + size);
        folder.draw(canvas);
        int badge = Math.max(1, Math.round(size * .46f));
        int right = left + size, bottom = top + size;
        float cx = right - badge / 2f, cy = bottom - badge / 2f;
        canvas.drawCircle(cx, cy, badge * .56f, backdrop);
        app.setBounds(right - badge, bottom - badge, right, bottom);
        app.draw(canvas);
    }

    @Override public void setAlpha(int alpha) {
        folder.setAlpha(alpha);
        app.setAlpha(alpha);
        backdrop.setAlpha(alpha);
        invalidateSelf();
    }
    @Override public void setColorFilter(ColorFilter filter) {
        folder.setColorFilter(filter);
        app.setColorFilter(filter);
        invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
