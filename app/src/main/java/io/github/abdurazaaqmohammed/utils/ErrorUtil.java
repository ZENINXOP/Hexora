package io.github.abdurazaaqmohammed.utils;

import static io.github.abdurazaaqmohammed.utils.FileUtils.doesNotHaveStoragePerm;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class ErrorUtil {
    private static final java.util.concurrent.ExecutorService REPORTS = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(16),
            new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    private final Activity context;
    private final DialogUtil dialogUtil;
    public ErrorUtil (Activity c) {
        this.context = c;
        dialogUtil = new DialogUtil(c);
    }
    private void copyText(CharSequence text) {
        ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("log", text));
        Extensions.showMessage(context, text);
    }

    public void showError(String s) {
        context.runOnUiThread(() -> {
        if (context.isFinishing() || context.isDestroyed()) return;
        dialogUtil.getDialogBuilder()
                .setMessage(s)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.copy_log, (dialog, which) -> copyText(s)).show();
        });
    }

    public void showError(Throwable e) {
        REPORTS.execute(() -> prepareError(e));
    }

    private void prepareError(Throwable e) {
        try {
            AppLogs.writeCrash(e, context);
        } catch (Exception ignored) {}
        final String mainErr = e.toString();
        StringBuilder stackTrace = new StringBuilder(mainErr).append('\n');
        for (StackTraceElement line : e.getStackTrace()) stackTrace.append(line).append('\n');
        stackTrace.append('\n')
                .append("SDK ").append(Build.VERSION.SDK_INT).append('\n')
                .append("Hexora ").append('v');
        String currentVer;
        try {
            currentVer = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ex) {
            currentVer = "1.0";
        }
        stackTrace.append(currentVer).append('\n').append("Storage permission granted: ").append(!doesNotHaveStoragePerm(context));
        context.runOnUiThread(() -> {
        if (context.isFinishing() || context.isDestroyed()) return;
        MaterialAlertDialogBuilder b = dialogUtil.getDialogBuilder()
                .setTitle(R.string.error)
                .setNegativeButton(R.string.close, null)
                .setNeutralButton(R.string.copy_log, (dialog, which) -> copyText(stackTrace))
                .setPositiveButton(R.string.hexora_error_details, (dialog, which) ->
                        dialogUtil.getDialogBuilder().setTitle(R.string.hexora_error_details)
                                .setMessage(stackTrace).setPositiveButton(R.string.close, null)
                                .setNeutralButton(R.string.copy_log, (details, button) -> copyText(stackTrace)).show());
            b.setMessage(e.getMessage() == null ? mainErr : e.getMessage()).show();
        });
    }
}
