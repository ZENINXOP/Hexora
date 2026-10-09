package io.github.abdurazaaqmohammed.listeners;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;

/** Native tap/long-press handling with horizontal swipe selection. */
public class SwipeTouchListener implements View.OnTouchListener {
    private final MainActivity context;
    private final View.OnClickListener clickListener;
    private final View.OnLongClickListener longClickListener;
    private final Object adapter;
    private final int position, pane;
    private final float swipeSlop, swipeConfirm;
    private float initialX, initialY;
    private boolean swiping, directionDecided;

    public SwipeTouchListener(MainActivity context, View.OnClickListener clickListener,
                              View.OnLongClickListener longClickListener, int position,
                              Object adapter, int pane) {
        this.context = context;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        this.position = position;
        this.adapter = adapter;
        this.pane = pane;
        float density = context.getResources().getDisplayMetrics().density;
        swipeSlop = Math.max(ViewConfiguration.get(context).getScaledTouchSlop(), 12 * density);
        swipeConfirm = 60 * density;
    }

    public void attachTo(View view) {
        view.setOnClickListener(v -> {
            context.setCurrentPane(pane);
            if (adapter instanceof MainFilesArrayAdapter && ((MainFilesArrayAdapter) adapter).isMultiSelectMode()) {
                ((MainFilesArrayAdapter) adapter).handleMultiSelect(position);
            } else {
                clickListener.onClick(v);
            }
        });
        view.setOnLongClickListener(v -> {
            context.setCurrentPane(pane);
            return longClickListener.onLongClick(v);
        });
        view.setOnTouchListener(this);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                view.animate().cancel();
                view.setTranslationX(0);
                swiping = false;
                directionDecided = false;
                initialX = event.getRawX();
                initialY = event.getRawY();
                context.setSelectedPane(pane);
                context.onPaneTouched(pane);
                // Let View dispatch its normal pressed state, click and long press.
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - initialX;
                float dy = event.getRawY() - initialY;
                if (!directionDecided && (Math.abs(dx) > swipeSlop || Math.abs(dy) > swipeSlop)) {
                    directionDecided = true;
                    swiping = Math.abs(dx) > Math.abs(dy);
                    if (swiping) {
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                        MotionEvent cancel = MotionEvent.obtain(event);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        view.onTouchEvent(cancel);
                        cancel.recycle();
                    }
                }
                if (swiping) {
                    float distance = Math.abs(dx);
                    float translated = distance <= swipeConfirm ? distance
                            : swipeConfirm + (float) Math.sqrt((distance - swipeConfirm) * swipeConfirm * 0.5f);
                    view.setTranslationX(Math.copySign(translated, dx));
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!swiping) return false;
                boolean confirmed = event.getActionMasked() == MotionEvent.ACTION_UP
                        && Math.abs(view.getTranslationX()) >= swipeConfirm * 0.75f;
                view.getParent().requestDisallowInterceptTouchEvent(false);
                view.setPressed(false);
                view.animate().translationX(0).setDuration(140)
                        .setInterpolator(new DecelerateInterpolator()).start();
                swiping = false;
                if (confirmed && adapter instanceof MainFilesArrayAdapter) {
                    context.setCurrentPane(pane);
                    ((MainFilesArrayAdapter) adapter).handleSwipe(position);
                }
                return true;
            default:
                return false;
        }
    }
}
