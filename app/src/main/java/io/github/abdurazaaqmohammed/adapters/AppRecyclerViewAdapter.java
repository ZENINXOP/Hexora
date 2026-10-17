package io.github.abdurazaaqmohammed.adapters;

import static android.view.View.GONE;

import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import io.github.abdurazaaqmohammed.ApkExtractor.APKExtractorActivity;
import io.github.abdurazaaqmohammed.ApkExtractor.AppInfo;
import io.github.abdurazaaqmohammed.MPManager.R;

public class AppRecyclerViewAdapter extends RecyclerView.Adapter<AppRecyclerViewAdapter.ViewHolder> implements Filterable {

    public final APKExtractorActivity apkExtractorActivity;
    public final List<AppInfo> appInfoList;
    public final List<AppInfo> filteredAppInfoList;

    private volatile Comparator<AppInfo> currentComparator;
    private AppInfoFilter filter;

    public AppRecyclerViewAdapter(APKExtractorActivity activity, List<AppInfo> list) {
        this.apkExtractorActivity = activity;
        this.appInfoList = list;
        this.filteredAppInfoList = new ArrayList<>(list);
    }

    public void setComparator(Comparator<AppInfo> comparator) {
        this.currentComparator = comparator;
    }

    @Override
    public int getItemCount() {
        return filteredAppInfoList.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
        return new ViewHolder(v);
    }

    public final HashSet<Integer> selectedItems = new HashSet<>();

    public void toggleSelection(int position) {
        if (position < 0 || position >= filteredAppInfoList.size()) return;
        if (selectedItems.contains(position)) selectedItems.remove(position);
        else selectedItems.add(position);
        notifyItemChanged(position);
    }

    public void clearSelection() {
        selectedItems.clear();
        apkExtractorActivity.findViewById(R.id.confirmButton).setVisibility(View.INVISIBLE);
        notifyDataSetChanged();
    }

    public void addItem(AppInfo appInfo) {
        synchronized (appInfoList) { if (!appInfoList.contains(appInfo)) appInfoList.add(appInfo); }
        filteredAppInfoList.add(appInfo);
        notifyItemInserted(filteredAppInfoList.size() - 1);
    }

