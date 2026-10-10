package io.github.abdurazaaqmohammed.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;

/** Scrollable, searchable native file chooser. All disk/package discovery runs off the UI thread. */
public final class OpenWithDialog {
    public enum Type {
        BUILT_IN(R.string.open_with_builtin, null), TEXT(R.string.text, "text/*"),
        IMAGE(R.string.image, "image/*"), VIDEO(R.string.open_with_video, "video/*"),
        AUDIO(R.string.open_with_audio, "audio/*"), ALL(R.string.open_with_all, "*/*");
        public final int label;
        public final String mime;
        Type(int label, String mime) { this.label = label; this.mime = mime; }
    }

    public static final class Action {
        final String label;
        final int icon, color;
        final Drawable appIcon;
        final Runnable open;
        final Consumer<View> onLongClick;
        public Action(String label, int icon, int color, Runnable open) {
            this(label, icon, color, null, open, null);
        }
        public Action(String label, Drawable appIcon, Runnable open, Consumer<View> onLongClick) {
            this(label, 0, 0, appIcon, open, onLongClick);
        }
        private Action(String label, int icon, int color, Drawable appIcon, Runnable open, Consumer<View> onLongClick) {
            this.label = label; this.icon = icon; this.color = color; this.appIcon = appIcon;
            this.open = open; this.onLongClick = onLongClick;
        }
    }

