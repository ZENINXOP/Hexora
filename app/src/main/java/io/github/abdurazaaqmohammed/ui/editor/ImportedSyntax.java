package io.github.abdurazaaqmohammed.ui.editor;

import android.content.Context;
import android.util.AtomicFile;

import org.eclipse.tm4e.core.registry.IGrammarSource;
import org.eclipse.tm4e.core.registry.IThemeSource;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;
import io.github.abdurazaaqmohammed.ui.fragment.UnifiedEditorFragment;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;

/** A bounded, app-owned TextMate JSON grammar, selected by its declared fileTypes. */
public final class ImportedSyntax {
    private static final int MAX_BYTES = 1024 * 1024;
    private static long loadedRevision = -1;
    private ImportedSyntax() { }

    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "editor-custom.tmLanguage.json"));
    }

    public static byte[] read(InputStream input) throws IOException {
        if (input == null) throw new IOException("Cannot read the selected syntax file");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
            if (output.size() + count > MAX_BYTES) throw new IOException("Syntax files must be smaller than 1 MB");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static JSONObject metadata(byte[] bytes) throws Exception {
        JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        String scope = json.optString("scopeName", "").trim();
        JSONArray types = json.optJSONArray("fileTypes");
        if (scope.isEmpty() || scope.length() > 128 || "source.smali".equals(scope)
                || json.optJSONArray("patterns") == null || types == null || types.length() == 0 || types.length() > 64)
            throw new IOException("Choose a TextMate JSON grammar with scopeName, patterns and fileTypes; the built-in Smali scope cannot be replaced");
        for (int index = 0; index < types.length(); index++) {
            if (!(types.get(index) instanceof String type) || !type.matches("[A-Za-z0-9_.+\\-]{1,64}"))
                throw new IOException("The grammar contains an invalid file type");
        }
        return json;
    }

    private static void prepare(Context context, byte[] bytes) throws Exception {
        UnifiedEditorFragment.ensureLanguageInitialized(context.getApplicationContext());
        String palette = ThemeRegistry.isDark(context) ? "smali-dark.json" : "smali-light.json";
        try (InputStream grammar = new ByteArrayInputStream(bytes);
             InputStream theme = context.getAssets().open("themes/" + palette)) {
            if (TextMateLanguage.prepareLoad(IGrammarSource.fromInputStream(grammar, "editor-custom.tmLanguage.json", null),
                    null, IThemeSource.fromInputStream(theme, palette, null)) == null)
                throw new IOException("The syntax grammar could not be loaded");
        }
    }

    public static synchronized String install(Context context, byte[] bytes) throws Exception {
        JSONObject json = metadata(bytes);
        prepare(context, bytes); // Validate before replacing the last working grammar.
        AtomicFile target = file(context);
        FileOutputStream output = null;
        try {
            output = target.startWrite();
            output.write(bytes);
            target.finishWrite(output);
        } catch (IOException failure) {
            if (output != null) target.failWrite(output);
            throw failure;
        }
        long revision = revision(context) + 1;
        EditorPreferences.get(context).edit().putLong("custom_syntax_revision", revision).apply();
        loadedRevision = revision;
        return json.optString("name", json.getString("scopeName"));
    }

    public static long revision(Context context) { return EditorPreferences.get(context).getLong("custom_syntax_revision", 0); }

    public static synchronized String name(Context context) {
        try (InputStream input = file(context).openRead()) {
            JSONObject json = metadata(read(input));
            return json.optString("name", json.getString("scopeName"));
        } catch (Exception absent) { return null; }
    }

    public static synchronized void remove(Context context) {
        file(context).delete();
        EditorPreferences.get(context).edit().putLong("custom_syntax_revision", revision(context) + 1).apply();
        loadedRevision = -1;
    }

    public static synchronized TextMateLanguage language(Context context, String filename) {
        if (filename == null) return null;
        try (InputStream input = file(context).openRead()) {
            byte[] bytes = read(input);
            JSONObject json = metadata(bytes);
            String name = new File(filename).getName().toLowerCase(Locale.ROOT);
            JSONArray types = json.getJSONArray("fileTypes");
            boolean matches = false;
            for (int index = 0; index < types.length(); index++) {
                String type = types.getString(index).toLowerCase(Locale.ROOT);
                if (name.equals(type) || name.endsWith(type.startsWith(".") ? type : "." + type)) matches = true;
            }
            if (!matches) return null;
            long revision = revision(context);
            if (loadedRevision != revision) { prepare(context, bytes); loadedRevision = revision; }
            return TextMateLanguage.create(json.getString("scopeName"), true);
        } catch (Exception invalid) { return null; }
    }
}
