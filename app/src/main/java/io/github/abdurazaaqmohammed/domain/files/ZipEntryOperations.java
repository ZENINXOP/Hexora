package io.github.abdurazaaqmohammed.domain.files;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

public final class ZipEntryOperations {
    private ZipEntryOperations() { }

    public static void rename(File archive, String fullPath, boolean directory, String newName) throws IOException {
        SafeFileOperations.validateName(newName);
        if (fullPath == null) throw new IOException("Cannot rename the parent entry.");
        String old = fullPath.replace('\\', '/');
        if (directory && old.endsWith("/")) old = old.substring(0, old.length() - 1);
        int slash = old.lastIndexOf('/');
        String target = (slash < 0 ? "" : old.substring(0, slash + 1)) + newName;
        if (old.equals(target)) return;
        try (ZipFile zip = new ZipFile(archive)) {
            for (FileHeader header : zip.getFileHeaders()) {
                String name = header.getFileName().replace('\\', '/');
                if (name.equals(target) || name.startsWith(target + "/")) {
                    throw new IOException("Destination already exists: " + newName);
                }
            }
            if (!directory) {
                FileHeader header = zip.getFileHeader(fullPath);
                if (header == null) throw new IOException("Archive entry no longer exists: " + fullPath);
                zip.renameFile(header, target);
            } else {
                Map<String, String> renames = new LinkedHashMap<>();
                for (FileHeader header : zip.getFileHeaders()) {
                    String name = header.getFileName().replace('\\', '/');
                    if (name.equals(old + "/") || name.startsWith(old + "/")) {
                        renames.put(header.getFileName(), target + name.substring(old.length()));
                    }
                }
                if (renames.isEmpty()) throw new IOException("Archive folder no longer exists: " + fullPath);
                zip.renameFiles(renames);
            }
        }
    }
}
