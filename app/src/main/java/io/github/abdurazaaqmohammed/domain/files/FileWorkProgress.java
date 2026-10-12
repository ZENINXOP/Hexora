package io.github.abdurazaaqmohammed.domain.files;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Worker-local progress hooks: bytes plus published entries, or entries for deletion. */
public final class FileWorkProgress {
    public interface ReadableFile { java.io.InputStream openInput() throws IOException; }
    public static java.io.InputStream openInput(File file) throws IOException {
        return file instanceof ReadableFile ? ((ReadableFile) file).openInput() : new java.io.FileInputStream(file);
    }
    public interface Listener { void advanced(long units); }
    private static final ThreadLocal<Listener> LISTENER = new ThreadLocal<>();
    private FileWorkProgress() { }

    public static final class Scope implements AutoCloseable {
        private final Listener previous;
        private Scope(Listener listener) { previous = LISTENER.get(); LISTENER.set(listener); }
        @Override public void close() { if (previous == null) LISTENER.remove(); else LISTENER.set(previous); }
    }
    public static Scope track(Listener listener) { return new Scope(listener); }
    public static void advance(long units) {
        Listener listener = LISTENER.get();
        if (listener != null && units > 0) listener.advanced(units);
    }
    public static long copyUnits(File file) throws IOException { return measure(file, true, new HashSet<>()); }
    public static long deleteUnits(File file) throws IOException { return measure(file, false, new HashSet<>()); }
    private static long measure(File file, boolean bytes, Set<String> ancestors) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Operation cancelled.");
        File parent = file.getAbsoluteFile().getParentFile();
        boolean link = parent != null && !new File(parent.getCanonicalFile(), file.getName()).equals(file.getCanonicalFile());
        if (!file.isDirectory() || (!bytes && link)) return bytes && file.isFile() ? Math.max(0, file.length()) + 1 : 1;
        String path = file.getCanonicalPath();
        if (!ancestors.add(path)) throw new IOException("Directory cycle: " + file);
        try {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot count directory: " + file);
            long total = 1;
            for (File child : children) total = Math.addExact(total, measure(child, bytes, ancestors));
            return total;
        } finally { ancestors.remove(path); }
    }
}
