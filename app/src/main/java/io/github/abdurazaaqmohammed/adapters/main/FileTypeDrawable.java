package io.github.abdurazaaqmohammed.adapters.main;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import io.github.abdurazaaqmohammed.domain.files.FileType;

/** Colored rounded tiles with white document marks; no bitmap decoding while scrolling. */
public final class FileTypeDrawable extends Drawable {
    private final RectF roundBounds = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path page = new Path();
    private final FileType type;
    private int opacity = 255;

    public FileTypeDrawable(FileType type) { this.type = type; }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float size = Math.min(bounds.width(), bounds.height());
        if (size <= 0) return;
        int save = canvas.save();
        canvas.translate(bounds.left + (bounds.width() - size) / 2f, bounds.top + (bounds.height() - size) / 2f);
        canvas.scale(size / 32f, size / 32f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(type.color); paint.setAlpha(opacity);
        roundRect(canvas, 0.5f, 0.5f, 31.5f, 31.5f, 5.5f, 5.5f, paint);
        paint.setColor(Color.WHITE); paint.setAlpha(opacity * 18 / 255);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(0.5f);
        roundRect(canvas, 0.75f, 0.75f, 31.25f, 31.25f, 5.25f, 5.25f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE); paint.setAlpha(opacity);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        if (type == FileType.JAVA) {
            roundRect(canvas, 9, 13, 21, 22, 1.5f, 1.5f, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
            roundRect(canvas, 19, 14, 25, 19, 2, 2, paint);
            canvas.drawLine(11, 24, 23, 24, paint);
            canvas.drawLine(12, 7, 12, 10, paint);
            canvas.drawLine(16, 6, 16, 10, paint);
            canvas.drawLine(20, 7, 20, 10, paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextSize(4.5f);
            canvas.drawText("JAVA", 16, 29, paint);
        } else if (type == FileType.FONT) {
            paint.setTextSize(18);
            canvas.drawText("Tt", 16, 22, paint);
        } else {
            page.reset();
            page.moveTo(9, 6); page.lineTo(18, 6); page.lineTo(24, 12);
            page.lineTo(24, 25); page.quadTo(24, 26, 23, 26);
            page.lineTo(9, 26); page.quadTo(8, 26, 8, 25);
            page.lineTo(8, 7); page.quadTo(8, 6, 9, 6); page.close();
            canvas.drawPath(page, paint);
            paint.setColor(type.color);
            page.reset(); page.moveTo(18, 7.5f); page.lineTo(18, 12); page.lineTo(22.5f, 12); page.close();
            canvas.drawPath(page, paint);
            if (type == FileType.TEXT) {
                paint.setStrokeWidth(1.5f);
                canvas.drawLine(11, 16, 21, 16, paint);
                canvas.drawLine(11, 19, 21, 19, paint);
                canvas.drawLine(11, 22, 18, 22, paint);
            } else {
                String label = type == FileType.XML ? "<>" : type == FileType.BINARY ? "01" : type.label;
                paint.setTextSize(label.length() > 4 ? 4.8f : label.length() > 3 ? 5.7f : label.length() > 2 ? 7f : 9.5f);
                canvas.drawText(label, 16, 22, paint);
            }
        }
        canvas.restoreToCount(save);
    }

    private void roundRect(Canvas canvas, float left, float top, float right, float bottom,
                           float rx, float ry, Paint paint) {
        roundBounds.set(left, top, right, bottom);
        canvas.drawRoundRect(roundBounds, rx, ry, paint);
    }

    @Override public void setAlpha(int alpha) { opacity = alpha; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
