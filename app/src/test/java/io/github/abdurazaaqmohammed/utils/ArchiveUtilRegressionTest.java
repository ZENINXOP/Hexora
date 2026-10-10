package io.github.abdurazaaqmohammed.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import static org.junit.Assert.*;

public class ArchiveUtilRegressionTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private void roundTrip(String extension) throws Exception {
        File src = tmp.newFolder("source" + extension.replace('.', '-'));
        new File(src, "nested/empty").mkdirs();
        Files.write(new File(src, "nested/note.txt").toPath(), "archive contents".getBytes(StandardCharsets.UTF_8));
        File archive = new File(tmp.getRoot(), "test" + extension);
        ArchiveUtil.create(archive, Collections.singletonList(src));
        File output = tmp.newFolder("output" + extension.replace('.', '-'));
        ArchiveUtil.extract(archive, output, true);
        assertEquals("archive contents", new String(Files.readAllBytes(new File(output, src.getName() + "/nested/note.txt").toPath()), StandardCharsets.UTF_8));
        assertTrue(new File(output, src.getName() + "/nested/empty").isDirectory());
    }
    @Test public void sevenZipRoundTrip() throws Exception { roundTrip(".7z"); }
    @Test public void tarRoundTrip() throws Exception { roundTrip(".tar"); }
    @Test public void tarGzipRoundTrip() throws Exception { roundTrip(".tar.gz"); }
    @Test public void tarBzipRoundTrip() throws Exception { roundTrip(".tar.bz2"); }
    @Test public void tarXzRoundTrip() throws Exception { roundTrip(".tar.xz"); }
    @Test public void compressedSingleFilesRoundTrip() throws Exception {
        File src = tmp.newFile("note.txt");
        byte[] content = "single compressed file".getBytes(StandardCharsets.UTF_8);
        Files.write(src.toPath(), content);
        for (String ext : new String[]{".gz", ".bz2", ".xz"}) {
            File archive = new File(tmp.getRoot(), "note.txt" + ext);
            ArchiveUtil.create(archive, Collections.singletonList(src));
            File output = tmp.newFolder("output" + ext);
            ArchiveUtil.extract(archive, output);
            assertArrayEquals(content, Files.readAllBytes(new File(output, "note.txt").toPath()));
        }
    }
    @Test public void rejectsTraversalAndAbsolutePaths() throws Exception {
        File directory = tmp.newFolder("out");
        for (String path : new String[]{"../escape", "safe/../../escape", "..\\escape", "/absolute", "C:/escape", ""}) {
            assertThrows(IOException.class, () -> ArchiveUtil.resolveEntry(directory, path));
        }
        assertEquals(new File(directory,"safe/note").getCanonicalFile(), ArchiveUtil.resolveEntry(directory,"safe/note").getCanonicalFile());
    }
    @Test public void maliciousTarDoesNotWriteOutsideDestination() throws Exception {
        File archive = tmp.newFile("bad.tar");
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new FileOutputStream(archive))) {
            TarArchiveEntry entry = new TarArchiveEntry("../escape");
            entry.setSize(4); tar.putArchiveEntry(entry); tar.write(new byte[]{1,2,3,4}); tar.closeArchiveEntry();
        }
        File output=tmp.newFolder("out");
        assertThrows(IOException.class, () -> ArchiveUtil.extract(archive, output));
        assertFalse(new File(tmp.getRoot(),"escape").exists());
    }
    @Test public void tarLinksFailExplicitly() throws Exception {
        File archive = tmp.newFile("link.tar");
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new FileOutputStream(archive))) {
            TarArchiveEntry entry = new TarArchiveEntry("link", TarConstants.LF_SYMLINK);
            entry.setLinkName("../outside"); tar.putArchiveEntry(entry); tar.closeArchiveEntry();
        }
        File output=tmp.newFolder("out");
        assertThrows(IOException.class, () -> ArchiveUtil.extract(archive, output));
    }
    @Test public void sevenZipWithoutTimestampCanBeExtracted() throws Exception {
        File archive=tmp.newFile("no-time.7z");
        try (org.apache.commons.compress.archivers.sevenz.SevenZOutputFile zip = new org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(archive)) {
            org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry entry = new org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry();
            entry.setName("note.txt"); entry.setSize(1); zip.putArchiveEntry(entry); zip.write(new byte[]{42}); zip.closeArchiveEntry();
        }
        File output=tmp.newFolder("out");
        ArchiveUtil.extract(archive,output,true);
        assertArrayEquals(new byte[]{42},Files.readAllBytes(new File(output,"note.txt").toPath()));
    }
    @Test public void archiveCreationCannotOverwriteExistingData() throws Exception {
        File output=tmp.newFile("existing.tar");
        byte[] original="keep original archive".getBytes(StandardCharsets.UTF_8);
        Files.write(output.toPath(),original);
        File input=tmp.newFile("note.txt");
        assertThrows(IOException.class, () -> ArchiveUtil.create(output,Collections.singletonList(input)));
        assertArrayEquals(original,Files.readAllBytes(output.toPath()));
    }
    @Test public void failedArchiveCreationLeavesNoPublishedFile() throws Exception {
        File output=new File(tmp.getRoot(),"bad.7z");
        assertThrows(IOException.class, () -> ArchiveUtil.create(output,Collections.singletonList(new File(tmp.getRoot(),"missing"))));
        assertFalse(output.exists());
        assertEquals(0,tmp.getRoot().list().length);
    }
}