    public void reset() {
        selectedItems.clear();
        filteredAppInfoList.clear();
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo appInfo = filteredAppInfoList.get(position);

        MaterialCardView cardView = holder.cardView;
        if (selectedItems.contains(position)) {
            TypedValue primaryColor = new TypedValue();
            cardView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, primaryColor, true);
            cardView.setStrokeColor(primaryColor.data);
            cardView.setStrokeWidth(6);

            TypedValue colorPrimaryContainer = new TypedValue();
            cardView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, colorPrimaryContainer, true);
            cardView.setCardBackgroundColor(colorPrimaryContainer.data);
        } else {
            TypedValue typedValue = new TypedValue();
            cardView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, typedValue, true);
            cardView.setStrokeColor(typedValue.data);
            cardView.setStrokeWidth(1);

            cardView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true);
            cardView.setCardBackgroundColor(typedValue.data);
            cardView.setBackgroundColor(typedValue.data);
        }

        APKExtractorActivity act = apkExtractorActivity;
        holder.appName.setText(appInfo.name);
        holder.appName.setVisibility(act.showAppName ? View.VISIBLE : GONE);
        StringBuilder sub = new StringBuilder();
        if (act.showPackageName) sub.append(appInfo.packageName);
        if (act.showVersionName && appInfo.versionName != null && !appInfo.versionName.isEmpty()) {
            if (sub.length() > 0) sub.append(" • ");
            sub.append('v').append(appInfo.versionName);
            if (act.showVersionCode) sub.append(" (").append(appInfo.getVersionCode()).append(')');
        } else if (act.showVersionCode) {
            if (sub.length() > 0) sub.append(" • ");
            sub.append("vCode ").append(appInfo.getVersionCode());
        }
        if (act.showFirstInstalled && appInfo.firstInstalled != null && !appInfo.firstInstalled.isEmpty()) {
            if (sub.length() > 0) sub.append('\n');
            sub.append(act.getString(R.string.first_install_date)).append(": ").append(appInfo.firstInstalled);
        }
        if (act.showLastUpdate && appInfo.lastUpdated != null && !appInfo.lastUpdated.isEmpty()) {
            if (sub.length() > 0) sub.append('\n');
            sub.append(act.getString(R.string.last_updated_date)).append(": ").append(appInfo.lastUpdated);
        }
        if (sub.length() == 0) sub.append(appInfo.packageName);
        holder.packageNameView.setText(sub.toString());
        holder.packageNameView.setVisibility(View.VISIBLE);
        holder.appIconView.setTag(null);
        if (act.showIcon) {
            holder.appIconView.setVisibility(View.VISIBLE);
            if (appInfo.icon != null) holder.appIconView.setImageDrawable(appInfo.icon);
            else io.github.abdurazaaqmohammed.ApkExtractor.AppIconLoader.load(act, appInfo, holder.appIconView);
        } else {
            holder.appIconView.setTag(null);
            holder.appIconView.setImageDrawable(null);
            holder.appIconView.setVisibility(GONE);
        }
        holder.splitIconView.setVisibility(appInfo.isSplit ? View.VISIBLE : GONE);

        holder.itemView.setOnClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (current == RecyclerView.NO_POSITION) return;
            if (!selectedItems.isEmpty()) {
                toggleSelection(current);
                if (selectedItems.isEmpty()) {
                    apkExtractorActivity.findViewById(R.id.confirmButton).setVisibility(View.INVISIBLE);
                }
            } else {
                apkExtractorActivity.showListViewDropdown(holder.extractIconView, current, this);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (current == RecyclerView.NO_POSITION) return true;
            apkExtractorActivity.findViewById(R.id.confirmButton).setVisibility(View.VISIBLE);
            toggleSelection(current);
            return true;
        });

        holder.extractIconView.setOnClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (current == RecyclerView.NO_POSITION) return;
            if (!selectedItems.isEmpty()) {
                toggleSelection(current);
                if (selectedItems.isEmpty()) {
                    apkExtractorActivity.findViewById(R.id.confirmButton).setVisibility(View.INVISIBLE);
                }
            } else {
                apkExtractorActivity.showListViewDropdown(holder.extractIconView, current, this);
            }
        });
    }

    @Override
    public Filter getFilter() {
        if (filter == null) filter = new AppInfoFilter();
        return filter;
    }

    private class AppInfoFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            List<AppInfo> snapshot;
            synchronized (appInfoList) { snapshot = new ArrayList<>(appInfoList); }
            if (TextUtils.isEmpty(constraint)) {
                results.values = snapshot;
            } else {
                List<AppInfo> filtered = new ArrayList<>();
                String pattern = constraint.toString().toLowerCase(java.util.Locale.ROOT).trim();
                for (AppInfo info : snapshot) {
                    if (info.name.toLowerCase(java.util.Locale.ROOT).contains(pattern) || info.packageName.toLowerCase(java.util.Locale.ROOT).contains(pattern)) {
                        filtered.add(info);
                    }
                }
                results.values = filtered;
            }
            Comparator<AppInfo> comparator = currentComparator;
            if (comparator != null) {
                List<AppInfo> sorted = (List<AppInfo>) results.values;
                Collections.sort(sorted, comparator);
                results.values = sorted;
            }
            results.count = ((List<AppInfo>) results.values).size();
            return results;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            if (apkExtractorActivity.isFinishing() || apkExtractorActivity.isDestroyed()) return;
            HashSet<AppInfo> selectedApps = new HashSet<>();
            for (int index : selectedItems) if (index >= 0 && index < filteredAppInfoList.size()) selectedApps.add(filteredAppInfoList.get(index));
            filteredAppInfoList.clear();
            if (results.values != null) filteredAppInfoList.addAll((List<AppInfo>) results.values);
            selectedItems.clear();
            for (int i = 0; i < filteredAppInfoList.size(); i++) if (selectedApps.contains(filteredAppInfoList.get(i))) selectedItems.add(i);
            apkExtractorActivity.updateAppSelectionUi();
            notifyDataSetChanged();
        }
    }

    @Override public void onViewRecycled(@NonNull ViewHolder holder) {
        holder.appIconView.setTag(null);
        holder.appIconView.setImageDrawable(null);
        super.onViewRecycled(holder);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardView;
        final TextView appName;
        final TextView packageNameView;
        final ImageView appIconView;
        final View splitIconView;
        final ImageView extractIconView;

        ViewHolder(View v) {
            super(v);
            cardView = (MaterialCardView) v;
            appName = v.findViewById(R.id.appName);
            packageNameView = v.findViewById(R.id.package_name_view);
            appIconView = v.findViewById(R.id.icon_view);
            splitIconView = v.findViewById(R.id.badge_view);
            extractIconView = v.findViewById(R.id.extract);
        }
    }
}
