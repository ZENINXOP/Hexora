package io.github.abdurazaaqmohammed.utils;

import android.text.InputType;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.domain.files.ArchivePasswords;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

public final class ArchiveAccess {
    private ArchiveAccess() { }
    public interface Action { void run() throws Exception; }
    public static void withAccess(AppCompatActivity activity, File file, Action action) {
        String name=file.getName().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".zip") || name.endsWith(".apk") || name.endsWith(".jar") || name.endsWith(".aar")
                || name.endsWith(".xapk") || name.endsWith(".apks") || name.endsWith(".apkm") || name.endsWith(".aspk"))) {
            proceed(activity, action); return;
        }
        new Thread(() -> {
            try (ZipFile zip=ArchivePasswords.open(file)) {
                boolean needs=zip.isEncrypted() && ArchivePasswords.get(file)==null;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (needs) prompt(activity,file,action); else proceed(activity,action);
                });
            } catch (Exception e) { activity.runOnUiThread(() -> new ErrorUtil(activity).showError(e)); }
        },"hexora-archive-access").start();
    }
    private static void proceed(AppCompatActivity activity, Action action) {
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            try { action.run(); } catch (Exception e) { new ErrorUtil(activity).showError(e); }
        });
    }
    private static void prompt(AppCompatActivity activity, File file, Action action) {
        EditText input=new EditText(activity);
        input.setSingleLine(true);
        input.setHint(R.string.enter_password);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        com.google.android.material.textfield.TextInputLayout passwordBox=UiFields.wrap(activity,input,null,0);
        AtomicBoolean dismissed=new AtomicBoolean();
        AlertDialog dialog=new MaterialAlertDialogBuilder(activity).setTitle(R.string.archive_password_title)
                .setMessage(file.getName()).setView(passwordBox)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,null).create();
        dialog.setOnDismissListener(d -> { dismissed.set(true); input.setText(""); });
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            char[] password=input.getText().toString().toCharArray();
            passwordBox.setError(null);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            new Thread(() -> {
                try (ZipFile zip=new ZipFile(file,password)) {
                    FileHeader smallest=null;
                    for (FileHeader header:zip.getFileHeaders()) if (header.isEncrypted() && !header.isDirectory()
                            && (smallest==null || header.getUncompressedSize()<smallest.getUncompressedSize())) smallest=header;
                    if (smallest!=null) try (InputStream stream=zip.getInputStream(smallest)) {
                        byte[] bytes=new byte[65536];
                        while (stream.read(bytes)!=-1) if (dismissed.get()) return;
                    }
                    if (dismissed.get()) return;
                    ArchivePasswords.remember(file,password);
                    ZipArchiveCache.invalidate(file);
                    activity.runOnUiThread(() -> {
                        if (dismissed.get()) return;
                        dialog.dismiss(); proceed(activity,action);
                    });
                } catch (Exception e) {
                    activity.runOnUiThread(() -> {
                        if (dismissed.get()) return;
                        passwordBox.setError(activity.getString(R.string.invalid_password));
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    });
                } finally { Arrays.fill(password,'\0'); }
            },"hexora-check-archive-password").start();
        }));
        dialog.show();
    }
}
