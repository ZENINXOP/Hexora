package io.github.abdurazaaqmohammed.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;

/** Colored launcher-style tile with a white, optically centered vector symbol. */
public final class GlyphTileDrawable extends Drawable {
    private final RectF roundBounds = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable glyph;
    private final int color;
    private int alpha = 255;

    public GlyphTileDrawable(Context context, int glyphId, int color) {
        this(AppCompatResources.getDrawable(context, glyphId), color);
    }

    private GlyphTileDrawable(Drawable source, int color) {
        glyph = source == null ? null : source.mutate();
        if (glyph != null) DrawableCompat.setTint(glyph, Color.WHITE);
        this.color = color;
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        float size = Math.min(b.width(), b.height());
        if (size <= 0) return;
        int save = canvas.save();
        canvas.translate(b.left + (b.width() - size) / 2f, b.top + (b.height() - size) / 2f);
        canvas.scale(size / 64f, size / 64f);
        paint.setColor(color); paint.setAlpha(alpha);
        roundRect(canvas, 1, 1, 63, 63, 11, 11, paint);
        paint.setColor(Color.WHITE); paint.setAlpha(alpha * 18 / 255);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1);
        roundRect(canvas, 1.5f, 1.5f, 62.5f, 62.5f, 10.5f, 10.5f, paint);
        paint.setStyle(Paint.Style.FILL);
        if (glyph != null) {
            glyph.setBounds(16, 16, 48, 48);
            glyph.setAlpha(alpha);
            glyph.draw(canvas);
        }
        canvas.restoreToCount(save);
    }

    private void roundRect(Canvas canvas, float left, float top, float right, float bottom,
                           float rx, float ry, Paint paint) {
        roundBounds.set(left, top, right, bottom);
        canvas.drawRoundRect(roundBounds, rx, ry, paint);
    }

    @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) {
        paint.setColorFilter(filter);
        if (glyph != null) glyph.setColorFilter(filter);
        invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    @Override public ConstantState getConstantState() {
        Drawable.ConstantState state = glyph == null ? null : glyph.getConstantState();
        if (state == null) return null;
        return new ConstantState() {
            @Override public Drawable newDrawable() { return new GlyphTileDrawable(state.newDrawable(), color); }
            @Override public Drawable newDrawable(Resources resources) {
                return new GlyphTileDrawable(state.newDrawable(resources), color);
            }
            @Override public int getChangingConfigurations() { return state.getChangingConfigurations(); }
        };
    }
}
