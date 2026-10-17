package io.github.abdurazaaqmohammed.utils;

import android.os.Looper;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.domain.files.SafeFileOperations;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Worker-side collision prompt and recoverable replacement, shared by rename/copy/move. */
public final class FileConflictResolver {
    private FileConflictResolver() { }
    public record Choice(File destination, boolean replace) { }
    public interface Operation { void run(File destination) throws IOException; }

    public static boolean exists(AppCompatActivity activity, File file) {
        if (LocalStorage.isPath(file)) return LocalStorage.resolve(file, false) != null;
        File plain = new File(file.getAbsolutePath());
        if (plain.exists()) return true;
        android.net.Uri granted = SafAccess.resolve(file, false);
        if (granted != null) {
            try { SafAccess.stat(file, granted); return true; }
            catch (IOException missing) { }
        }
        if (plain.getParentFile() != null && plain.getParentFile().canRead()) return false;
        return AccessManager.fileOpsOn(activity) && AccessManager.exists(activity, file.getAbsolutePath());
    }

    public static File unused(AppCompatActivity activity, File desired, boolean directory) {
        String name = desired.getName();
        int dot = directory ? -1 : name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, suffix = dot > 0 ? name.substring(dot) : "";
        File candidate = desired;
        for (int i = 1; exists(activity, candidate); i++) candidate = new File(desired.getParentFile(), base + "_" + i + suffix);
        return candidate;
    }

    public static Choice resolve(AppCompatActivity activity, File source, File desired) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Resolve conflicts on a worker.");
        boolean same = source.getCanonicalPath().equals(desired.getCanonicalPath());
        if (!same && !exists(activity, desired)) return new Choice(desired, false);
        int answer = choose(activity, desired.getName(), same);
        return answer == 0 ? new Choice(desired, true) : new Choice(unused(activity, desired, source.isDirectory()), false);
    }

    public static Choice resolveDestination(AppCompatActivity activity, File desired, boolean directory) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Resolve conflicts on a worker.");
        if (!exists(activity, desired)) return new Choice(desired, false);
        return choose(activity, desired.getName(), false) == 0 ? new Choice(desired, true)
                : new Choice(unused(activity, desired, directory), false);
    }

    /** 0 = replace, 1 = keep both. Closing/cancelling interrupts the pending operation. */
    public static int choose(AppCompatActivity activity, String name, boolean same) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Resolve conflicts on a worker.");
        CountDownLatch answered = new CountDownLatch(1);
        int[] answer = {-1};
        AlertDialog[] prompt = {null};
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) { answered.countDown(); return; }
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.file_conflict_title)
                    .setMessage(activity.getString(same ? R.string.file_conflict_same_source : R.string.file_conflict_message, name))
                    .setNegativeButton(android.R.string.cancel, (d, which) -> answered.countDown())
                    .setNeutralButton(R.string.keep_both, (d, which) -> { answer[0] = 1; answered.countDown(); });
            builder.setPositiveButton(R.string.replace, (d, which) -> { answer[0] = 0; answered.countDown(); });
            prompt[0] = builder.create();
            prompt[0].setOnDismissListener(d -> answered.countDown());
            prompt[0].show();
        });
        try {
            while (!answered.await(100, TimeUnit.MILLISECONDS))
                if (activity.isFinishing() || activity.isDestroyed()) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Operation cancelled.");
                }
            if (Thread.currentThread().isInterrupted() || answer[0] < 0) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Operation cancelled.");
            }
            return answer[0];
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Operation cancelled.");
        } finally {
            activity.runOnUiThread(() -> { if (prompt[0] != null) prompt[0].dismiss(); });
        }
    }

    public static File refresh(File file) throws IOException {
        if (LocalStorage.isPath(file)) return LocalStorage.file(file);
        if (new File(file.getAbsolutePath()).canRead()) return file;
        android.net.Uri granted = SafAccess.resolve(file, false);
        return granted == null ? file : SafAccess.stat(file, granted);
    }

    public static void apply(AppCompatActivity activity, Choice choice, Operation operation) throws IOException {
        io.github.abdurazaaqmohammed.domain.files.ReplacementTransaction.run(choice.destination(), choice.replace(),
                new io.github.abdurazaaqmohammed.domain.files.ReplacementTransaction.Storage() {
                    @Override public boolean exists(File file) { return FileConflictResolver.exists(activity, file); }
                    @Override public void rename(File source, File target) throws IOException {
                        AccessManager.rename(activity, source.getAbsolutePath(), target.getAbsolutePath(), true);
                    }
                    @Override public void remove(File file) throws IOException { FileConflictResolver.remove(activity, file); }
                }, operation::run);
    }

    private static void remove(AppCompatActivity activity, File file) throws IOException {
        IOException localError = null;
        try { SafeFileOperations.delete(refresh(file)); }
        catch (IOException error) { localError = error; }
        // Raw File.exists() can be false for a still-present elevated file.
        // Confirm absence through the same storage used to resolve the conflict.
        if (!exists(activity, file)) return;
        if (LocalStorage.isPath(file) || !AccessManager.fileOpsOn(activity))
            throw localError != null ? localError : new IOException("Cannot remove " + file);
        AccessManager.delete(activity, file.getAbsolutePath(), true);
        if (exists(activity, file)) throw new IOException("Cannot remove " + file);
    }

    public static void discard(AppCompatActivity activity, File backup) throws IOException { remove(activity, backup); }

    public static void rename(AppCompatActivity activity, File source, String name) throws IOException {
        SafeFileOperations.validateName(name);
        File target = new File(source.getParentFile(), name);
        if (source.getCanonicalPath().equals(target.getCanonicalPath())) return;
        Choice choice = resolve(activity, source, target);
        apply(activity, choice, destination -> AccessManager.rename(activity, source.getAbsolutePath(), destination.getAbsolutePath(), true));
    }
}
