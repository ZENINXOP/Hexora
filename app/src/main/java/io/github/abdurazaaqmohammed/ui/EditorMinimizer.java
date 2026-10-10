package io.github.abdurazaaqmohammed.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.inputmethod.InputMethodManager;

import java.lang.ref.WeakReference;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;

/** Keeps the most recently minimized editor on its existing activity stack. */
public final class EditorMinimizer {
    public interface SessionOwner {
        void onEditorMinimized();
    }
    public static final String ACTION_RESUME = "app.hexora.manager.RESUME_EDITOR";
    private static WeakReference<Activity> minimized = new WeakReference<>(null);

    private EditorMinimizer() { }

    public static void minimize(Activity editor) {
        if (editor instanceof SessionOwner) ((SessionOwner) editor).onEditorMinimized();
        InputMethodManager keyboard = (InputMethodManager) editor.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(editor.getWindow().getDecorView().getWindowToken(), 0);
        }
        minimized = new WeakReference<>(editor);
        // Reordering keeps both activities, open tabs and unsaved buffers alive.
        editor.startActivity(new Intent(editor, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    public static boolean hasMinimizedEditor() {
        Activity editor = minimized.get();
        return editor != null && !editor.isFinishing() && !editor.isDestroyed();
    }

    public static void resume(Activity manager) {
        Activity editor = minimized.get();
        if (!hasMinimizedEditor() || editor == null) return;
        manager.startActivity(new Intent(manager, editor.getClass())
                .setAction(ACTION_RESUME)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    public static void onEditorResumed(Activity editor) {
        Activity pending = minimized.get();
        if (pending != null && pending.getClass() == editor.getClass()) minimized.clear();
    }

    public static void onEditorClosed(Activity editor) {
        if (minimized.get() == editor) minimized.clear();
    }
}
