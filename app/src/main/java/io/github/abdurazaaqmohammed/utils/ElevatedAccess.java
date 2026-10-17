package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.preference.PreferenceManager;

import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell;
import rikka.shizuku.Shizuku;

/** Discovers grants and resumes waiting file workers without blocking the UI thread. */
public final class ElevatedAccess {
    private static final int PERMISSION_CODE = 5102;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean ROOT_CHECK = new AtomicBoolean();
    private static WeakReference<Activity> foreground = new WeakReference<>(null);
    private static boolean resumed;
    private static boolean initialized;
    private static boolean shizukuDetected;
    private static boolean promptedForBinder;
    private static long deniedAt;
    private static boolean permissionInFlight;
    private static long permissionFinishedAt = -2000;
    private static CountDownLatch permissionAttempt;

    private ElevatedAccess() { }

    public static synchronized void initialize(Context context) {
        if (initialized || Build.VERSION.SDK_INT < 23) return;
        initialized = true;
        Context app = context.getApplicationContext();
        Shizuku.addBinderReceivedListenerSticky(() -> MAIN.post(() -> {
            detectShizuku(app);
            requestPermission(false);
        }));
        Shizuku.addBinderDeadListener(() -> MAIN.post(() -> {
            synchronized (ElevatedAccess.class) {
                shizukuDetected = false;
                promptedForBinder = false;
                deniedAt = 0;
                io.github.abdurazaaqmohammed.shizuku.ShizukuConnection.reset();
                finishPermissionAttempt();
            }
        }));
        Shizuku.addRequestPermissionResultListener((code, result) -> {
            // Settings and the folder guide can also obtain the same grant.
            if (code != PERMISSION_CODE && result != PackageManager.PERMISSION_GRANTED) return;
            MAIN.post(() -> {
                if (result == PackageManager.PERMISSION_GRANTED) detectShizuku(app);
                synchronized (ElevatedAccess.class) {
                    if (result != PackageManager.PERMISSION_GRANTED) deniedAt = SystemClock.elapsedRealtime();
                    finishPermissionAttempt();
                }
            });
        });
        ShizukuShell.warmUp(app);
    }

    public static void onResume(Activity activity) {
        synchronized (ElevatedAccess.class) {
            foreground = new WeakReference<>(activity);
            resumed = true;
        }
        initialize(activity);
        detectShizuku(activity);
        requestPermission(false);
        if ((!ShizukuManager.isRunning() || RootManager.getInstance(activity).isRootMode())
                && ROOT_CHECK.compareAndSet(false, true)) {
            Context app = activity.getApplicationContext();
            new Thread(() -> {
                try { RootManager.getInstance(app).autoEnableRootIfAvailable(); }
                finally { ROOT_CHECK.set(false); }
            }, "hexora-access-detection").start();
        }
    }

    public static synchronized void onPause(Activity activity) {
        if (foreground.get() == activity) resumed = false;
    }

    public static synchronized void onDestroy(Activity activity) {
        if (foreground.get() == activity) {
            foreground.clear();
            resumed = false;
        }
    }

    private static synchronized void detectShizuku(Context context) {
        if (!ShizukuManager.ready()) {
            shizukuDetected = false;
            return;
        }
        if (shizukuDetected) return;
        shizukuDetected = true;
        RootManager root = RootManager.getInstance(context);
        // Keep an already authorized root session when it was the selected backend.
        if (root.getWorkingMode() != RootManager.WorkingMode.ROOT || !root.hasCachedRootAccess()) {
            root.setWorkingMode(RootManager.WorkingMode.SHIZUKU);
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean("shizuku_file_ops", true).apply();
        ShizukuManager.warmUp(context);
    }

    private static synchronized void requestPermission(boolean fromAction) {
        Activity activity = foreground.get();
        if (ShizukuManager.hasPermission()) {
            if (activity != null) detectShizuku(activity);
            finishPermissionAttempt();
            return;
        }
        if (permissionInFlight) return;
        if (permissionAttempt == null) permissionAttempt = new CountDownLatch(1);
        if (!ShizukuManager.isRunning() || activity == null || activity.isFinishing()
                || activity.isDestroyed() || !resumed) {
            finishPermissionAttempt();
            return;
        }
        if (promptedForBinder && (!fromAction || SystemClock.elapsedRealtime() - deniedAt < 5000)) {
            finishPermissionAttempt();
            return;
        }
        promptedForBinder = true;
        permissionInFlight = true;
        try { Shizuku.requestPermission(PERMISSION_CODE); }
        catch (RuntimeException unavailable) {
            deniedAt = SystemClock.elapsedRealtime();
            finishPermissionAttempt();
        }
    }

    private static void finishPermissionAttempt() {
        if (permissionInFlight) permissionFinishedAt = SystemClock.elapsedRealtime();
        permissionInFlight = false;
        if (permissionAttempt != null) permissionAttempt.countDown();
        permissionAttempt = null;
    }

    public static synchronized boolean isPermissionTransition() {
        return permissionInFlight || SystemClock.elapsedRealtime() - permissionFinishedAt < 1500;
    }

    /** Called by the shared filesystem backend, including worker threads already doing an action. */
    public static void prepare(Context context) {
        if (context == null) return;
        initialize(context);
        detectShizuku(context);
        if (Looper.myLooper() == Looper.getMainLooper()) return;
        if (Thread.currentThread().isInterrupted())
            throw new java.util.concurrent.CancellationException("Operation cancelled");
        RootManager root = RootManager.getInstance(context);
        if (root.getWorkingMode() == RootManager.WorkingMode.ROOT && root.isRootAvailable()) {
            root.autoEnableRootIfAvailable();
            return;
        }
        if (root.getWorkingMode() == RootManager.WorkingMode.ROOT && ShizukuManager.ready()) {
            root.setWorkingMode(RootManager.WorkingMode.SHIZUKU);
        }
        if (ShizukuManager.isRunning() && !ShizukuManager.hasPermission()) {
            CountDownLatch attempt;
            synchronized (ElevatedAccess.class) {
                if (permissionAttempt == null) {
                    permissionAttempt = new CountDownLatch(1);
                    MAIN.post(() -> requestPermission(true));
                }
                attempt = permissionAttempt;
            }
            long deadline = SystemClock.elapsedRealtime() + 60000;
            try {
                while (!attempt.await(200, TimeUnit.MILLISECONDS)) {
                    if (SystemClock.elapsedRealtime() >= deadline)
                        throw new java.util.concurrent.CancellationException("Permission request timed out");
                    if (context instanceof Activity activity
                            && (activity.isFinishing() || activity.isDestroyed()))
                        throw new java.util.concurrent.CancellationException("Activity closed");
                }
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CancellationException("Operation cancelled");
            }
            detectShizuku(context);
        }
        if (Thread.currentThread().isInterrupted())
            throw new java.util.concurrent.CancellationException("Operation cancelled");
        if (context instanceof Activity activity && (activity.isFinishing() || activity.isDestroyed()))
            throw new java.util.concurrent.CancellationException("Activity closed");
        if (!ShizukuManager.ready()) root.autoEnableRootIfAvailable();
        if (Thread.currentThread().isInterrupted())
            throw new java.util.concurrent.CancellationException("Operation cancelled");
    }
}
