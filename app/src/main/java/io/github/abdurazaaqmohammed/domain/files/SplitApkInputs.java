package io.github.abdurazaaqmohammed.domain.files;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

/** Extracts only APK members, flattening nested bundle paths without trusting archive names. */
public final class SplitApkInputs {
    private SplitApkInputs() { }

    public static int extract(File archive, File directory, List<String> selected) throws IOException {
        Set<String> wanted = selected == null ? null : new HashSet<>(selected);
        Set<String> seen = new HashSet<>();
        int count = 0;
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create split staging folder.");
        try (ZipFile zip = ArchivePasswords.open(archive)) {
            for (FileHeader entry : zip.getFileHeaders()) {
                String name = entry.getFileName();
                if (entry.isDirectory() || !name.toLowerCase(Locale.ROOT).endsWith(".apk")
                        || (wanted != null && !wanted.contains(name))) continue;
                if (!seen.add(name)) throw new IOException("Duplicate split entry: " + name);
                String shortName = SharedFileCopy.safeName(name, null);
                shortName = shortName.substring(0, shortName.length() - 4) + ".apk";
                long space = directory.getUsableSpace();
                long budget = space > 0 ? Math.max(0, space - 16L * 1024 * 1024) : Long.MAX_VALUE;
                try (InputStream input = zip.getInputStream(entry)) {
                    SharedFileCopy.copy(input, directory, count + "_" + shortName, budget);
                }
                count++;
            }
        }
        if (count == 0) throw new IOException("No APK splits found.");
        if (wanted != null && !seen.containsAll(wanted)) throw new IOException("A selected APK split is missing from the bundle.");
        return count;
    }
}
