package io.github.abdurazaaqmohammed.player;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.domain.files.SharedFileCopy;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Receives granted content URIs while the sender's temporary permission is alive. */
public class LocateActivity extends BaseActivity {
    private TextView statusView;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Future<?> receiveTask;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, pad, pad, pad);
        statusView = new TextView(this);
        statusView.setTextSize(15);
        statusView.setText(R.string.locate_resolving);
        root.addView(statusView, new LinearLayout.LayoutParams(-1, -2));
        MaterialButton cancel = new MaterialButton(this);
        cancel.setText(android.R.string.cancel);
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        receiveTask = worker.submit(() -> receive(getIntent()));
    }

    private void receive(Intent intent) {
        List<File> received = new ArrayList<>();
        List<File> copies = new ArrayList<>();
        try {
            List<Uri> uris = collectUris(intent);
            if (uris.isEmpty()) throw new IOException(getString(R.string.shared_no_file));
            for (Uri uri : uris) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Receiving cancelled.");
                if ("file".equalsIgnoreCase(uri.getScheme())) {
                    File file = new File(uri.getPath());
                    if (!file.isFile() || !file.canRead()) throw new IOException(getString(R.string.shared_read_failed));
                    received.add(file);
                } else if ("content".equalsIgnoreCase(uri.getScheme())) {
                    File file = importContent(uri, intent == null ? null : intent.getType());
                    copies.add(file);
                    received.add(file);
                } else throw new IOException(getString(R.string.shared_read_failed));
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) { for (File file : copies) file.delete(); return; }
                try {
                    startActivity(new Intent(this, MainActivity.class)
                            .putExtra("locatePath", received.get(0).getAbsolutePath())
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    finish();
                } catch (Exception e) {
                    for (File file : copies) file.delete();
                    statusView.setText(getString(R.string.locate_failed) + "\n" + getString(R.string.shared_read_failed));
                }
            });
        } catch (Exception e) {
            for (File file : copies) file.delete();
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) statusView.setText(getString(R.string.locate_failed)
                        + "\n" + getString(R.string.shared_read_failed));
            });
        } finally { worker.shutdown(); }
    }

    private File importContent(Uri uri, String intentMime) throws IOException {
        String name = null;
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) name = cursor.getString(column);
            }
        } catch (Exception ignored) { }
        String mime = intentMime;
        try { String type = getContentResolver().getType(uri); if (type != null) mime = type; }
        catch (Exception ignored) { }
        String extension = mime == null ? null : MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if (name == null || name.trim().isEmpty()) name = uri.getLastPathSegment();
        File directory = io.github.abdurazaaqmohammed.utils.SharedFileCache.directory(this);
        long available = getCacheDir().getUsableSpace();
        long budget = available > 0 ? Math.max(0, available - 16L * 1024 * 1024) : Long.MAX_VALUE;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            return SharedFileCopy.copy(input, directory, SharedFileCopy.safeName(name, extension), budget);
        } catch (SecurityException e) {
            throw new IOException(getString(R.string.shared_read_failed), e);
        }
    }

    private List<Uri> collectUris(Intent intent) {
        LinkedHashSet<Uri> uris = new LinkedHashSet<>();
        if (intent != null) {
            if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
                ArrayList<Uri> multiple = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (multiple != null) for (Uri uri : multiple) if (uri != null) uris.add(uri);
            } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
                Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null) uris.add(uri);
            }
            if (intent.getData() != null) uris.add(intent.getData());
            if (intent.getClipData() != null) {
                for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
                    Uri uri = intent.getClipData().getItemAt(i).getUri();
                    if (uri != null) uris.add(uri);
                }
            }
        }
        return new ArrayList<>(uris);
    }

    @Override protected void onDestroy() {
        if (receiveTask != null) receiveTask.cancel(true);
        worker.shutdownNow();
        super.onDestroy();
    }
}
