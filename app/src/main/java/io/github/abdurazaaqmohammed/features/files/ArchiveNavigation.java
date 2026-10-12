package io.github.abdurazaaqmohammed.features.files;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Logical parents for staged archives. Physical cache directories are never navigation parents. */
public final class ArchiveNavigation {
    public record Origin(NavigationHistoryEntry parent, String name) { }
    private final Map<String, Origin> origins = new HashMap<>();

    public void register(File readable, File original, File containingArchive, String entryPath) {
        NavigationHistoryEntry parent;
        String name;
        if (containingArchive != null && entryPath != null) {
            int slash = entryPath.lastIndexOf('/');
            parent = new NavigationHistoryEntry(containingArchive, true,
                    slash < 0 ? "" : entryPath.substring(0, slash + 1));
            name = new File(entryPath).getName();
        } else {
            parent = new NavigationHistoryEntry(original.getAbsoluteFile().getParentFile(), false, null);
            name = original.getName();
        }
        origins.put(readable.getAbsolutePath(), new Origin(parent, name));
    }

    public NavigationHistoryEntry parent(File archive) {
        Origin origin = origins.get(archive.getAbsolutePath());
        return origin == null ? new NavigationHistoryEntry(archive.getAbsoluteFile().getParentFile(), false, null)
                : origin.parent();
    }

    public String displayPath(File archive) { return displayPath(archive, new HashSet<>()); }

    private String displayPath(File archive, Set<String> visited) {
        if (!visited.add(archive.getAbsolutePath())) return archive.getName();
        Origin origin = origins.get(archive.getAbsolutePath());
        if (origin == null) return archive.getPath();
        NavigationHistoryEntry parent = origin.parent();
        if (parent.file() == null) return origin.name();
        return parent.isZip() ? displayPath(parent.file(), visited) + "!" + parent.zipPath() + origin.name()
                : new File(parent.file(), origin.name()).getPath();
    }

    public Map<String, Origin> snapshot() { return new HashMap<>(origins); }
    public void restore(Map<String, Origin> saved) { origins.putAll(saved); }
}
