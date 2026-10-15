package io.github.abdurazaaqmohammed.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.reandroid.apk.APKLogger;

import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.ref.WeakReference;

import io.github.abdurazaaqmohammed.MPManager.R;

public class ProgressManager {
    private final AppCompatActivity activity;
    private final Handler handler;
    private volatile boolean indeterminate;
    public AlertDialog dialog;
    private volatile String currentText;
    private volatile long progressVal, maxVal;
    private boolean hidden;
    private volatile boolean dismissed;
    private volatile Runnable onCancelled;
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private NotificationManagerCompat nm;
    private View content;
    private final AtomicBoolean renderQueued = new AtomicBoolean();
    private final Runnable renderState = this::renderPendingState;
    private long lastNotificationAt;

    private static final String CHANNEL_ID = "progress_channel";
    private static final String ACTION_RESTORE = "io.github.abdurazaaqmohammed.action.RESTORE_PROGRESS";
    private static final String ACTION_CANCEL = "io.github.abdurazaaqmohammed.action.CANCEL_PROGRESS";
    private static final String EXTRA_OPERATION = "progressOperation";
    private static final AtomicInteger NEXT_ID = new AtomicInteger((int) (System.currentTimeMillis() & 0x3fffffff));
    private static final ConcurrentHashMap<Integer, WeakReference<ProgressManager>> ACTIVE = new ConcurrentHashMap<>();
    private final int notificationId = NEXT_ID.getAndIncrement();

    public static boolean isRestoreIntent(Intent intent) {
        return intent != null && ACTION_RESTORE.equals(intent.getAction());
    }

    public static boolean hasActiveOperations() {
        for (WeakReference<ProgressManager> reference : ACTIVE.values()) {
            ProgressManager progress = reference.get();
            if (progress != null && !progress.dismissed) return true;
        }
        return false;
    }

    public static void restoreFromIntent(AppCompatActivity activity, Intent intent) {
        if (!isRestoreIntent(intent)) return;
        int id = intent.getIntExtra(EXTRA_OPERATION, -1);
        if (id < 0) return;
        WeakReference<ProgressManager> reference = ACTIVE.get(id);
        ProgressManager progress = reference == null ? null : reference.get();
        if (progress != null && progress.activity == activity && !progress.dismissed) progress.show();
        else NotificationManagerCompat.from(activity).cancel(id);
        // Do not replay the tap on subsequent resumes.
        intent.removeExtra(EXTRA_OPERATION);
    }

    public ProgressManager(AppCompatActivity activity, boolean indeterminate) {
        this.activity = activity;
        this.handler = new Handler(Looper.getMainLooper());
        this.indeterminate = indeterminate;
        ACTIVE.put(notificationId, new WeakReference<>(this));
        initChannel();
    }

    public ProgressManager onCancel(Runnable action) {
        onCancelled = action;
        scheduleRender();
        return this;
    }

    public boolean isCancelled() { return cancellationRequested.get(); }

    public void checkCancelled() throws java.io.InterruptedIOException {
        if (isCancelled() || Thread.currentThread().isInterrupted())
            throw new java.io.InterruptedIOException("Operation cancelled");
    }

    /** Register before starting so Cancel also works before the first progress update. */
    public void startWorker(Runnable task) {
        Thread worker = new Thread(() -> {
            if (isCancelled()) Thread.currentThread().interrupt();
            try { task.run(); }
            finally { dismiss(); }
        }, "hexora-file-task");
        onCancel(worker::interrupt);
        worker.start();
    }

    public void reportError(Throwable error) {
        if (!isCancelled() && !Thread.currentThread().isInterrupted()) new ErrorUtil(activity).showError(error);
    }

    public void cancel() {
        Runnable action = onCancelled;
        if (dismissed || action == null || !cancellationRequested.compareAndSet(false, true)) return;
        currentText = activity.getString(R.string.progress_cancelling);
        // Keep the progress UI until the worker has closed streams and cleaned up.
        action.run();
        scheduleRender();
    }

