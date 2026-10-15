package io.github.abdurazaaqmohammed.utils;

import java.io.IOException;
import java.io.InterruptedIOException;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.progress.ProgressMonitor;
import io.github.abdurazaaqmohammed.domain.files.FileWorkProgress;

/** Waits for the ZIP worker and forwards its measured work on the owning operation thread. */
public final class ZipTaskProgress {
    public interface Task { void run() throws IOException; }
    private ZipTaskProgress() { }
    public static void run(ZipFile zip, Task task, long units) throws IOException {
        zip.setRunInThread(true);
        ProgressMonitor monitor = zip.getProgressMonitor();
        monitor.fullReset();
        long reported = 0;
        boolean interrupted = false;
        try {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Archive operation cancelled");
            task.run();
            while (monitor.getState() == ProgressMonitor.State.BUSY) {
                long total = monitor.getTotalWork();
                if (total > 0) {
                    long done = Math.min(units, (long) ((double) Math.max(0, monitor.getWorkCompleted()) / total * units));
                    if (done > reported) { FileWorkProgress.advance(done - reported); reported = done; }
                }
                try { Thread.sleep(50); }
                catch (InterruptedException e) { interrupted = true; monitor.setCancelAllTasks(true); }
            }
            if (interrupted || monitor.getResult() == ProgressMonitor.Result.CANCELLED) throw new InterruptedIOException("Archive operation cancelled");
            if (monitor.getResult() == ProgressMonitor.Result.ERROR) throw new IOException("Archive operation failed", monitor.getException());
            FileWorkProgress.advance(Math.max(0, units - reported));
        } finally {
            // Never close an archive while the library is still replacing it.
            zip.setRunInThread(false);
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
