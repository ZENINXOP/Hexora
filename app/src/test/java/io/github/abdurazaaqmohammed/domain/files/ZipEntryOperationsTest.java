package io.github.abdurazaaqmohammed.domain.files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.FileOutputStream;
import net.lingala.zip4j.ZipFile;
import static org.junit.Assert.*;

public class ZipEntryOperationsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File archive() throws Exception {
        File file = tmp.newFile("sample.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (String path : new String[]{"docs/alpha.txt", "docs/sub/beta.txt", "docs-other/keep.txt", "top.txt"}) {
                zip.putNextEntry(new ZipEntry(path));
                zip.write(path.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return file;
    }
    @Test public void nestedFileRenameRetainsItsParent() throws Exception {
        File file = archive();
        ZipEntryOperations.rename(file, "docs/sub/beta.txt", false, "renamed.txt");
        try (ZipFile zip = new ZipFile(file)) {
            assertNotNull(zip.getFileHeader("docs/sub/renamed.txt"));
            assertNull(zip.getFileHeader("docs/sub/beta.txt"));
            assertNull(zip.getFileHeader("renamed.txt"));
        }
    }
    @Test public void virtualFolderRenameDoesNotRenamePrefixSiblings() throws Exception {
        File file = archive();
        ZipEntryOperations.rename(file, "docs/", true, "renamed");
        try (ZipFile zip = new ZipFile(file)) {
            assertNotNull(zip.getFileHeader("renamed/alpha.txt"));
            assertNotNull(zip.getFileHeader("renamed/sub/beta.txt"));
            assertNotNull(zip.getFileHeader("docs-other/keep.txt"));
        }
    }
    @Test public void rejectsCollisionsWithoutChangingArchive() throws Exception {
        File file = archive();
        byte[] before = java.nio.file.Files.readAllBytes(file.toPath());
        assertThrows(IOException.class, () -> ZipEntryOperations.rename(file, "docs/", true, "docs-other"));
        assertArrayEquals(before, java.nio.file.Files.readAllBytes(file.toPath()));
    }
    @Test public void rejectsPathNames() throws Exception {
        File file = archive();
        assertThrows(IOException.class, () -> ZipEntryOperations.rename(file, "top.txt", false, "../escape"));
    }
}
