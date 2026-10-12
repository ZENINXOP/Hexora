package io.github.abdurazaaqmohammed.features.files;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;
import io.github.abdurazaaqmohammed.domain.files.FileClipboard;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

public final class FileClipboardController {
    private static final AtomicBoolean pasting=new AtomicBoolean();
    private final MainActivity activity;
    public FileClipboardController(MainActivity activity) { this.activity=activity; }
    public boolean hasFiles() { return !FileClipboard.SESSION.snapshot().files().isEmpty(); }
    public void capture(List<File> files,boolean cut) {
        try {
            FileClipboard.SESSION.set(files,cut);
            Extensions.showMessage(activity,activity.getString(cut ? R.string.clipboard_cut_count : R.string.clipboard_copy_count,
                    FileClipboard.SESSION.snapshot().files().size()));
        } catch (Exception e) { new ErrorUtil(activity).showError(e); }
    }
    public void clear() { FileClipboard.SESSION.clear(); }
    public void paste() {
        if (!(activity.getCurrentPane().getAdapter() instanceof MainFilesArrayAdapter adapter) || adapter.isInZip) {
            Extensions.showMessage(activity,R.string.clipboard_local_destination); return;
        }
        var selection=FileClipboard.SESSION.snapshot();
        if (selection.files().isEmpty()) { Extensions.showMessage(activity,R.string.nothing_found_to_paste); return; }
        if (!pasting.compareAndSet(false,true)) { Extensions.showMessage(activity,R.string.clipboard_busy); return; }
        File destination=adapter.pane1 ? activity.pane1Folder : activity.pane2Folder;
        boolean started=adapter.pasteFiles(selection.files(),selection.cut(),destination,completed -> {
            FileClipboard.SESSION.completed(selection,completed); pasting.set(false);
        });
        if (!started) pasting.set(false);
    }
}
