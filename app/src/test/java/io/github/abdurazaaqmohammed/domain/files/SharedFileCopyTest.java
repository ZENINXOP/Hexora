package io.github.abdurazaaqmohammed.domain.files;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class SharedFileCopyTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    @Test public void namesCannotEscapeFolderAndKeepExtension() {
        assertEquals("report.pdf", SharedFileCopy.safeName("../../report.pdf", null));
        assertEquals("report.pdf", SharedFileCopy.safeName("C:\\files\\report.pdf", null));
        assertEquals("Shared file.pdf", SharedFileCopy.safeName("..", "pdf"));
        assertTrue(SharedFileCopy.safeName("x".repeat(300) + ".xapk", null).endsWith(".xapk"));
        assertEquals(160, SharedFileCopy.safeName("x".repeat(300) + ".xapk", null).length());
    }
    @Test public void sameNameNeverReplacesEarlierBytes() throws Exception {
        File dir = folder.newFolder();
        File first = SharedFileCopy.copy(new ByteArrayInputStream(new byte[]{1,2}), dir, "report.pdf", 100);
        File second = SharedFileCopy.copy(new ByteArrayInputStream(new byte[]{3,4}), dir, "report.pdf", 100);
        assertNotEquals(first, second);
        assertArrayEquals(new byte[]{1,2}, Files.readAllBytes(first.toPath()));
        assertArrayEquals(new byte[]{3,4}, Files.readAllBytes(second.toPath()));
    }
    @Test public void incompleteStreamLeavesNoPublishedFile() throws Exception {
        File dir = folder.newFolder();
        InputStream broken = new InputStream() {
            int count;
            @Override public int read() throws IOException {
                if (++count > 3) throw new IOException("provider disconnected");
                return 1;
            }
        };
        assertThrows(IOException.class, () -> SharedFileCopy.copy(broken, dir, "partial.apk", 100));
        assertEquals(0, dir.list().length);
    }
    @Test public void diskBudgetFailureLeavesNoFile() throws Exception {
        File dir = folder.newFolder();
        assertThrows(IOException.class, () -> SharedFileCopy.copy(new ByteArrayInputStream(new byte[8]), dir, "large.xapk", 4));
        assertEquals(0, dir.list().length);
    }
}
