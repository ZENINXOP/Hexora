package io.github.abdurazaaqmohammed.domain.files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/** Local file operations shared by the browser and its regression tests. */
public final class SafeFileOperations {
    private SafeFileOperations() { }

    public interface AtomicReplacement {
        void replace(File temporary, File destination) throws IOException;
    }

    /** Replaces a local document without colliding with its user-visible .bak backup. */
    public static void writeAtomically(File destination, byte[] bytes, AtomicReplacement replacement) throws IOException {
        checkInterrupted();
        File parent = destination.getAbsoluteFile().getParentFile();
        File temporary = File.createTempFile(".hexora-save-", ".tmp", parent);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes);
                output.getFD().sync();
            }
            checkInterrupted();
            replacement.replace(temporary, destination);
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    public static File unusedFile(File file) {
        if (!file.exists()) return file;
        String name = file.getName();
        int dot = file.isDirectory() ? -1 : name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        int number = 1;
        File candidate;
        do { candidate = new File(file.getParentFile(), base + "_" + number++ + extension); }
        while (candidate.exists());
        return candidate;
    }

    /** Restores a backup by swapping versions, checking every rename and retaining recovery data. */
    public static void restoreBackup(File backup, File destination) throws IOException {
        if (!backup.isFile()) throw new IOException("Backup is not a file: " + backup);
        validateDestination(backup, destination);
        if (!backup.getCanonicalFile().getParentFile().equals(destination.getCanonicalFile().getParentFile())) {
            throw new IOException("Restore the backup in its original folder.");
        }
        if (!destination.exists()) {
            if (!backup.renameTo(destination)) throw new IOException("Cannot restore backup: " + backup);
            return;
        }
        if (!destination.isFile()) throw new IOException("Destination is not a file: " + destination);
        File previous = File.createTempFile(".hexora-restore-", ".bak", backup.getAbsoluteFile().getParentFile());
        if (!previous.delete()) throw new IOException("Cannot prepare restore: " + previous);
        if (!destination.renameTo(previous)) throw new IOException("Cannot preserve original: " + destination);
        if (!backup.renameTo(destination)) {
            if (!previous.renameTo(destination)) throw new IOException("Restore failed; previous version retained at " + previous);
            throw new IOException("Cannot restore backup; original retained: " + backup);
        }
        if (!previous.renameTo(backup)) {
            throw new IOException("Backup restored; previous version retained at " + previous);
        }
    }

    public static void validateName(String name) throws IOException {
        if (name == null || name.trim().isEmpty() || ".".equals(name) || "..".equals(name)
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            throw new IOException("Enter a file name without path separators.");
        }
    }

    public static void validateDestination(File source, File destination) throws IOException {
        String src = source.getCanonicalPath();
        String dst = destination.getCanonicalPath();
        if (dst.equals(src) || (source.isDirectory() && dst.startsWith(src + File.separator))) {
            throw new IOException("A folder cannot be copied or moved into itself.");
        }
    }

    /** Copies the contents into destination, preserving subfolders and empty directories. */
    public static void copyDirectory(File source, File destination) throws IOException {
        validateDestination(source, destination);
        copyDirectory(source, destination, new HashSet<>());
    }

    private static void copyDirectory(File source, File destination, Set<String> ancestors) throws IOException {
        checkInterrupted();
        if (!source.isDirectory()) throw new IOException("Not a directory: " + source);
        if (!ancestors.add(source.getCanonicalPath())) throw new IOException("Directory cycle: " + source);
        try {
            File[] children = source.listFiles();
            if (children == null) throw new IOException("Cannot read directory: " + source);
            if (!destination.isDirectory() && !destination.mkdirs()) {
                throw new IOException("Cannot create directory: " + destination);
            }
            for (File child : children) {
                File target = new File(destination, child.getName());
                if (target.exists()) throw new IOException("Destination already exists: " + target);
                if (child.isDirectory()) copyDirectory(child, target, ancestors);
                else copyFile(child, target);
            }
            destination.setLastModified(source.lastModified());
        } finally {
            ancestors.remove(source.getCanonicalPath());
        }
    }

    /** Publishes a complete file only; failures retain the source and existing destination. */
    public static void copyFile(File source, File destination) throws IOException {
        validateDestination(source, destination);
        if (destination.exists()) throw new IOException("Destination already exists: " + destination);
        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent == null || !parent.isDirectory()) throw new IOException("Cannot write to: " + parent);
        File temporary = File.createTempFile(".hexora-copy-", ".tmp", parent);
        try {
            long expectedSize = source.length();
            long expectedTime = source.lastModified();
            long copied = 0;
            try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    checkInterrupted();
                    out.write(buffer, 0, count);
                    copied += count;
                }
                out.getFD().sync();
            }
            if (copied != expectedSize || source.length() != expectedSize || source.lastModified() != expectedTime) {
                throw new IOException("Source changed while copying: " + source);
            }
            if (destination.exists() || !temporary.renameTo(destination)) {
                throw new IOException("Cannot finish copy: " + destination);
            }
            destination.setLastModified(expectedTime);
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    /** Check every relative path and byte before a cross-volume move deletes its source. */
    public static void verifyCopy(File source, File destination) throws IOException {
        checkInterrupted();
        if (source.isDirectory()) {
            File[] children = source.listFiles();
            File[] copied = destination.listFiles();
            if (children == null || copied == null || children.length != copied.length) {
                throw new IOException("Incomplete directory copy: " + source);
            }
            for (File child : children) verifyCopy(child, new File(destination, child.getName()));
        } else {
            if (!destination.isFile() || source.length() != destination.length()) {
                throw new IOException("Incomplete file copy: " + source);
            }
            try (InputStream left = new FileInputStream(source); InputStream right = new FileInputStream(destination)) {
                byte[] a = new byte[64 * 1024];
                byte[] b = new byte[a.length];
                int count;
                while ((count = left.read(a)) != -1) {
                    checkInterrupted();
                    int offset = 0;
                    while (offset < count) {
                        int read = right.read(b, offset, count - offset);
                        if (read < 0) throw new IOException("Incomplete file copy: " + source);
                        offset += read;
                    }
                    for (int i = 0; i < count; i++) {
                        if (a[i] != b[i]) throw new IOException("Copy verification failed: " + source);
                    }
                }
                if (right.read() != -1) throw new IOException("Copy verification failed: " + source);
            }
        }
    }

    public static void delete(File file) throws IOException {
        checkInterrupted();
        // Never traverse a symbolic link into a different directory.
        File parent = file.getAbsoluteFile().getParentFile();
        boolean link = parent != null && !new File(parent.getCanonicalFile(), file.getName()).equals(file.getCanonicalFile());
        if (file.isDirectory() && !link) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot read directory: " + file);
            for (File child : children) delete(child);
        }
        if (!file.delete()) throw new IOException("Cannot delete: " + file);
    }

    private static void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Operation cancelled.");
    }
}
