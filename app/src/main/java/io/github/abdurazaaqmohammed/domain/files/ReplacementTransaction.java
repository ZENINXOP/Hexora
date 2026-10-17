package io.github.abdurazaaqmohammed.domain.files;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Holds the old destination until replacement commits; rollback ignores cancellation.
 * AI-assisted contribution: OpenAI Codex. */
public final class ReplacementTransaction {
    private ReplacementTransaction() { }
    public interface Storage {
        boolean exists(File file);
        void rename(File source, File destination) throws IOException;
        void remove(File file) throws IOException;
    }
    public interface Operation { void run(File destination) throws IOException; }

    public static void run(File destination, boolean replace, Storage storage, Operation operation) throws IOException {
        File previous = null;
        if (storage.exists(destination)) {
            if (!replace) throw new IOException("Destination appeared while waiting: " + destination + ". Try again.");
            do { previous = new File(destination.getParentFile(), ".hexora-replaced-" + UUID.randomUUID()); }
            while (storage.exists(previous));
            storage.rename(destination, previous);
        }
        boolean complete = false;
        try {
            operation.run(destination);
            complete = true;
        } finally {
            if (previous != null) {
                boolean interrupted = Thread.interrupted();
                try {
                    if (complete) storage.remove(previous);
                    else {
                        if (storage.exists(destination)) storage.remove(destination);
                        storage.rename(previous, destination);
                    }
                } catch (IOException error) {
                    throw new IOException("The previous destination is retained at " + previous + ". " + error.getMessage(), error);
                } finally { if (interrupted) Thread.currentThread().interrupt(); }
            }
        }
    }
}
