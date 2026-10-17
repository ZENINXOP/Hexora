package io.github.abdurazaaqmohammed.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.BottomBarButtonAdapter;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.ui.editor.EditorPreferences;
import io.github.abdurazaaqmohammed.ui.editor.ImportedSyntax;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import modder.hub.dexeditor.activity.EditFloatingMenusActivity;

public class EditorSettingsActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_editor_preferences);
        MaterialToolbar toolbar = findViewById(R.id.editorPreferencesToolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        if (state == null) getSupportFragmentManager().beginTransaction()
                .replace(R.id.editorPreferencesContent, new SettingsFragment()).commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        private static final String[] ACTIONS = {"None", "Search", "Insert text", "Regex find and replace",
                "Copy selection", "Cut selection", "Paste selection", "Copy line", "Cut line",
                "Delete line", "Empty line", "Replace line"};
        private final Handler main = new Handler(Looper.getMainLooper());
        private boolean importing;
        private final ActivityResultLauncher<String[]> syntaxPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri == null || !isAdded()) return;
                    Context app = requireContext().getApplicationContext();
                    importing = true;
                    updateImportSummary();
                    new Thread(() -> {
                        Exception failure = null;
                        try (InputStream input = app.getContentResolver().openInputStream(uri)) {
                            ImportedSyntax.install(app, ImportedSyntax.read(input));
                        } catch (Exception error) { failure = error; }
                        Exception result = failure;
                        main.post(() -> {
                            importing = false;
                            if (!isAdded()) return;
                            updateImportSummary();
                            if (result != null) new ErrorUtil(requireActivity()).showError(result.getMessage());
                            else Toast.makeText(requireContext(), R.string.ep_syntax_imported, Toast.LENGTH_SHORT).show();
                        });
                    }, "hexora-syntax-import").start();
                });

        @Override public void onCreatePreferences(Bundle state, String rootKey) {
            SharedPreferences prefs = EditorPreferences.get(requireContext());
            EditorPreferences.normalize(prefs);
            getPreferenceManager().setSharedPreferencesName("editor_prefs");
            setPreferencesFromResource(R.xml.editor_preferences, rootKey);
            style(getPreferenceScreen());
            Preference floating = findPreference("edit_floating_menus");
            floating.setOnPreferenceClickListener(pref -> {
                startActivity(new Intent(requireContext(), EditFloatingMenusActivity.class));
                return true;
            });
            findPreference("pref_bottom_bar_buttons").setOnPreferenceClickListener(pref -> {
                showBottomBarManagementDialog(); return true;
            });
            findPreference("pref_import_syntax").setOnPreferenceClickListener(pref -> {
                if (ImportedSyntax.name(requireContext()) == null) chooseSyntax();
                else new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ep_import_syntax)
                        .setItems(new String[]{getString(R.string.ep_choose_grammar), getString(R.string.ep_remove_grammar)}, (dialog, which) -> {
                            if (which == 0) chooseSyntax();
                            else { ImportedSyntax.remove(requireContext()); updateImportSummary(); }
                        }).show();
                return true;
            });
            updateImportSummary();
        }

        private void style(PreferenceGroup group) {
            for (int index = 0; index < group.getPreferenceCount(); index++) {
                Preference pref = group.getPreference(index);
                pref.setIconSpaceReserved(false);
                pref.setLayoutResource(pref instanceof PreferenceCategory
                        ? R.layout.item_editor_preference_category : R.layout.item_editor_preference);
                if (pref instanceof SwitchPreferenceCompat)
                    pref.setWidgetLayoutResource(R.layout.widget_editor_preference_switch);
                if (pref instanceof PreferenceGroup children) style(children);
            }
        }

        @Override public void onViewCreated(View view, Bundle state) {
            super.onViewCreated(view, state);
            getListView().setPadding(dp(12), 0, dp(12), dp(24));
            getListView().setClipToPadding(false);
            setDivider(null);
        }

        @Override public void onDisplayPreferenceDialog(Preference preference) {
            if (!(preference instanceof ListPreference list)) { super.onDisplayPreferenceDialog(preference); return; }
            new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Hexora_EditorPreferenceChoice).setTitle(list.getTitle())
                    .setSingleChoiceItems(list.getEntries(), list.findIndexOfValue(list.getValue()), (dialog, which) -> {
                        String value = list.getEntryValues()[which].toString();
                        if (list.callChangeListener(value)) list.setValue(value);
                        dialog.dismiss();
                    }).setNegativeButton(android.R.string.cancel, null).show();
        }

        @Override public void onResume() {
            super.onResume();
            EditorPreferences.get(requireContext()).registerOnSharedPreferenceChangeListener(this);
            updateImportSummary();
        }
        @Override public void onPause() {
            EditorPreferences.get(requireContext()).unregisterOnSharedPreferenceChangeListener(this);
            super.onPause();
        }
        @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if ("custom_syntax_revision".equals(key)) updateImportSummary();
        }
        private void chooseSyntax() { syntaxPicker.launch(new String[]{"application/json", "text/*", "application/octet-stream"}); }
        private void updateImportSummary() {
            Preference pref = findPreference("pref_import_syntax");
            if (pref == null) return;
            String name = importing ? null : ImportedSyntax.name(requireContext());
            pref.setEnabled(!importing);
            pref.setSummary(importing ? getString(R.string.ep_importing) : name == null
                    ? getString(R.string.ep_import_syntax_summary) : getString(R.string.ep_imported_syntax, name));
        }

        private void showBottomBarManagementDialog() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            JSONArray buttons = new JSONArray();
            try {
                String json = prefs.getString("pref_bottom_bar_buttons", "[]");
                JSONArray saved = "Search,Copy,Cut,Paste".equals(json) ? new JSONArray() : new JSONArray(json);
                for (int index = 0; index < saved.length(); index++)
                    if (saved.optJSONObject(index) != null) buttons.put(saved.optJSONObject(index));
            } catch (Exception invalid) { /* A malformed legacy list opens as an editable empty list. */ }
            JSONArray entries = buttons;
            ListView list = new ListView(requireContext());
            list.setDivider(null);
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.ep_empty_toolbar);
            empty.setTextColor(com.google.android.material.color.MaterialColors.getColor(empty,
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
            empty.setPadding(dp(24), dp(24), dp(24), dp(24));
            FrameLayout box = new FrameLayout(requireContext());
            box.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280)));
            box.addView(empty);
            BottomBarButtonAdapter[] adapter = new BottomBarButtonAdapter[1];
            Runnable refresh = () -> {
                prefs.edit().putString("pref_bottom_bar_buttons", entries.toString()).apply();
                adapter[0].notifyDataSetChanged();
                empty.setVisibility(entries.length() == 0 ? View.VISIBLE : View.GONE);
                list.setVisibility(entries.length() == 0 ? View.GONE : View.VISIBLE);
            };
            adapter[0] = new BottomBarButtonAdapter(requireContext(), entries, new BottomBarButtonAdapter.OnButtonActionListener() {
                @Override public void onEdit(int position, JSONObject button) { showButtonDialog(entries, position, button, refresh); }
                @Override public void onDelete(int position) {
                    new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.delete_button)
                            .setMessage(getString(R.string.confirm_delete_f, entries.optJSONObject(position).optString("label",
                                    entries.optJSONObject(position).optString("action", ""))))
                            .setPositiveButton(R.string.delete, (dialog, which) -> { entries.remove(position); refresh.run(); })
                            .setNegativeButton(android.R.string.cancel, null).show();
                }
            });
            list.setAdapter(adapter[0]);
            empty.setVisibility(entries.length() == 0 ? View.VISIBLE : View.GONE);
            list.setVisibility(entries.length() == 0 ? View.GONE : View.VISIBLE);
            AlertDialog manager = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ep_toolbar).setView(box)
                    .setPositiveButton(R.string.add, null).setNegativeButton(R.string.close, null).create();
            manager.setOnShowListener(dialog -> manager.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> showButtonDialog(entries, -1, null, refresh)));
            manager.show();
        }

        private void showButtonDialog(JSONArray entries, int index, JSONObject existing, Runnable saved) {
            LinearLayout body = new LinearLayout(requireContext());
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(24), dp(8), dp(24), dp(16));
            ScrollView scroll = new ScrollView(requireContext());
            scroll.addView(body);
            EditText label = field(body, R.string.ep_button_label);
            if (existing != null) label.setText(existing.optString("label"));
            ActionFields tap = new ActionFields(body, R.string.edsettings_click, existing, "action", "data1", "data2");
            ActionFields hold = new ActionFields(body, R.string.edsettings_long, existing, "longAction", "longData1", "longData2");
            AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(existing == null ? R.string.edsettings_add : R.string.edsettings_edit).setView(scroll)
                    .setPositiveButton(R.string.save, null).setNegativeButton(android.R.string.cancel, null).create();
            dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (tap.action() == 0 && hold.action() == 0) { label.setError(getString(R.string.ep_choose_action)); return; }
                if (!tap.validate() || !hold.validate()) return;
                try {
                    JSONObject button = new JSONObject();
                    String title = label.getText().toString().trim();
                    if (!title.isEmpty()) button.put("label", title);
                    tap.write(button, "action", "data1", "data2");
                    hold.write(button, "longAction", "longData1", "longData2");
                    if (index < 0) entries.put(button); else entries.put(index, button);
                    saved.run();
                    dialog.dismiss();
                } catch (Exception failure) { new ErrorUtil(requireActivity()).showError(failure); }
            }));
            dialog.show();
        }

        private EditText field(LinearLayout body, int hint) {
            TextInputLayout input = input(body, hint);
            TextInputEditText text = new TextInputEditText(requireContext());
            text.setTextSize(14);
            text.setMinHeight(dp(56));
            input.addView(text, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            text.setTag(input);
            return text;
        }
        private TextInputLayout input(LinearLayout body, int hint) {
            TextInputLayout input = new TextInputLayout(requireContext());
            input.setHint(getString(hint));
            input.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
            input.setBoxCornerRadii(dp(12), dp(12), dp(12), dp(12));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(12);
            body.addView(input, params);
            return input;
        }
        private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

        private class ActionFields {
            final MaterialAutoCompleteTextView action;
            final EditText first;
            final EditText second;
            final String[] labels = {getString(R.string.ep_no_action), getString(R.string.search), getString(R.string.insert_text),
                    getString(R.string.regex_find_replace), getString(android.R.string.copy), getString(R.string.cut),
                    getString(R.string.paste), getString(R.string.copy_line), getString(R.string.cut_line),
                    getString(R.string.delete_line), getString(R.string.empty_line), getString(R.string.replace_line_with_clipboard)};
            int selected;
            ActionFields(LinearLayout body, int title, JSONObject existing, String key, String firstKey, String secondKey) {
                TextInputLayout choice = input(body, title);
                action = new MaterialAutoCompleteTextView(requireContext());
                action.setInputType(android.text.InputType.TYPE_NULL);
                action.setTextSize(14);
                action.setMinHeight(dp(56));
                choice.addView(action);
                choice.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
                action.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, labels));
                first = field(body, R.string.ep_insert_value);
                second = field(body, R.string.ep_replace_regex);
                if (existing != null) {
                    for (int position = 0; position < ACTIONS.length; position++)
                        if (ACTIONS[position].equals(existing.optString(key))) selected = position;
                    first.setText(existing.optString(firstKey));
                    second.setText(existing.optString(secondKey));
                }
                action.setText(labels[selected], false);
                update();
                action.setOnItemClickListener((parent, view, position, id) -> { selected = position; update(); });
            }
            int action() { return selected; }
            void update() {
                ((TextInputLayout) first.getTag()).setHint(getString(selected == 3 ? R.string.ep_find_regex : R.string.ep_insert_value));
                ((View) first.getTag()).setVisibility(selected == 2 || selected == 3 ? View.VISIBLE : View.GONE);
                ((View) second.getTag()).setVisibility(selected == 3 ? View.VISIBLE : View.GONE);
            }
            boolean validate() {
                if ((selected == 2 || selected == 3) && TextUtils.isEmpty(first.getText())) {
                    first.setError(getString(R.string.ep_value_required)); return false;
                }
                if (selected == 3) try { Pattern.compile(first.getText().toString()); }
                catch (RuntimeException invalid) { first.setError(getString(R.string.ep_invalid_regex)); return false; }
                return true;
            }
            void write(JSONObject target, String key, String firstKey, String secondKey) throws Exception {
                if (selected != 0) target.put(key, ACTIONS[selected]);
                target.put(firstKey, first.getText().toString());
                target.put(secondKey, second.getText().toString());
            }
        }
    }
}
