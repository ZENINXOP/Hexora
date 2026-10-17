package io.github.abdurazaaqmohammed.domain.editor;

import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import com.android.tools.smali.dexlib2.writer.io.FileDeferredOutputStream;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.domain.files.SafeFileOperations;

/** Disk-backed DEX saves: keep the original and pending edits until publication succeeds.
 * AI-assisted contribution: OpenAI Codex. */
public final class DexSaveWriter {
    private DexSaveWriter() { }

    public static void write(DexPool dex, File destination) throws IOException {
        checkCancelled();
        File parent = destination.getAbsoluteFile().getParentFile();
        File temporary = File.createTempFile(".hexora-dex-", ".tmp", parent);
        List<File> scratchFiles = new ArrayList<>();
        List<FileDeferredOutputStream> scratchStreams = new ArrayList<>();
        try {
            // Both the final DEX and deferred method code go to disk. A MemoryDataStore
            // plus Arrays.copyOf previously held several extra full-file buffers on heap.
            dex.writeTo(new FileDataStore(temporary) {
                @Override public OutputStream outputAt(int offset) {
                    return new FilterOutputStream(super.outputAt(offset)) {
                        @Override public void write(int value) throws IOException {
                            checkCancelled();
                            out.write(value);
                        }
                        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                            checkCancelled();
                            out.write(bytes, offset, length);
                        }
                    };
                }
            }, () -> {
                checkCancelled();
                File file = File.createTempFile(".hexora-dex-code-", ".tmp", parent);
                scratchFiles.add(file);
                FileDeferredOutputStream stream = new FileDeferredOutputStream(file, 64 * 1024);
                scratchStreams.add(stream);
                return stream;
            });
            try (FileOutputStream sync = new FileOutputStream(temporary, true)) {
                sync.getFD().sync();
            }
            checkCancelled();
            if (destination.exists()) {
                File backup = SafeFileOperations.unusedFile(new File(destination.getPath() + ".bak"));
                SafeFileOperations.copyFile(destination, backup);
            }
            checkCancelled();
            com.reandroid.utils.io.AtomicFileReplace.replace(temporary, destination);
        } finally {
            for (FileDeferredOutputStream stream : scratchStreams) {
                try { stream.close(); } catch (IOException ignored) { }
            }
            for (File file : scratchFiles) if (file.exists()) file.delete();
            if (temporary.exists()) temporary.delete();
        }
    }

    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Compilation cancelled");
    }
}
