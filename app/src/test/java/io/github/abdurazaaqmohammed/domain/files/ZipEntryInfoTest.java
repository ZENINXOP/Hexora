package io.github.abdurazaaqmohammed.domain.files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import net.lingala.zip4j.ZipFile;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class ZipEntryInfoTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    @Test public void timestampsUseEpochMillisRatherThanDosBits() throws Exception {
        long expected = 1791540000000L;
        File archive = tmp.newFile("dated.zip");
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(archive))) {
            ZipEntry entry = new ZipEntry("docs/note.txt"); entry.setTime(expected);
            output.putNextEntry(entry); output.write(1); output.closeEntry();
        }
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntryInfo entry = new ZipEntryInfo(zip.getFileHeader("docs/note.txt"), archive, "docs/");
            assertEquals("note.txt",entry.getName());
            assertTrue(Math.abs(expected-entry.getLastModified()) < 2000);
        }
    }
}
