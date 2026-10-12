package io.github.abdurazaaqmohammed.domain.files;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** In-app file selection; no Activity, credentials or system text clipboard data. */
public final class FileClipboard {
    public static final FileClipboard SESSION=new FileClipboard();
    public record Snapshot(long generation,List<File> files,boolean cut) { }
    private long generation;
    private Snapshot current=new Snapshot(0,Collections.emptyList(),false);
    public synchronized Snapshot snapshot() { return current; }
    public synchronized void clear() { current=new Snapshot(++generation,Collections.emptyList(),false); }
    private static String path(File file) throws IOException { return file.getCanonicalPath(); }
    public synchronized void set(Collection<File> files,boolean cut) throws IOException {
        LinkedHashMap<String,File> unique=new LinkedHashMap<>();
        for (File file:files) unique.putIfAbsent(path(file),file);
        List<File> roots=new ArrayList<>();
        for (Map.Entry<String,File> candidate:unique.entrySet()) {
            boolean nested=false;
            for (Map.Entry<String,File> other:unique.entrySet()) if (other.getValue().isDirectory()
                    && candidate.getKey().startsWith(other.getKey()+File.separator)) { nested=true; break; }
            if (!nested) roots.add(candidate.getValue());
        }
        if (roots.isEmpty()) throw new IOException("Select files or folders first.");
        current=new Snapshot(++generation,Collections.unmodifiableList(roots),cut);
    }
    /** A failed batch retains unfinished cut items; a newer selection is never cleared. */
    public synchronized void completed(Snapshot job,Collection<File> completed) {
        if (!job.cut() || current.generation()!=job.generation()) return;
        List<File> remaining=new ArrayList<>(current.files()); remaining.removeAll(completed);
        current=new Snapshot(++generation,Collections.unmodifiableList(remaining),true);
    }
}
