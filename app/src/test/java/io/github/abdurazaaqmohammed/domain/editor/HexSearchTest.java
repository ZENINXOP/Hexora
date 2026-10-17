package io.github.abdurazaaqmohammed.domain.editor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;
import java.util.TreeMap;
import static org.junit.Assert.*;

/** Buffered boundaries, edits, cancellation and offsets beyond signed-int range. */
public class HexSearchTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void findsMatchesAcrossBufferBoundariesInBothDirections() throws Exception {
        byte[] bytes = new byte[200000];
        Arrays.fill(bytes, (byte) 'a');
        byte[] needle = {'a', 'a', 'b', 'a', 'b'};
        System.arraycopy(needle, 0, bytes, 65534, needle.length);
        System.arraycopy(needle, 0, bytes, 131070, needle.length);
        File file = temporary.newFile();
        Files.write(file.toPath(), bytes);
        assertEquals(65534, HexSearch.find(file, bytes.length, new TreeMap<>(), needle, 0, true));
        assertEquals(131070, HexSearch.find(file, bytes.length, new TreeMap<>(), needle, bytes.length, false));
        assertEquals(65534, HexSearch.find(file, bytes.length, new TreeMap<>(), needle, 131069, false));
    }

    @Test public void unsavedEditsAreSearchedWithoutChangingTheFile() throws Exception {
        File file = temporary.newFile();
        Files.write(file.toPath(), new byte[]{1, 2, 3, 4});
        TreeMap<Long, Integer> edits = new TreeMap<>();
        edits.put(1L, 9);
        assertEquals(0, HexSearch.find(file, 4, edits, new byte[]{1, 9, 3}, 0, true));
        assertEquals(-1, HexSearch.find(file, 4, edits, new byte[]{1, 2, 3}, 0, true));
        assertArrayEquals(new byte[]{1, 2, 3, 4}, Files.readAllBytes(file.toPath()));
    }

    @Test public void handlesLargeSparseFileOffsetsWithoutIntegerWraparound() throws Exception {
        File file = new File(temporary.getRoot(), "large-sparse.bin");
        long offset = (long) Integer.MAX_VALUE + 31;
        try (java.nio.channels.SeekableByteChannel output = Files.newByteChannel(file.toPath(),
                java.util.EnumSet.of(java.nio.file.StandardOpenOption.CREATE_NEW,
                        java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.SPARSE))) {
            output.position(offset);
            output.write(java.nio.ByteBuffer.wrap(new byte[]{4, 5, 6}));
        }
        assertEquals(offset, HexSearch.find(file, file.length(), new TreeMap<>(), new byte[]{4, 5, 6}, offset - 1, true));
        assertEquals(offset, HexSearch.find(file, file.length(), new TreeMap<>(), new byte[]{4, 5, 6}, offset + 1, false));
    }

    @Test public void randomizedSearchAgreesWithNaiveMatching() throws Exception {
        Random random = new Random(42);
        File file = temporary.newFile();
        for (int round = 0; round < 100; round++) {
            byte[] bytes = new byte[1000];
            random.nextBytes(bytes);
            int target = random.nextInt(990);
            byte[] needle = Arrays.copyOfRange(bytes, target, target + 10);
            Files.write(file.toPath(), bytes);
            assertEquals(target, HexSearch.find(file, bytes.length, new TreeMap<>(), needle, 0, true));
            assertEquals(target, HexSearch.find(file, bytes.length, new TreeMap<>(), needle, bytes.length, false));
        }
    }

    @Test public void cancelledSearchStopsWithInterruptPreserved() throws Exception {
        File file = temporary.newFile();
        Files.write(file.toPath(), new byte[100]);
        Thread.currentThread().interrupt();
        try {
            HexSearch.find(file, 100, new TreeMap<>(), new byte[]{1}, 0, true);
            fail("Expected cancellation");
        } catch (InterruptedIOException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
