package io.github.abdurazaaqmohammed.domain.files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class ReplacementTransactionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final ReplacementTransaction.Storage storage = new ReplacementTransaction.Storage() {
        public boolean exists(File file) { return file.exists(); }
        public void rename(File source, File target) throws IOException { Files.move(source.toPath(), target.toPath()); }
        public void remove(File file) throws IOException {
            assertFalse("Recovery must run without cancellation", Thread.currentThread().isInterrupted());
            SafeFileOperations.delete(file);
        }
    };

    private File original() throws IOException {
        File file = temporary.newFile("original.txt");
        Files.write(file.toPath(), new byte[]{1, 2, 3});
        return file;
    }

    @Test public void successfulReplacementPublishesNewBytesAndRemovesPrevious() throws Exception {
        File file = original();
        ReplacementTransaction.run(file, true, storage, target -> Files.write(target.toPath(), new byte[]{4, 5}));
        assertArrayEquals(new byte[]{4, 5}, Files.readAllBytes(file.toPath()));
        assertEquals(1, temporary.getRoot().list().length);
    }

    @Test public void failedReplacementRestoresOriginalAfterPartialWrite() throws Exception {
        File file = original();
        try {
            ReplacementTransaction.run(file, true, storage, target -> {
                Files.write(target.toPath(), new byte[]{4});
                throw new IOException("Copy failed");
            });
            fail("Expected failure");
        } catch (IOException expected) { assertEquals("Copy failed", expected.getMessage()); }
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file.toPath()));
        assertEquals(1, temporary.getRoot().list().length);
    }

    @Test public void cancelledReplacementRestoresOriginalAndPreservesInterrupt() throws Exception {
        File file = original();
        try {
            ReplacementTransaction.run(file, true, storage, target -> {
                Files.write(target.toPath(), new byte[]{4});
                Thread.currentThread().interrupt();
                throw new IOException("Cancelled");
            });
            fail("Expected cancellation");
        } catch (IOException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file.toPath()));
    }

    @Test public void keepBothDestinationCannotOverwriteAnArrivingFile() throws Exception {
        File file = original();
        try {
            ReplacementTransaction.run(file, false, storage, target -> fail("Must not replace an existing destination"));
            fail("Expected collision");
        } catch (IOException expected) { assertTrue(expected.getMessage().contains("Destination appeared")); }
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file.toPath()));
    }

    @Test public void rollbackFailureRetainsOriginalAtReportedPath() throws Exception {
        File file = original();
        ReplacementTransaction.Storage failing = new ReplacementTransaction.Storage() {
            public boolean exists(File target) { return storage.exists(target); }
            public void rename(File from, File to) throws IOException {
                if (from.getName().startsWith(".hexora-replaced-")) throw new IOException("Storage disconnected");
                storage.rename(from, to);
            }
            public void remove(File target) throws IOException { storage.remove(target); }
        };
        try {
            ReplacementTransaction.run(file, true, failing, target -> { throw new IOException("Copy failed"); });
            fail("Expected recovery failure");
        } catch (IOException expected) {
            File[] previous = temporary.getRoot().listFiles((dir, name) -> name.startsWith(".hexora-replaced-"));
            assertEquals(1, previous.length);
            assertTrue(expected.getMessage().contains(previous[0].getPath()));
            assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(previous[0].toPath()));
        }
    }
}
