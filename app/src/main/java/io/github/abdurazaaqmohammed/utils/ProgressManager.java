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

import io.github.abdurazaaqmohammed.MPManager.R;

public class ProgressManager {
    private final AppCompatActivity activity;
    private final Handler handler;
    private volatile boolean indeterminate;
    public AlertDialog dialog;
    private volatile String currentText;
    private volatile int progressVal, maxVal;
    private boolean hidden;
    private volatile boolean dismissed;
    private NotificationManagerCompat nm;
    private View content;
    private final AtomicBoolean renderQueued = new AtomicBoolean();
    private final Runnable renderState = this::renderPendingState;

    private static final String CHANNEL_ID = "progress_channel";
    private static final int NOTIFICATION_ID = 1001;

    public ProgressManager(AppCompatActivity activity, boolean indeterminate) {
        this.activity = activity;
        this.handler = new Handler(Looper.getMainLooper());
        this.indeterminate = indeterminate;
        initChannel();
    }

    public ProgressManager show() {
        handler.post(() -> {
            if (dismissed || activity.isFinishing() || activity.isDestroyed() || (dialog != null && dialog.isShowing())) return;
            View v = LayoutInflater.from(activity).inflate(R.layout.progress_dialog, null, false);
            content = v;
            v.findViewById(R.id.hideButton).setOnClickListener(v1 -> hide());
            updateDialog(v);
            dialog = new MaterialAlertDialogBuilder(activity).setView(v).show();
        });
        return this;
    }

    public void setText(int id, String append) {
        setText(activity.getString(id, append));
    }

    public ProgressManager setText(String text) {
        if (dismissed) return this;
        this.currentText = text;
        scheduleRender();
        return this;
    }

    public ProgressManager setProgress(int progress, int max) {
        if (dismissed || max <= 0) return this;
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
        title.setText(currentText == null ? activity.getString(R.string.loading) : currentText);
        LinearProgressIndicator bar = view.findViewById(R.id.progressBar);
        TextView value = view.findViewById(R.id.progressValue);
        int max = maxVal;
        if (!indeterminate && max > 0) {
            int progress = Math.max(0, Math.min(progressVal, max));
            bar.setMax(max);
            // Material completes the indeterminate cycle before animating real progress.
            bar.setProgressCompat(progress, true);
            value.setText(NumberFormat.getPercentInstance().format((double) progress / max));
            value.setVisibility(View.VISIBLE);
        } else {
            value.setVisibility(View.INVISIBLE);
        }
    }

    public void dismiss() {
        dismissed = true;
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
        nm.notify(NOTIFICATION_ID, buildNotif().build());
    }

    private void updateNotification() {
        if (!io.github.codehasan.colorpicker.extensions.Extensions.canShowNotification(activity)) return;
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotif().build());
    }

    private NotificationCompat.Builder buildNotif() {
        NotificationCompat.Builder b = new NotificationCompat.Builder(activity, CHANNEL_ID)
                .setContentTitle(activity.getString(R.string.app_name))
                .setContentText(currentText != null ? currentText : activity.getString(R.string.progress_working))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(activity, 0, new Intent(activity, activity.getClass()), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        if (!indeterminate && maxVal > 0) b.setProgress(maxVal, progressVal, false);
        else b.setProgress(0, 0, true);
        return b;
    }

    private void cancelNotification() {
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }
}