    private final Activity activity;
    private final List<Action> builtIn, actions = new ArrayList<>(), visible = new ArrayList<>();
    private final Function<Type, List<Action>> apps;
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> new Thread(r, "hexora-open-with"));
    private final Handler main = new Handler(Looper.getMainLooper());
    private final View root, progress, empty, searchPanel;
    private final TextView category;
    private final EditText query;
    private final OptionsAdapter adapter = new OptionsAdapter();
    private final AlertDialog dialog;
    private final int columns;
    private Type type = Type.BUILT_IN;
    private Future<?> appLoad;
    private int generation;

    public OpenWithDialog(Activity activity, String filename, List<Action> builtIn,
                          Function<Type, List<Action>> apps, Supplier<String> mimeDetails, Runnable more) {
        this.activity = activity;
        this.builtIn = new ArrayList<>(builtIn);
        this.apps = apps;
        root = LayoutInflater.from(activity).inflate(R.layout.dialog_open_with, null, false);
        ((TextView) root.findViewById(R.id.open_with_filename)).setText(filename);
        progress = root.findViewById(R.id.open_with_progress);
        empty = root.findViewById(R.id.open_with_empty);
        category = root.findViewById(R.id.open_with_category);
        query = root.findViewById(R.id.open_with_search_input);
        searchPanel = root.findViewById(R.id.open_with_search_panel);
        RecyclerView grid = root.findViewById(R.id.open_with_grid);
        int widthDp = (int) (activity.getResources().getDisplayMetrics().widthPixels
                / activity.getResources().getDisplayMetrics().density) - 32;
        columns = widthDp < 320 || activity.getResources().getConfiguration().fontScale > 1.3f ? 2 : 3;
        grid.setLayoutManager(new GridLayoutManager(activity, columns));
        grid.setItemAnimator(null);
        grid.setAdapter(adapter);

        dialog = new MaterialAlertDialogBuilder(activity).setView(root)
                .setBackgroundInsetStart(0).setBackgroundInsetEnd(0)
                .setBackgroundInsetTop(0).setBackgroundInsetBottom(0).create();
        dialog.setOnDismissListener(d -> {
            generation++;
            hideKeyboard();
            loader.shutdownNow();
        });
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) {
                generation++;
                loader.shutdownNow();
            }
        });
        root.findViewById(R.id.open_with_close).setOnClickListener(v -> dialog.dismiss());
        root.findViewById(R.id.open_with_more).setOnClickListener(v -> {
            dialog.dismiss();
            runAction(more);
        });
        root.findViewById(R.id.open_with_type).setOnClickListener(this::showTypes);
        root.findViewById(R.id.open_with_search_button).setOnClickListener(v -> {
            boolean show = searchPanel.getVisibility() != View.VISIBLE;
            searchPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                query.requestFocus();
                query.post(() -> {
                    if (!isOpen()) return;
                    InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (keyboard != null) keyboard.showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
                });
            } else { query.setText(""); hideKeyboard(); }
        });
        View details = root.findViewById(R.id.open_with_details_panel);
        root.findViewById(R.id.open_with_info_button).setOnClickListener(v -> {
            details.setVisibility(details.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            v.setSelected(details.getVisibility() == View.VISIBLE);
        });
        SwitchMaterial actualMime = root.findViewById(R.id.open_with_actual_mime);
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        actualMime.setChecked(prefs.getBoolean("fix_mime_type", false));
        actualMime.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("fix_mime_type", checked).apply());
        ((TextView) root.findViewById(R.id.open_with_mime_info)).setText(R.string.loading);
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        actions.addAll(builtIn);
        filter();
        loader.submit(() -> {
            String detailsText;
            try { detailsText = mimeDetails.get(); }
            catch (Exception e) { detailsText = activity.getString(R.string.open_with_details_unavailable); }
            String text = detailsText;
            main.post(() -> {
                if (isOpen()) ((TextView) root.findViewById(R.id.open_with_mime_info)).setText(text);
            });
        });
    }

    public void show() {
        if (activity.isFinishing() || activity.isDestroyed()) { loader.shutdownNow(); return; }
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            float density = activity.getResources().getDisplayMetrics().density;
            int width = Math.min((int) (560 * density), activity.getResources().getDisplayMetrics().widthPixels - (int) (32 * density));
            int rows = (builtIn.size() + columns - 1) / columns;
            int preferredHeight = Math.min(680, 180 + rows * 112);
            int height = Math.min((int) (preferredHeight * density), (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.84f));
            window.setLayout(width, height);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private boolean isOpen() { return dialog.isShowing() && !activity.isFinishing() && !activity.isDestroyed(); }

    private void hideKeyboard() {
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(query.getWindowToken(), 0);
    }

    private void showTypes(View anchor) {
        hideKeyboard();
        PopupMenu menu = new PopupMenu(activity, anchor);
        for (Type option : Type.values()) {
            menu.getMenu().add(1, option.ordinal(), option.ordinal(), option.label)
                    .setCheckable(true).setChecked(option == type);
        }
        menu.getMenu().setGroupCheckable(1, true, true);
        menu.setOnMenuItemClickListener(item -> { selectType(Type.values()[item.getItemId()]); return true; });
        menu.show();
    }

    private void selectType(Type selected) {
        type = selected;
        int request = ++generation;
        if (appLoad != null) appLoad.cancel(true);
        actions.clear();
        if (selected == Type.BUILT_IN) {
            actions.addAll(builtIn);
            progress.setVisibility(View.GONE);
            filter();
            return;
        }
        visible.clear(); adapter.notifyDataSetChanged();
        empty.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        category.setText(selected.label);
        appLoad = loader.submit(() -> {
            List<Action> matches;
            try { matches = apps.apply(selected); }
            catch (Exception e) { matches = java.util.Collections.emptyList(); }
            List<Action> loaded = matches;
            main.post(() -> {
                if (!isOpen() || request != generation) return;
                if (selected == Type.ALL) actions.addAll(builtIn);
                actions.addAll(loaded);
                progress.setVisibility(View.GONE);
                filter();
            });
        });
    }

    private void filter() {
        String search = query.getText().toString().trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (Action action : actions) if (action.label.toLowerCase(Locale.ROOT).contains(search)) visible.add(action);
        adapter.notifyDataSetChanged();
        ((TextView) empty).setText(search.isEmpty() ? R.string.no_apps_found : R.string.open_with_no_matches);
        empty.setVisibility(visible.isEmpty() && progress.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
        category.setText(activity.getString(R.string.open_with_group, activity.getString(type.label), visible.size()));
    }

    private void runAction(Runnable action) {
        try { action.run(); }
        catch (Exception e) { new ErrorUtil(activity).showError(e); }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        Holder(View item) { super(item); icon = item.findViewById(R.id.open_with_icon); label = item.findViewById(R.id.open_with_label); }
    }

    private final class OptionsAdapter extends RecyclerView.Adapter<Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(activity).inflate(R.layout.item_open_with, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            Action action = visible.get(position);
            holder.label.setText(action.label);
            holder.itemView.setContentDescription(action.label);
            Drawable icon = action.appIcon;
            if (icon == null) icon = new GlyphTileDrawable(activity,
                    action.icon == 0 ? R.drawable.baseline_open_in_new_24 : action.icon,
                    action.icon == 0 ? 0xFF536D83 : action.color);
            else if (icon.getConstantState() != null) icon = icon.getConstantState().newDrawable(activity.getResources()).mutate();
            holder.icon.setImageDrawable(icon);
            holder.itemView.setOnClickListener(v -> { dialog.dismiss(); runAction(action.open); });
            holder.itemView.setOnLongClickListener(action.onLongClick == null ? null : v -> {
                action.onLongClick.accept(v); return true;
            });
        }
        @Override public int getItemCount() { return visible.size(); }
    }
}
