package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns only imported copies; sender files and explicitly saved/exported files are never cleaned. */
public final class SharedFileCache {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService CLEANER = Executors.newSingleThreadExecutor();
    private static volatile int activityCount;
    private static int generation;

    private SharedFileCache() { }

    public static File directory(android.content.Context context) {
        return new File(context.getCacheDir(), "shared-incoming");
    }

    public static void initialize(Application app) {
        // Snapshot before any receiver starts, so startup cleanup cannot include a new import.
        List<File> previous = candidates(app);
        clean(app, previous, false, new HashSet<>());
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                activityCount++;
                generation++;
            }
            @Override public void onActivityDestroyed(Activity activity) {
                activityCount = Math.max(0, activityCount - 1);
                if (activityCount == 0) scheduleCleanup(app, generation);
            }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
        });
    }

    private static void scheduleCleanup(Application app, int expectedGeneration) {
        // Let configuration changes, viewer handoffs and final editor recovery writes settle.
        MAIN.postDelayed(() -> {
            if (activityCount != 0 || generation != expectedGeneration) return;
            if (ProgressManager.hasActiveOperations()) {
                scheduleCleanup(app, expectedGeneration);
                return;
            }
            Set<String> protectedPaths = new HashSet<>(
                    io.github.abdurazaaqmohammed.player.PlayerManager.activeFilePaths());
            clean(app, candidates(app), true, protectedPaths);
        }, 5000);
    }

    private static List<File> candidates(Application app) {
        List<File> files = new ArrayList<>();
        File[] folders = {directory(app), new File(app.getFilesDir(), "Shared files")};
        for (File folder : folders) {
            File[] children = folder.listFiles();
            if (children != null) for (File file : children) if (file.isFile()) files.add(file);
        }
        return files;
    }

    private static void clean(Application app, List<File> candidates, boolean requireClosed, Set<String> protectedPaths) {
        CLEANER.execute(() -> {
            try {
                for (String path : new ArrayList<>(protectedPaths)) protectedPaths.add(new File(path).getCanonicalPath());
                // A minimized/recoverable editor can outlive the process. Preserve its sources
                // and archive parents until the user closes that session normally.
                File session = new File(app.getCacheDir(), "text_editor_session.json");
                if (session.isFile()) {
                    StringBuilder json = new StringBuilder();
                    try (InputStreamReader reader = new InputStreamReader(new FileInputStream(session), StandardCharsets.UTF_8)) {
                        char[] buffer = new char[8192];
                        int count;
                        while ((count = reader.read(buffer)) != -1) {
                            if (json.length() + count > 32 * 1024 * 1024) return;
                            json.append(buffer, 0, count);
                        }
                    }
                    JSONObject root = new JSONObject(json.toString());
                    protect(root.optJSONArray("tabs"), protectedPaths, "file", "zipFile");
                    protect(root.optJSONArray("archiveEdits"), protectedPaths, "file", "archive");
                }
                for (File file : candidates) {
                    if (requireClosed && activityCount != 0) return;
                    String path = file.getCanonicalPath();
                    if (!protectedPaths.contains(path) && !protectedPaths.contains(path.replaceAll("\\.bak$", ""))) file.delete();
                }
            } catch (Exception ignored) {
                // Unreadable recovery metadata must never cause loss of a recoverable source.
            }
        });
    }

    private static void protect(JSONArray entries, Set<String> paths, String... keys) throws Exception {
        if (entries == null) return;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            for (String key : keys) {
                String path = entry.optString(key, "");
                if (!path.isEmpty()) paths.add(new File(path).getCanonicalPath());
            }
        }
    }
}
