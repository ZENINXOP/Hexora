package io.github.abdurazaaqmohammed.features.files;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.R;

/** Search and disclosure state for settings; does not change preference values. */
final class SettingsSections {
    private final Activity activity;
    private final EditText search;
    private final View theme;
    private final View about;
    private final View empty;
    private final List<Section> sections = new ArrayList<>();

    SettingsSections(Activity activity, View root) {
        this.activity = activity;
        search = root.findViewById(R.id.settingsSearch);
        theme = root.findViewById(R.id.settingsThemeCard);
        about = root.findViewById(R.id.about);
        empty = root.findViewById(R.id.settingsEmpty);
        add(root, R.id.settingsAppearanceSection, R.string.appearance,
                R.string.settings_appearance_summary, R.drawable.light);
        add(root, R.id.settingsFoldersSection, R.string.folders,
                R.string.settings_folders_summary, R.drawable.folder_24px);
        add(root, R.id.settingsFilesSection, R.string.file_operations,
                R.string.settings_files_summary, R.drawable.baseline_folder_zip_24);
        add(root, R.id.settingsAccessSection, R.string.elevated_access,
                R.string.settings_access_summary, R.drawable.lock_24px);
        add(root, R.id.settingsBehaviorSection, R.string.behavior,
                R.string.settings_behavior_summary, R.drawable.baseline_settings_24);
        add(root, R.id.settingsSigningSection, R.string.signing_card,
                R.string.settings_signing_summary, R.drawable.ic_open_key);
        add(root, R.id.settingsUpdatesSection, R.string.update,
                R.string.settings_updates_summary, R.drawable.baseline_refresh_24);
        add(root, R.id.settingsPluginsSection, R.string.plugins_section,
                R.string.settings_plugins_summary, R.drawable.code_24px);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); }
            @Override public void afterTextChanged(Editable text) { }
        });
        search.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
            search.clearFocus();
            return true;
        });
        filter();
    }

    private void add(View root, int id, int titleId, int summaryId, int iconId) {
        View card = root.findViewById(id);
        if (card == null) return; // Plugins only contribute a section when installed.
        View header = card.findViewWithTag("settings_header");
        View content = card.findViewWithTag("settings_content");
        TextView title = header.findViewWithTag("settings_title");
        TextView summary = header.findViewWithTag("settings_summary");
        ImageView icon = header.findViewWithTag("settings_icon");
        title.setText(titleId);
        summary.setText(summaryId);
        icon.setImageResource(iconId);
        header.setContentDescription(title.getText() + ". " + summary.getText());
        ViewCompat.setAccessibilityHeading(header, true);
        Section section = new Section(card, header, content);
        sections.add(section);
        header.setOnClickListener(view -> {
            boolean expanded = content.getVisibility() != View.VISIBLE;
            // Searching temporarily expands matches without losing the user's normal layout.
            if (query().isEmpty()) section.expanded = expanded;
            section.show(expanded, true);
        });
    }

    void save(Bundle state) {
        state.putString("settings_search_query", search.getText().toString());
        ArrayList<Integer> expanded = new ArrayList<>();
        for (Section section : sections) if (section.expanded) expanded.add(section.card.getId());
        state.putIntegerArrayList("settings_expanded_sections", expanded);
    }

    void restore(Bundle state) {
        if (state == null) return;
        ArrayList<Integer> expanded = state.getIntegerArrayList("settings_expanded_sections");
        if (expanded != null) for (Section section : sections) section.expanded = expanded.contains(section.card.getId());
        search.setText(state.getString("settings_search_query", ""));
        filter();
    }

    private String query() {
        return search.getText().toString().trim().toLowerCase(Locale.ROOT);
    }

    private void filter() {
        String query = query();
        boolean found = showMatch(theme, query) | showMatch(about, query);
        for (Section section : sections) {
            boolean matches = query.isEmpty() || matches(section.card, query);
            section.card.setVisibility(matches ? View.VISIBLE : View.GONE);
            section.show(matches && (!query.isEmpty() || section.expanded), false);
            found |= matches;
        }
        empty.setVisibility(found ? View.GONE : View.VISIBLE);
    }

    private boolean showMatch(View view, String query) {
        boolean matches = query.isEmpty() || matches(view, query);
        view.setVisibility(matches ? View.VISIBLE : View.GONE);
        return matches;
    }

    private static boolean matches(View view, String query) {
        StringBuilder text = new StringBuilder();
        collectText(view, text);
        String searchable = text.toString().toLowerCase(Locale.ROOT);
        for (String word : query.split("\\s+")) if (!searchable.contains(word)) return false;
        return true;
    }

    private static void collectText(View view, StringBuilder text) {
        if (view instanceof TextView label) {
            if (label.getText() != null) text.append(' ').append(label.getText());
            if (label.getHint() != null) text.append(' ').append(label.getHint());
        }
        if (view instanceof TextInputLayout input && input.getHint() != null) text.append(' ').append(input.getHint());
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) collectText(group.getChildAt(index), text);
        }
    }

    private final class Section {
        final View card;
        final View header;
        final View content;
        final ImageView chevron;
        boolean expanded;

        Section(View card, View header, View content) {
            this.card = card;
            this.header = header;
            this.content = content;
            chevron = header.findViewWithTag("settings_chevron");
            chevron.setScaleX(ViewCompat.getLayoutDirection(header) == ViewCompat.LAYOUT_DIRECTION_RTL ? -1 : 1);
        }

        void show(boolean expanded, boolean animate) {
            content.setVisibility(expanded ? View.VISIBLE : View.GONE);
            float rotation = expanded ? (ViewCompat.getLayoutDirection(header) == ViewCompat.LAYOUT_DIRECTION_RTL ? -90 : 90) : 0;
            chevron.animate().cancel();
            if (animate) chevron.animate().rotation(rotation).setDuration(160).start();
            else chevron.setRotation(rotation);
            ViewCompat.setStateDescription(header, activity.getString(expanded ? R.string.settings_expanded : R.string.settings_collapsed));
        }
    }
}
