package io.github.abdurazaaqmohammed.adapters;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.FileMenuOrder;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class DialogAdapter extends RecyclerView.Adapter<DialogAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private final MainActivity context;
    private final List<FileMenuOrder.MenuItem> items;
    private final boolean isInZip;
    private final boolean grid;
    private final OnItemClickListener listener;

    public DialogAdapter(MainActivity context, List<FileMenuOrder.MenuItem> items, boolean isInZip, OnItemClickListener listener) {
        this(context, items, isInZip, false, listener);
    }

    public DialogAdapter(MainActivity context, List<FileMenuOrder.MenuItem> items, boolean isInZip, boolean grid, OnItemClickListener listener) {
        this.context = context;
        this.items = items;
        this.isInZip = isInZip;
        this.grid = grid;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(grid ? R.layout.item_file_menu_action_grid : R.layout.item_file_menu_action, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileMenuOrder.MenuItem item = items.get(position);
        holder.label.setText(item.label());
        int iconRes = FileMenuOrder.iconFor(context, item.id(), false, false);
        Drawable drawable = androidx.appcompat.content.res.AppCompatResources.getDrawable(context,
                iconRes == 0 ? R.drawable.tools_24px : iconRes);
        if (drawable != null) {
            drawable = DrawableCompat.wrap(drawable).mutate();
            int i = Extensions.dp2px(context, grid ? 20 : 24);
            drawable.setBounds(0, 0, i, i);
            DrawableCompat.setTint(drawable, MaterialColors.getColor(holder.label,
                    FileMenuOrder.DELETE.equals(item.id()) ? com.google.android.material.R.attr.colorError
                            : com.google.android.material.R.attr.colorPrimary));
        }
        holder.label.setCompoundDrawablesRelative(drawable, null, null, null);
        holder.label.setCompoundDrawablePadding(Extensions.dp2px(context, 8));

        boolean disabled = (FileMenuOrder.MOVE.equals(item.id()) && sameDestination())
                || ((FileMenuOrder.COMPRESS.equals(item.id()) || FileMenuOrder.BOOKMARK.equals(item.id()) || FileMenuOrder.CMD.equals(item.id())) && isInZip);
        holder.itemView.setAlpha(disabled ? 0.38f : 1f);
        holder.itemView.setEnabled(!disabled);
        holder.itemView.setOnClickListener(v -> {
            if (disabled || listener == null) return;
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onItemClick(pos);
        });
    }

    private boolean sameDestination() {
        if (!java.util.Objects.equals(context.pane1Folder, context.pane2Folder)) return false;
        RecyclerView left = context.findViewById(R.id.listViewPane1);
        RecyclerView right = context.findViewById(R.id.listViewPane2);
        if (left.getAdapter() instanceof MainFilesArrayAdapter a && a.isInZip
                && right.getAdapter() instanceof MainFilesArrayAdapter b && b.isInZip) {
            return java.util.Objects.equals(a.currentZipPath, b.currentZipPath);
        }
        return true;
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView label;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.menuItemLabel);
        }
    }
}