    static void cancelFromNotification(android.content.Context context, Intent intent) {
        if (intent == null || !ACTION_CANCEL.equals(intent.getAction())) return;
        int id = intent.getIntExtra(EXTRA_OPERATION, -1);
        WeakReference<ProgressManager> reference = ACTIVE.get(id);
        ProgressManager progress = reference == null ? null : reference.get();
        if (progress != null) progress.cancel();
        else if (id >= 0) NotificationManagerCompat.from(context).cancel(id);
    }

    public ProgressManager show() {
        handler.post(() -> {
            if (dismissed || activity.isFinishing() || activity.isDestroyed()) return;
            hidden = false;
            cancelNotification();
            if (dialog != null) {
                if (content != null) updateDialog(content);
                if (!dialog.isShowing()) dialog.show();
                return;
            }
            View v = LayoutInflater.from(activity).inflate(R.layout.progress_dialog, null, false);
            content = v;
            v.findViewById(R.id.hideButton).setOnClickListener(v1 -> hide());
            v.findViewById(R.id.cancelTaskButton).setOnClickListener(v1 -> cancel());
            updateDialog(v);
            dialog = new MaterialAlertDialogBuilder(activity).setView(v).setCancelable(false).show();
        });
        return this;
    }

    public void setText(int id, String append) {
        setText(activity.getString(id, append));
    }

    public ProgressManager setText(int id) { return setText(activity.getString(id)); }

    public ProgressManager setText(String text) {
        if (dismissed || isCancelled()) return this;
        this.currentText = text;
        scheduleRender();
        return this;
    }

    public ProgressManager setProgress(int progress, int max) {
        return setProgress((long) progress, (long) max);
    }

    public ProgressManager setProgress(long progress, long max) {
        if (dismissed || isCancelled() || max <= 0) return this;
        this.progressVal = Math.max(0, Math.min(progress, max));
        this.maxVal = max;
        this.indeterminate = false;
        scheduleRender();
        return this;
    }

    private void scheduleRender() {
        // Logs and copy callbacks can arrive much faster than the screen refreshes.
        if (renderQueued.compareAndSet(false, true)) handler.postDelayed(renderState, 50);
    }

    private void renderPendingState() {
        renderQueued.set(false);
        if (dismissed || activity.isFinishing() || activity.isDestroyed()) return;
        if (dialog != null && dialog.isShowing() && content != null) updateDialog(content);
        if (hidden) updateNotification();
    }

    private void updateDialog(View view) {
        TextView title = view.findViewById(R.id.dialogTitle);
        title.setText(isCancelled() ? activity.getString(R.string.progress_cancelling)
                : currentText == null ? activity.getString(R.string.loading) : currentText);
        android.widget.Button cancel = view.findViewById(R.id.cancelTaskButton);
        cancel.setVisibility(onCancelled == null ? View.GONE : View.VISIBLE);
        cancel.setEnabled(!isCancelled());
        cancel.setText(isCancelled() ? R.string.progress_cancelling : android.R.string.cancel);
        LinearProgressIndicator bar = view.findViewById(R.id.progressBar);
        TextView value = view.findViewById(R.id.progressValue);
        long max = maxVal;
        if (!isCancelled() && !indeterminate && max > 0) {
            long progress = Math.max(0, Math.min(progressVal, max));
            bar.setMax(10000);
            // Material completes the indeterminate cycle before animating real progress.
            bar.setProgressCompat((int) ((double) progress / max * 10000), true);
            value.setText(progressLabel(progress, max));
            value.setVisibility(View.VISIBLE);
        } else {
            bar.setIndeterminate(true);
            value.setVisibility(View.INVISIBLE);
        }
    }

    public void dismiss() {
        dismissed = true;
        ACTIVE.remove(notificationId);
        handler.post(() -> {
            handler.removeCallbacks(renderState);
            renderQueued.set(false);
            if (dialog != null) dialog.dismiss();
            cancelNotification();
        });
    }

