package com.reandroid.utils.io;

import java.io.File;
import java.io.IOException;

/** Publish a staged file on storage that may refuse rename-over-existing.
 * AI-assisted contribution: OpenAI Codex. */
public final class AtomicFileReplace {
    private AtomicFileReplace() { }

    public static void replace(File staged, File destination) throws IOException {
        if (!staged.isFile()) throw new IOException("Missing staged file: " + staged);
        if (staged.getCanonicalFile().equals(destination.getCanonicalFile())) {
            throw new IOException("Staged and destination files must differ");
        }
        // Android/Linux supports an atomic rename over an existing regular file.
        if (staged.renameTo(destination)) return;
        if (!destination.isFile()) throw new IOException("Cannot publish file: " + destination);
        File recovery = File.createTempFile(".hexora-original-", ".tmp", destination.getAbsoluteFile().getParentFile());
        if (!recovery.delete()) throw new IOException("Cannot prepare recovery file: " + recovery);
        if (!destination.renameTo(recovery)) throw new IOException("Cannot preserve original: " + destination);
        try {
            if (destination.exists() || !staged.renameTo(destination)) {
                throw new IOException("Cannot publish file: " + destination);
            }
        } catch (IOException | RuntimeException error) {
            if (destination.exists() || !recovery.renameTo(destination)) {
                error.addSuppressed(new IOException("Original retained at: " + recovery));
            }
            throw error;
        }
        recovery.delete();
    }
}
