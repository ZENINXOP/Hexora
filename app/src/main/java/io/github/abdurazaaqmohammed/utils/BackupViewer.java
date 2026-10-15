package io.github.abdurazaaqmohammed.utils;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Materializes a viewable ZIP without modifying or restoring the original backup. */
public final class BackupViewer {
    private BackupViewer() { }

    public static File convert(android.content.Context context, File backup) throws IOException {
        File directory = SharedFileCache.directory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot prepare the backup viewer.");
        File output = File.createTempFile("xiaomi-backup-", ".zip", directory);
        boolean complete = false;
        try (InputStream source = FileUtils.getInputStream(backup);
             TarArchiveInputStream tar = new TarArchiveInputStream(io.github.abdurazaaqmohammed.domain.files.AndroidBackup.openTar(source));
             ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
            zip.setLevel(1);
            Set<String> names = new HashSet<>();
            byte[] buffer = new byte[256 * 1024];
            TarArchiveEntry entry;
            long total = 0;
            int entries = 0;
            long available = context.getCacheDir().getUsableSpace();
            long budget = available > 0 ? Math.max(0, available - 16L * 1024 * 1024) : Long.MAX_VALUE;
            while ((entry = tar.getNextTarEntry()) != null) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Opening backup cancelled.");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.matches("^[A-Za-z]:.*") || name.indexOf('\0') >= 0)
                    throw new IOException("Invalid backup entry path.");
                for (String part : name.split("/")) if (part.equals("..")) throw new IOException("Backup entry escapes its folder.");
                if (entry.isSymbolicLink() || entry.isLink()) continue; // Viewing must not follow private-data links.
                if (!entry.isFile() && !entry.isDirectory()) throw new IOException("Unsupported backup entry: " + name);
                if (name.isEmpty()) throw new IOException("Backup entry has no name.");
                if (entry.isDirectory() && !name.endsWith("/")) name += "/";
                if (!names.add(name) || ++entries > 100_000) throw new IOException("Duplicate or excessive backup entries.");
                ZipEntry target = new ZipEntry(name);
                if (entry.getLastModifiedDate() != null) target.setTime(entry.getLastModifiedDate().getTime());
                zip.putNextEntry(target);
                int count;
                while ((count = tar.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Opening backup cancelled.");
                    total += count;
                    if (total > budget) throw new IOException("Not enough free space to open this backup.");
                    zip.write(buffer, 0, count);
                }
                zip.closeEntry();
            }
            if (entries == 0) throw new IOException("No readable files were found in this backup.");
            zip.finish();
            complete = true;
            return output;
        } finally { if (!complete) output.delete(); }
    }
}
