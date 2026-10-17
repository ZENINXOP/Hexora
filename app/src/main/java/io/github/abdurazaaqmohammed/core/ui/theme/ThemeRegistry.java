package io.github.abdurazaaqmohammed.core.ui.theme;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.EditorMinimizer;
import io.github.abdurazaaqmohammed.utils.ProgressManager;

/** One persisted Light/Dark choice shared by every screen.
 * AI-assisted contribution: OpenAI Codex. */
public final class ThemeRegistry {
    public static final String PREF_KEY = "theme_plugin_id";
    private static final Map<String, ThemePlugin> PLUGINS = new LinkedHashMap<>();
    private static boolean builtInsLoaded;
    private ThemeRegistry() { }

    private static boolean supported(String id) {
        return BuiltInThemes.LIGHT_ID.equals(id) || BuiltInThemes.DARK_ID.equals(id);
    }

    public static synchronized void register(ThemePlugin plugin) {
        ensureBuiltIns();
        // Keep the compatibility API, while offering only the two bundled palettes.
        if (plugin != null && supported(plugin.id()) && !PLUGINS.containsKey(plugin.id())) {
            PLUGINS.put(plugin.id(), plugin);
        }
    }

    public static synchronized void unregister(String id) {
        if (!supported(id)) PLUGINS.remove(id);
    }

    public static synchronized List<ThemePlugin> getAll() {
        ensureBuiltIns();
        return new ArrayList<>(PLUGINS.values());
    }

    public static synchronized ThemePlugin get(String id) {
        ensureBuiltIns();
        return PLUGINS.get(id);
    }

    private static String systemChoice() {
        return (Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES ? BuiltInThemes.DARK_ID : BuiltInThemes.LIGHT_ID;
    }

    /** Resolve old System/Black choices once, then persist an explicit Light or Dark. */
    public static String getCurrentId(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String id = prefs.getString(PREF_KEY, null);
        if (!supported(id)) {
            if (BuiltInThemes.BLACK_ID.equals(id)) id = BuiltInThemes.DARK_ID;
            else if (id == null && !prefs.getBoolean("systemTheme", true)) {
                int legacy = prefs.getInt("theme", 0);
                id = legacy == R.style.Theme_MyApp_Light ? BuiltInThemes.LIGHT_ID
                        : legacy == R.style.Theme_MyApp_Dark || legacy == R.style.Theme_MyApp_Black
                        ? BuiltInThemes.DARK_ID : systemChoice();
            } else id = systemChoice();
        }
        int style = BuiltInThemes.DARK_ID.equals(id) ? R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light;
        if (!id.equals(prefs.getString(PREF_KEY, null)) || prefs.getBoolean("systemTheme", true)
                || prefs.getInt("theme", 0) != style) {
            prefs.edit().putString(PREF_KEY, id).putInt("theme", style).putBoolean("systemTheme", false).apply();
        }
        return id;
    }

    public static boolean isDark(Context context) {
        return BuiltInThemes.DARK_ID.equals(getCurrentId(context));
    }

    /** A global night-mode change recreates activities; protect live editor buffers and file tasks. */
    public static boolean canChangeNow(Context context) {
        if (!EditorMinimizer.hasMinimizedEditor() && !ProgressManager.hasActiveOperations()) return true;
        Toast.makeText(context, R.string.theme_change_busy, Toast.LENGTH_SHORT).show();
        return false;
    }

    public static boolean applyChoice(Context context, String id) {
        if (!supported(id)) return false;
        if (id.equals(getCurrentId(context))) return true;
        if (!canChangeNow(context)) return false;
        setCurrentId(context, id);
        return true;
    }

    public static void setCurrentId(Context context, String id) {
        String choice = supported(id) ? id
                : BuiltInThemes.BLACK_ID.equals(id) ? BuiltInThemes.DARK_ID : systemChoice();
        int style = BuiltInThemes.DARK_ID.equals(choice) ? R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY, choice).putInt("theme", style).putBoolean("systemTheme", false).apply();
        // AppCompat recreates affected activities itself. Callers must not also recreate().
        AppCompatDelegate.setDefaultNightMode(modeFor(context, choice));
    }

    public static int modeFor(Context context, String id) {
        return BuiltInThemes.DARK_ID.equals(id) || BuiltInThemes.BLACK_ID.equals(id)
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
    }

    public static int currentStyleRes(Context context) {
        return isDark(context) ? R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light;
    }

    public static void applySaved(Activity activity) {
        ThemePlugin plugin = get(getCurrentId(activity));
        activity.setTheme(plugin.styleRes());
        AppCompatDelegate.setDefaultNightMode(plugin.nightMode());
        plugin.apply(activity);
    }

    private static void ensureBuiltIns() {
        if (builtInsLoaded) return;
        builtInsLoaded = true;
        BuiltInThemes.registerAll();
    }
}
