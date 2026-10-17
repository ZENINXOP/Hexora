package io.github.abdurazaaqmohammed.utils;

import androidx.appcompat.app.AppCompatActivity;
import io.github.abdurazaaqmohammed.domain.files.SafeFileOperations;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFileOps;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/** Destination selection is explicit; source deletion happens only after a verified replacement commits. */
public final class FileTransfers {
    private FileTransfers() { }
    public interface BeforeCopy { void run() throws IOException; }

    public static void transfer(AppCompatActivity activity, File source, File folder, boolean move) throws IOException {
        transfer(activity, source, folder, move, () -> { });
    }

    public static void transfer(AppCompatActivity activity, File source, File folder, boolean move, BeforeCopy beforeCopy) throws IOException {
        File desired = new File(folder, source.getName());
        if (!source.getCanonicalPath().equals(desired.getCanonicalPath())) SafeFileOperations.validateDestination(source, desired);
        FileConflictResolver.Choice choice = FileConflictResolver.resolve(activity, source, desired);
        // Replacing an item with itself is already complete. Do not move it aside,
        // copy over its own bytes, or delete it as the source of a move.
        if (choice.replace() && source.getCanonicalPath().equals(choice.destination().getCanonicalPath())) return;
        boolean[] renamed = {false};
        FileConflictResolver.apply(activity, choice, destination -> {
            if (move && !LocalStorage.isPath(source) && !LocalStorage.isPath(destination)) {
                try {
                    AccessManager.rename(activity, source.getAbsolutePath(), destination.getAbsolutePath(), true);
                    renamed[0] = true;
                    return;
                } catch (IOException ignored) { }
            }
            beforeCopy.run();
            copy(activity, source, destination);
            if (move) verify(activity, source, FileConflictResolver.refresh(destination), new java.util.HashSet<>());
        });
        if (move && !renamed[0]) {
            // Once committed, keep the complete new copy even if removing the source fails.
            if (LocalStorage.isPath(source)) SafeFileOperations.delete(LocalStorage.file(source));
            else if (new File(source.getAbsolutePath()).canWrite()) SafeFileOperations.delete(source);
            else AccessManager.delete(activity, source.getAbsolutePath(), true);
        }
    }

    private static void copy(AppCompatActivity activity, File source, File destination) throws IOException {
        if (LocalStorage.isPath(source) || LocalStorage.isPath(destination)) {
            LocalStorage.transferTo(source, destination, false);
            return;
        }
        File plainSource = new File(source.getAbsolutePath());
        File parent = destination.getParentFile();
        if (plainSource.canRead() && parent != null && parent.canWrite()) {
            if (plainSource.isDirectory()) SafeFileOperations.copyDirectory(plainSource, destination);
            else SafeFileOperations.copyFile(plainSource, destination);
            return;
        }
        if (AccessManager.fileOpsOn(activity)) {
            if (source.isDirectory()) AccessManager.copyDir(activity, source.getAbsolutePath(), destination.getAbsolutePath(), true);
            else AccessManager.copyFile(activity, source.getAbsolutePath(), destination.getAbsolutePath(), true);
            AccessManager.preserveTime(activity, source.getAbsolutePath(), destination.getAbsolutePath());
            return;
        }
        if (ShizukuFileOps.involvesShizukuPath(source, parent)
                && ShizukuFileOps.shellCopy(source, parent, destination.getName()) != null) return;
        throw new IOException("Cannot copy " + source.getName() + ". Check folder access.");
    }

    private static File[] children(AppCompatActivity activity, File directory) throws IOException {
        File[] children = directory.listFiles();
        if (children == null) children = AccessManager.listWithStat(activity, directory.getAbsolutePath());
        if (children == null) throw new IOException("Cannot verify folder: " + directory);
        return children;
    }

    private static void verify(AppCompatActivity activity, File source, File destination, java.util.Set<String> ancestors) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Operation cancelled.");
        if (source.isDirectory()) {
            String identity = source.getCanonicalPath();
            if (!ancestors.add(identity)) throw new IOException("Directory cycle: " + source);
            try {
                File[] originals = children(activity, source), copies = children(activity, destination);
                if (originals.length != copies.length) throw new IOException("Incomplete copy: " + source);
                java.util.Map<String, File> copied = new java.util.HashMap<>();
                for (File file : copies) if (copied.put(file.getName(), file) != null) throw new IOException("Duplicate destination name.");
                for (File child : originals) {
                    File target = copied.remove(child.getName());
                    if (target == null || child.isDirectory() != target.isDirectory()) throw new IOException("Incomplete copy: " + child);
                    verify(activity, child, target, ancestors);
                }
            } finally { ancestors.remove(identity); }
        } else try (InputStream left = RootStaging.openInputStream(activity, source);
                    InputStream right = RootStaging.openInputStream(activity, destination)) {
            byte[] a = new byte[256 * 1024], b = new byte[a.length];
            int count;
            while ((count = left.read(a)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Operation cancelled.");
                int offset = 0;
                while (offset < count) {
                    int read = right.read(b, offset, count - offset);
                    if (read < 0) throw new IOException("Incomplete copy: " + source);
                    offset += read;
                }
                if (!java.util.Arrays.equals(a, b)) throw new IOException("Copy verification failed: " + source);
                io.github.abdurazaaqmohammed.domain.files.FileWorkProgress.advance(count);
            }
            if (right.read() != -1) throw new IOException("Copy verification failed: " + source);
        }
        io.github.abdurazaaqmohammed.domain.files.FileWorkProgress.advance(1);
    }
}
