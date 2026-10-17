package io.github.abdurazaaqmohammed.domain.files;

import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

public class FileSortingTest {
    private static final class CountingFile extends File {
        final boolean directory;
        final long bytes;
        int statCalls, sizeCalls, dateCalls;
        CountingFile(String name, boolean directory, long bytes) {
            super(name);
            this.directory = directory;
            this.bytes = bytes;
        }
        @Override public boolean isDirectory() { statCalls++; return directory; }
        @Override public long length() { sizeCalls++; return bytes; }
        @Override public long lastModified() { dateCalls++; return bytes; }
    }

    @Test public void metadataIsReadOncePerEntryNotOncePerComparison() {
        CountingFile[] files = new CountingFile[2000];
        for (int i = 0; i < files.length; i++) files[i] = new CountingFile("file" + i, false, files.length - i);
        FileSorting.sortFiles(files, FileSorting.SORT_SIZE, false);
        for (CountingFile file : files) {
            assertEquals(1, file.statCalls);
            assertEquals(1, file.sizeCalls);
            assertEquals(0, file.dateCalls);
        }
        assertEquals(1, files[0].bytes);
    }

    @Test public void reverseOrderingStillKeepsDirectoriesFirst() {
        CountingFile directory = new CountingFile("a", true, 0);
        CountingFile a = new CountingFile("a", false, 2);
        CountingFile z = new CountingFile("z", false, 1);
        File[] files = {a, z, directory};
        FileSorting.sortFiles(files, FileSorting.SORT_NAME, true);
        assertArrayEquals(new File[]{directory, z, a}, files);
        assertEquals(0, a.sizeCalls);
        assertEquals(0, z.dateCalls);
    }
}