    public APKLogger getLogger() {
        boolean saveLog = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("logEnabled", false);
        FileWriter fw = null;
        if (saveLog) try {
            fw = new FileWriter(AppLogs.newLogFile("log"), true);
        } catch (IOException ignored) {}
        FileWriter logFw = fw;
        return new APKLogger() {
            @Override public void logMessage(String s) { setText(s); if (logFw != null) try { logFw.write(s + "\n"); } catch (IOException ignored) {} }
            @Override public void logError(String s, Throwable t) { try { AppLogs.writeCrash(t, activity); } catch (Exception ignored) {} new ErrorUtil(activity).showError(t); if (logFw != null) try { logFw.write(s + "\n"); for (StackTraceElement e : t.getStackTrace()) logFw.write(e.toString() + "\n"); } catch (IOException ignored) {} }
            @Override public void logVerbose(String s) { setText((s)); if (logFw != null) try { logFw.write(s + "\n"); } catch (IOException ignored) {} }
            @Override public void close() { if (logFw != null) try { logFw.close(); } catch (IOException ignored) {} }
        };
    }

    private void hide() {
        if (dismissed) return;
        // Keep the dialog accessible if notification permission is unavailable.
        if (!io.github.codehasan.colorpicker.extensions.Extensions.canShowNotification(activity)) return;
        hidden = true;
        if (dialog != null) dialog.hide();
        showNotification();
    }

    private void initChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, activity.getString(R.string.notif_channel_progress), NotificationManager.IMPORTANCE_LOW);
            NotificationManager m = activity.getSystemService(NotificationManager.class);
            if (m != null) m.createNotificationChannel(c);
        }
    }

    private void showNotification() {
        if (!io.github.codehasan.colorpicker.extensions.Extensions.canShowNotification(activity)) return;
        nm = NotificationManagerCompat.from(activity);
        lastNotificationAt = android.os.SystemClock.uptimeMillis();
        nm.notify(notificationId, buildNotif().build());
    }

    private void updateNotification() {
        if (!io.github.codehasan.colorpicker.extensions.Extensions.canShowNotification(activity)) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastNotificationAt < 500) {
            if (renderQueued.compareAndSet(false, true)) handler.postDelayed(renderState, 500 - (now - lastNotificationAt));
            return;
        }
        if (nm != null) {
            lastNotificationAt = now;
            nm.notify(notificationId, buildNotif().build());
        }
    }

    private NotificationCompat.Builder buildNotif() {
        NotificationCompat.Builder b = new NotificationCompat.Builder(activity, CHANNEL_ID)
                .setContentTitle(activity.getString(R.string.app_name))
                .setContentText(isCancelled() ? activity.getString(R.string.progress_cancelling)
                        : currentText != null ? currentText : activity.getString(R.string.progress_working))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(activity, notificationId,
                        new Intent(activity, activity.getClass()).setAction(ACTION_RESTORE)
                                .putExtra(EXTRA_OPERATION, notificationId)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        if (!isCancelled() && !indeterminate && maxVal > 0) {
            b.setProgress(10000, (int) Math.max(0, Math.min(10000, (double) progressVal / maxVal * 10000)), false);
            b.setSubText(progressLabel(progressVal, maxVal));
        }
        else b.setProgress(0, 0, true);
        if (onCancelled != null && !isCancelled()) {
            Intent cancel = new Intent(activity, ProgressCancelReceiver.class).setAction(ACTION_CANCEL)
                    .putExtra(EXTRA_OPERATION, notificationId);
            b.addAction(R.drawable.ic_close_mt, activity.getString(android.R.string.cancel),
                    PendingIntent.getBroadcast(activity, notificationId, cancel,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        return b;
    }

    private String progressLabel(long done, long total) {
        double ratio = Math.max(0, Math.min(1, (double) done / total));
        // Keep incomplete operations below 100 even when decimal formatting rounds up.
        double shown = done >= total ? 1 : Math.floor(ratio * 10000) / 10000;
        NumberFormat format = NumberFormat.getPercentInstance();
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return activity.getString(R.string.progress_complete_remaining,
                format.format(shown), format.format(1 - shown));
    }

    private void cancelNotification() {
        if (nm != null) nm.cancel(notificationId);
    }
}
