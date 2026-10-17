package io.github.abdurazaaqmohammed.domain.files;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Pure file-search logic extracted from MainActivity.
 * Directory listing is injected so callers can fall back to
 * elevated (root/Shizuku) listings on unreadable folders.
 */
public final class FileSearch {

    /** Max hits/content size guards, kept from the original implementation. */
    public static final int MAX_HITS = 500;
    public static final long MAX_CONTENT_BYTES = 2097152;
    public static final int MAX_LINES_PER_FILE = 20000;
    public static final int SNIPPET_LEN = 140;

    private FileSearch() {
    }

    public static List<File> searchByName(File dir, String query, boolean subfolders,
            boolean matchCase, boolean regex, String textInside,
            long minSize, long maxSize, Function<File, File[]> lister) {
        List<File> results = new ArrayList<>();
        Pattern pattern = null;
        if (regex) {
            try {
                pattern = SearchPattern.compile(query, matchCase ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                return results;
            }
        }
        String normalized = matchCase ? query : query.toLowerCase(java.util.Locale.ROOT);
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<File> pending = new java.util.ArrayDeque<>();
        if (dir != null) pending.push(dir);
        while (!pending.isEmpty() && results.size() < MAX_HITS && !Thread.currentThread().isInterrupted()) {
            searchDirectory(pending.pop(), results, normalized, subfolders, matchCase, regex,
                    pattern, textInside, minSize, maxSize, lister, visited, pending);
        }
        return results;
    }

    private static void searchDirectory(File dir, List<File> results, String query,
            boolean subfolders, boolean mCase, boolean regex, Pattern pattern,
            String textInside, long minSize, long maxSize, Function<File, File[]> lister, java.util.Set<String> visited,
            java.util.ArrayDeque<File> pending) {
        if (dir == null || Thread.currentThread().isInterrupted()) return;
        try { if (!visited.add(dir.getCanonicalPath())) return; } catch (java.io.IOException e) { return; }
        File[] files = dir.listFiles();
        if (files == null) {
            try {
                files = lister.apply(dir);
            } catch (Exception ignored) {
            }
            if (files == null) return;
        }
        for (File f : files) {
            if (results.size() >= MAX_HITS || Thread.currentThread().isInterrupted()) return;
            boolean matchName;
            String name = f.getName();
            if (regex && pattern != null) {
                matchName = SearchPattern.find(pattern, name);
            } else {
                matchName = mCase ? name.contains(query) : name.toLowerCase(java.util.Locale.ROOT).contains(query);
            }

            boolean matchSize = true;
            if (f.isFile() && (minSize != -1 || maxSize != -1)) {
                long len = f.length();
                if (minSize != -1 && len < minSize) matchSize = false;
                if (maxSize != -1 && len > maxSize) matchSize = false;
            }

            boolean matchText = textInside == null || textInside.isEmpty();
            if (matchName && matchSize && f.isFile() && textInside != null && !textInside.isEmpty()) {
                matchText = false;
                if (f.length() < 10485760) { // Limit to 10MB files to prevent OOM
                    try (BoundedLines br = new BoundedLines(f, 10 * 1024 * 1024)) {
                        String line;
                        String needle = mCase ? textInside : textInside.toLowerCase(java.util.Locale.ROOT);
                        int lines = 0;
                        while (++lines <= MAX_LINES_PER_FILE && !Thread.currentThread().isInterrupted() && (line = br.readLine()) != null) {
                            if (mCase ? line.contains(textInside)
                                    : line.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                                matchText = true;
                                break;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            if (matchName && matchSize && matchText) results.add(f);

            if (subfolders && f.isDirectory()) {
                pending.push(f);
            }
        }
    }

    public static List<ContentHit> findInFiles(File dir, String needle,
            boolean matchCase, boolean regex, Progress progress) {
        List<ContentHit> hits = new ArrayList<>();
        Pattern pattern = null;
        if (regex) {
            try {
                pattern = SearchPattern.compile(needle, matchCase ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                return hits;
            }
        }
        String normNeedle = matchCase ? needle : needle.toLowerCase(java.util.Locale.ROOT);
        int[] scanned = {0};
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<File> pending = new java.util.ArrayDeque<>();
        if (dir != null) pending.push(dir);
        while (!pending.isEmpty() && hits.size() < MAX_HITS && !Thread.currentThread().isInterrupted()) {
            findDirectory(pending.pop(), normNeedle, matchCase, regex, pattern, hits, scanned, progress, visited, pending);
        }
        return hits;
    }

    private static void findDirectory(File dir, String needle, boolean matchCase,
            boolean regex, Pattern pattern, List<ContentHit> hits, int[] scanned,
            Progress progress, java.util.Set<String> visited, java.util.ArrayDeque<File> pending) {
        if (dir == null || Thread.currentThread().isInterrupted()) return;
        try { if (!visited.add(dir.getCanonicalPath())) return; } catch (java.io.IOException e) { return; }
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (hits.size() >= MAX_HITS || Thread.currentThread().isInterrupted()) return;
            if (f.isDirectory()) {
                pending.push(f);
            } else if (f.isFile() && f.length() < MAX_CONTENT_BYTES) {
                scanned[0]++;
                if (scanned[0] % 50 == 0 && progress != null) progress.onProgress(scanned[0]);
                try (BoundedLines br = new BoundedLines(f, (int) MAX_CONTENT_BYTES)) {
                    String line;
                    int lineNo = 0;
                    while (!Thread.currentThread().isInterrupted() && (line = br.readLine()) != null) {
                        lineNo++;
                        if (lineNo == 1 && line.indexOf('\0') >= 0) break;
                        boolean hit;
                        if (regex && pattern != null) hit = SearchPattern.find(pattern, line);
                        else hit = matchCase ? line.contains(needle) : line.toLowerCase(java.util.Locale.ROOT).contains(needle);
                        if (hit) {
                            String snippet = line.trim();
                            if (snippet.length() > SNIPPET_LEN)
                                snippet = snippet.substring(0, SNIPPET_LEN) + "\u2026";
                            hits.add(new ContentHit(f, lineNo, snippet));
                            if (hits.size() >= MAX_HITS || Thread.currentThread().isInterrupted()) return;
                        }
                        if (lineNo >= MAX_LINES_PER_FILE) break;
                    }
                } catch (java.io.IOException | SecurityException ignored) {
                }
            }
        }
    }

    /** A growing file or a virtual file reporting length zero must still be bounded. */
    private static final class BoundedLines implements java.io.Closeable {
        private final BufferedReader reader;
        private int remaining;
        private boolean skipLf;

        BoundedLines(File file, int maxCharacters) throws java.io.IOException {
            reader = new BufferedReader(new FileReader(file));
            remaining = maxCharacters;
        }

        String readLine() throws java.io.IOException {
            StringBuilder line = new StringBuilder();
            while (remaining-- > 0) {
                if ((remaining & 4095) == 0 && Thread.currentThread().isInterrupted())
                    throw new java.io.InterruptedIOException();
                int ch = reader.read();
                if (ch < 0) return line.length() == 0 ? null : line.toString();
                if (skipLf) {
                    skipLf = false;
                    if (ch == '\n') continue;
                }
                if (ch == '\r') { skipLf = true; return line.toString(); }
                if (ch == '\n') return line.toString();
                if (line.length() >= 64 * 1024) throw new java.io.IOException("Search line limit exceeded");
                line.append((char) ch);
            }
            return line.length() == 0 ? null : line.toString();
        }

        @Override public void close() throws java.io.IOException { reader.close(); }
    }

    /** Called from worker threads; the caller marshals to the UI thread. */
    public interface Progress {
        void onProgress(int scannedFiles);
    }
}
