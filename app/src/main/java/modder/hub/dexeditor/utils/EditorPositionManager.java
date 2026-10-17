/*
 * Dex-Editor-Android an Advanced Dex Editor for Android
 * Copyright 2024-26, developer-krushna
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of developer-krushna nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


 *     Please contact Krushna by email mt.modder.hub@gmail.com if you need
 *     additional information or have any questions
 */

package modder.hub.dexeditor.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

// Author - @developer-krushna
public class EditorPositionManager {
    private static final String FILE_NAME = "editor_positions.json";
    private static final long PERSIST_DELAY_MS = 1500;
    private static EditorPositionManager instance;
    private final File file;
    private final Map<String, Position> positions = new java.util.LinkedHashMap<>();
    private final java.util.concurrent.ExecutorService diskWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private int loadGeneration;
    private boolean loaded;
    private final java.util.List<Runnable> loadCallbacks = new java.util.ArrayList<>();
    private static final int MAX_POSITIONS = 5000;
    private final Gson gson = new Gson();
    private final Handler persistHandler = new Handler(Looper.getMainLooper());
    private final Runnable persistRunnable = new Runnable() {
        @Override
        public void run() {
            persistNow();
        }
    };

    public static class Position {
        public final int lineno;
        public final int column;

        public Position(int lineno, int column) {
            this.lineno = lineno;
            this.column = column;
        }
    }

    private EditorPositionManager(Context context) {
        file = new File(context.getFilesDir(), FILE_NAME);
        diskWorker.execute(() -> {
            try { load(); }
            finally {
                java.util.List<Runnable> callbacks;
                synchronized (positions) {
                    loaded = true;
                    callbacks = new java.util.ArrayList<>(loadCallbacks);
                    loadCallbacks.clear();
                }
                for (Runnable callback : callbacks) persistHandler.post(callback);
            }
        });
    }

    public static synchronized EditorPositionManager getInstance(Context context) {
        if (instance == null) {
            instance = new EditorPositionManager(context.getApplicationContext());
        }
        return instance;
    }

    private void load() {
        if (file.exists() && file.length() <= 2 * 1024 * 1024) {
            String json = FileUtil.readFile(file.getAbsolutePath());
            if (json != null && !json.isEmpty()) {
                try {
                    Type type = new TypeToken<Map<String, Position>>() {}.getType();
                    Map<String, Position> loaded = gson.fromJson(json, type);
                    if (loaded != null) {
                        synchronized (positions) {
                            if (loadGeneration != 0) return;
                            for (Map.Entry<String, Position> entry : loaded.entrySet()) {
                                if (positions.size() >= MAX_POSITIONS) break;
                                if (entry.getKey() != null && entry.getValue() != null)
                                    positions.putIfAbsent(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                } catch (Exception | StackOverflowError e) {
                    // Keep positions already recorded while the disk read was pending.
                }
            }
        }
    }

    public void savePosition(String className, int lineno, int column) {
        synchronized (positions) {
            Position previous = positions.get(className);
            if (previous != null && previous.lineno == lineno && previous.column == column) return;
            positions.remove(className);
            positions.put(className, new Position(lineno, column));
            while (positions.size() > MAX_POSITIONS) positions.remove(positions.keySet().iterator().next());
        }
        schedulePersist();
    }

    private void schedulePersist() {
        persistHandler.removeCallbacks(persistRunnable);
        persistHandler.postDelayed(persistRunnable, PERSIST_DELAY_MS);
    }

    private void persistNow() {
        diskWorker.execute(() -> {
            Map<String, Position> snapshot;
            synchronized (positions) { snapshot = new HashMap<>(positions); }
            String json = gson.toJson(snapshot);
            FileUtil.writeFile(file.getAbsolutePath(), json);
        });
    }

    public Position getPosition(String className) {
        synchronized (positions) { return positions.get(className); }
    }

    public void whenLoaded(Runnable callback) {
        synchronized (positions) {
            if (!loaded) { loadCallbacks.add(callback); return; }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) callback.run();
        else persistHandler.post(callback);
    }

    public void removePosition(String className) {
        synchronized (positions) {
            loadGeneration++;
            positions.remove(className);
        }
        schedulePersist();
    }

    public void clear() {
        persistHandler.removeCallbacks(persistRunnable);
        synchronized (positions) {
            loadGeneration++;
            positions.clear();
        }
        diskWorker.execute(() -> { if (file.exists()) file.delete(); });
    }
}
