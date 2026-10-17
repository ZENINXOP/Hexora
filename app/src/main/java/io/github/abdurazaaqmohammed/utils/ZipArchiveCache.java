package io.github.abdurazaaqmohammed.utils;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;

/** Shared, read-only ZIP metadata. Call get/copyEntry from a worker thread. */
public final class ZipArchiveCache {
    // Keep both panes warm without retaining every archive opened during a session.
    private static final Map<String, Pending> CACHE = new LinkedHashMap<>(4, .75f, true);

    private ZipArchiveCache() {}

    private static final class Pending {
        final long size;
        final long modified;
        final FutureTask<Archive> task;

        Pending(File file, long size, long modified) {
            this.size = size;
            this.modified = modified;
            task = new FutureTask<>(() -> new Archive(file));
        }
    }

    public static Archive get(File file) throws IOException {
        String key = file.getAbsolutePath();
        long size = file.length();
        long modified = file.lastModified();
        Pending pending;
        synchronized (CACHE) {
            pending = CACHE.get(key);
            if (pending == null || pending.size != size || pending.modified != modified) {
                pending = new Pending(file, size, modified);
                CACHE.put(key, pending);
                while (CACHE.size() > 2) CACHE.remove(CACHE.keySet().iterator().next());
            }
        }
        // FutureTask coalesces simultaneous requests from the two panes. No disk I/O
        // under the cache lock, so another archive can load independently.
        pending.task.run();
        try {
            return pending.task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Archive loading interrupted");
        } catch (ExecutionException e) {
            synchronized (CACHE) {
                if (CACHE.get(key) == pending) CACHE.remove(key);
            }
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("Cannot read archive", cause);
        }
    }

    public static void invalidate(File file) {
        if (file == null) return;
        synchronized (CACHE) {
            CACHE.remove(file.getAbsolutePath());
        }
    }

    public static void clear() {
        synchronized (CACHE) { CACHE.clear(); }
    }

    public static String folderPath(String path) {
        if (path == null || path.isEmpty()) return "";
        String normalized = path.replace('\\', '/');
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    public static final class Archive {
        private final ZipFile reader;
        private final Map<String, FileHeader> headers = new HashMap<>();
        private final Map<String, List<ZipEntryInfo>> folders = new HashMap<>();

        private static final class FolderDetails {
            long size;
            long recordedModified;
            long descendantModified;
        }

        private Archive(File file) throws IOException {
            if (!file.isFile()) throw new IOException("Archive no longer exists: " + file);
            reader = io.github.abdurazaaqmohammed.domain.files.ArchivePasswords.open(file);
            folders.put("", new ArrayList<>());
            Map<String, FolderDetails> folderDetails = new HashMap<>();
            try {
                for (FileHeader header : reader.getFileHeaders()) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                    String path = header.getFileName().replace('\\', '/');
                    if (path.isEmpty()) continue;
                    headers.put(path, header);
                    ZipEntryInfo entry = new ZipEntryInfo(header, file, "");
                    String parent = "";
                    int slash = path.indexOf('/');
                    while (slash >= 0) {
                        String directory = path.substring(0, slash + 1);
                        FolderDetails details = folderDetails.computeIfAbsent(directory, ignored -> new FolderDetails());
                        if (!entry.isDirectory()) {
                            details.size = details.size > Long.MAX_VALUE - entry.getSize()
                                    ? Long.MAX_VALUE : details.size + entry.getSize();
                        }
                        details.descendantModified = Math.max(details.descendantModified, entry.getLastModified());
                        if (!folders.containsKey(directory)) {
                            folders.put(directory, new ArrayList<>());
                            folders.get(parent).add(new ZipEntryInfo(
                                    path.substring(parent.length(), slash), directory,
                                    true, 0L, 0L, file));
                        }
                        parent = directory;
                        slash = path.indexOf('/', slash + 1);
                    }
                    if (entry.isDirectory()) {
                        folderDetails.computeIfAbsent(folderPath(path), ignored -> new FolderDetails())
                                .recordedModified = entry.getLastModified();
                    }
                    if (!path.endsWith("/")) {
                        if (header.isDirectory()) {
                            String directory = path + "/";
                            if (!folders.containsKey(directory)) {
                                folders.put(directory, new ArrayList<>());
                                folders.get(parent).add(entry);
                            }
                        } else {
                            folders.get(parent).add(entry);
                        }
                    }
                }
                // APKs commonly omit directory headers. Publish immutable folder rows
                // using descendant metadata, retaining explicit dates regardless of ZIP order.
                // Compute once while indexing, never by extracting or rescanning on each row.
                // AI-assisted contribution: OpenAI Codex.
                for (List<ZipEntryInfo> children : folders.values()) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                    for (int index = 0; index < children.size(); index++) {
                        ZipEntryInfo entry = children.get(index);
                        if (!entry.isDirectory()) continue;
                        String directory = folderPath(entry.getFullPath());
                        FolderDetails details = folderDetails.get(directory);
                        if (details == null) continue;
                        long modified = details.recordedModified > 0
                                ? details.recordedModified : details.descendantModified;
                        children.set(index, new ZipEntryInfo(entry.getName(), directory, true,
                                details.size, modified, file));
                    }
                }
            } finally {
                reader.close();
            }
        }

        /** Only direct children: navigation never rescans the archive's other entries. */
        public List<ZipEntryInfo> children(String path) {
            List<ZipEntryInfo> entries = folders.get(folderPath(path));
            return entries == null ? Collections.emptyList() : Collections.unmodifiableList(entries);
        }

        public boolean contains(String path) {
            return headers.containsKey(path.replace('\\', '/'));
        }

        /** Reuse parsed headers, but close every file stream after copying. */
        public synchronized void copyEntry(String path, File destination) throws IOException {
            FileHeader header = headers.get(path.replace('\\', '/'));
            if (header == null || header.isDirectory()) throw new IOException("Entry no longer exists: " + path);
            boolean complete = false;
            try {
                try (InputStream input = reader.getInputStream(header);
                     FileOutputStream output = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                        output.write(buffer, 0, count);
                    }
                }
                complete = true;
            } finally {
                // zip4j retains references to closed streams until close() is called.
                reader.close();
                if (!complete) destination.delete();
            }
        }
    }
}
