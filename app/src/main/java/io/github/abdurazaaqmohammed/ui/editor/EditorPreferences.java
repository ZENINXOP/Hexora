package io.github.abdurazaaqmohammed.ui.editor;

import android.content.Context;
import android.content.SharedPreferences;

/** One preference store and bounded values for both text and DEX editors. */
public final class EditorPreferences {
    private EditorPreferences() { }

    public static SharedPreferences get(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("editor_prefs", Context.MODE_PRIVATE);
        synchronized (EditorPreferences.class) {
            if (!prefs.getBoolean("preferences_v2", false)) {
                SharedPreferences.Editor edit = prefs.edit();
                copy(prefs, edit, "pref_font_size", "font_size");
                copy(prefs, edit, "pref_show_line_numbers", "show_line_numbers");
                copy(prefs, edit, "pref_word_wrap", "wrap_text");
                edit.putBoolean("preferences_v2", true).apply();
            }
        }
        return prefs;
    }

    private static void copy(SharedPreferences prefs, SharedPreferences.Editor edit, String old, String current) {
        Object value = prefs.getAll().get(old);
        if (value instanceof String text) edit.putString(current, text);
        else if (value instanceof Boolean enabled) edit.putBoolean(current, enabled);
    }

    public static int number(SharedPreferences prefs, String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(String.valueOf(prefs.getAll().get(key)));
            return value >= min && value <= max ? value : fallback;
        } catch (RuntimeException invalid) { return fallback; }
    }

    public static void normalize(SharedPreferences prefs) {
        int size = number(prefs, "font_size", 14, 8, 30);
        if ((size & 1) != 0) size = 14; // Must be one of the font-size list entries.
        prefs.edit().putString("font_size", String.valueOf(size))
                .putString("pref_tab_size", String.valueOf(number(prefs, "pref_tab_size", 4, 1, 16)))
                .putString("font_type", "monospace".equals(prefs.getAll().get("font_type")) ? "monospace" : "normal")
                .apply();
    }
}
