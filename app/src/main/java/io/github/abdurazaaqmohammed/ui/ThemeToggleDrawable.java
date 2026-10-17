package io.github.abdurazaaqmohammed.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.animation.AccelerateDecelerateInterpolator;

/** A theme-colored sun that morphs into a crescent on tap.
 * AI-assisted contribution: OpenAI Codex. */
public final class ThemeToggleDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path body = new Path();
    private final Path cutout = new Path();
    private final int color;
    private float progress;
    private int alpha = 255;
    private ValueAnimator animator;

    public ThemeToggleDrawable(boolean darkIcon, int color) {
        this.color = color;
        progress = darkIcon ? 1f : 0f;
    }

    public void setDarkIcon(boolean darkIcon) {
        stopAnimation();
        progress = darkIcon ? 1f : 0f;
        invalidateSelf();
    }

    public void animateTo(boolean darkIcon, Runnable finished) {
        stopAnimation();
        ValueAnimator next = ValueAnimator.ofFloat(progress, darkIcon ? 1f : 0f);
        animator = next;
        // ValueAnimator honors the system animation-duration scale, including zero.
        next.setDuration(260L);
        next.setInterpolator(new AccelerateDecelerateInterpolator());
        next.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidateSelf();
        });
        next.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (animator == next) animator = null;
                if (!cancelled && finished != null) finished.run();
            }
        });
        next.start();
    }

    public void stopAnimation() {
        ValueAnimator running = animator;
        animator = null;
        if (running != null) running.cancel();
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float size = Math.min(bounds.width(), bounds.height());
        if (size <= 0f) return;
        int saved = canvas.save();
        canvas.translate(bounds.left + (bounds.width() - size) / 2f,
                bounds.top + (bounds.height() - size) / 2f);
        canvas.scale(size / 24f, size / 24f);
        canvas.rotate(progress * -30f, 12f, 12f);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAlpha(Math.round(alpha * (1f - progress)));
        if (progress < 1f) {
            for (int index = 0; index < 8; index++) {
                double angle = Math.toRadians(index * 45f + progress * 90f);
                float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
                canvas.drawLine(12f + x * 7.5f, 12f + y * 7.5f,
                        12f + x * 9.5f, 12f + y * 9.5f, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(alpha);
        body.reset();
        body.addCircle(12f, 12f, 4.2f + progress * 3f, Path.Direction.CW);
        if (progress > 0f) {
            cutout.reset();
            cutout.addCircle(15.3f, 8.5f, progress * 6.5f, Path.Direction.CW);
            body.op(cutout, Path.Op.DIFFERENCE);
        }
        canvas.drawPath(body, paint);
        canvas.restoreToCount(saved);
    }

    @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 24; }
    @Override public int getIntrinsicHeight() { return 24; }
}
