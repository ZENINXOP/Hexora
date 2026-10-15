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
    @Test public void tarLinksCannotEscapeDestination() throws Exception {
        File archive = tmp.newFile("link.tar");
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new FileOutputStream(archive))) {
            TarArchiveEntry entry = new TarArchiveEntry("link", TarConstants.LF_SYMLINK);
            entry.setLinkName("../outside"); tar.putArchiveEntry(entry); tar.closeArchiveEntry();
        }
        File output=tmp.newFolder("out");
        assertThrows(IOException.class, () -> ArchiveUtil.extract(archive, output));
    }

    private void addTarFile(TarArchiveOutputStream tar, String name, byte[] contents) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(contents.length);
        tar.putArchiveEntry(entry);
        tar.write(contents);
        tar.closeArchiveEntry();
    }

    private void addTarLink(TarArchiveOutputStream tar, String name, String target, byte type) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name, type);
        entry.setLinkName(target);
        tar.putArchiveEntry(entry);
        tar.closeArchiveEntry();
    }

    @Test public void ndkLibrarySymlinkCanReferToALaterEntry() throws Exception {
        String folder = "android-ndk-aide/toolchains/aarch64-linux-android-4.9/prebuilt/linux-arm64/libexec/gcc/aarch64-linux-android/4.9.x/";
        byte[] library = new byte[]{0x7f, 'E', 'L', 'F', 1, 2, 3};
        File archive = tmp.newFile("ndk.tar");
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new FileOutputStream(archive))) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            addTarLink(tar, folder + "liblto_plugin.so", "liblto_plugin.so.0", TarConstants.LF_SYMLINK);
            addTarFile(tar, folder + "liblto_plugin.so.0", library);
        }
        File output = tmp.newFolder("ndk");
        ArchiveUtil.extract(archive, output, true);
        assertArrayEquals(library, Files.readAllBytes(new File(output, folder + "liblto_plugin.so").toPath()));
    }

    @Test public void tarLinkChainsAndRootRelativeHardLinksAreResolved() throws Exception {
        File archive = tmp.newFile("chain.tar");
        byte[] content = "library".getBytes(StandardCharsets.UTF_8);
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new FileOutputStream(archive))) {
            addTarLink(tar, "bin/plugin", "../lib/alias", TarConstants.LF_SYMLINK);
            addTarLink(tar, "lib/alias", "target", TarConstants.LF_SYMLINK);
            addTarLink(tar, "bin/hard", "lib/target", TarConstants.LF_LINK);
            addTarFile(tar, "lib/target", content);
        }
        File output = tmp.newFolder("chain");
        ArchiveUtil.extract(archive, output);
        assertArrayEquals(content, Files.readAllBytes(new File(output, "bin/plugin").toPath()));
        assertArrayEquals(content, Files.readAllBytes(new File(output, "bin/hard").toPath()));
    }

    @Test public void directoryLinkIncludesItsDeferredChildren() throws Exception {
        File archive = tmp.newFile("directory-link.tar");
        byte[] content = "header".getBytes(StandardCharsets.UTF_8);
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new FileOutputStream(archive))) {
            addTarLink(tar, "include", "real", TarConstants.LF_SYMLINK);
            addTarLink(tar, "real/alias.h", "target.h", TarConstants.LF_SYMLINK);
            addTarFile(tar, "real/target.h", content);
        }
        File output = tmp.newFolder("directory-link");
        ArchiveUtil.extract(archive, output);
        assertArrayEquals(content, Files.readAllBytes(new File(output, "include/target.h").toPath()));
        assertArrayEquals(content, Files.readAllBytes(new File(output, "include/alias.h").toPath()));
    }

    @Test public void missingAndCyclicLinksDoNotHangOrCreateFakeFiles() throws Exception {
        for (boolean cyclic : new boolean[]{false, true}) {
            File archive = tmp.newFile(cyclic ? "cyclic.tar" : "missing.tar");
            try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new FileOutputStream(archive))) {
                addTarLink(tar, "a", "b", TarConstants.LF_SYMLINK);
                if (cyclic) addTarLink(tar, "b", "a", TarConstants.LF_SYMLINK);
            }
            File output = tmp.newFolder(cyclic ? "cyclic" : "missing");
            assertThrows(IOException.class, () -> ArchiveUtil.extract(archive, output));
            assertFalse(new File(output, "a").exists());
        }
    }

    @Test public void absoluteAndEscapingHardLinkTargetsAreRejected() throws Exception {
        int index = 0;
        for (String target : new String[]{"/absolute", "../outside", "C:/outside", "..\\outside"}) {
            File archive = tmp.newFile("bad-target-" + index + ".tar");
            try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new FileOutputStream(archive))) {
                addTarLink(tar, "link", target, TarConstants.LF_LINK);
            }
            File output = tmp.newFolder("bad-target-" + index++);
            assertThrows(IOException.class, () -> ArchiveUtil.extract(archive, output));
        }
        assertFalse(new File(tmp.getRoot(), "outside").exists());
    }

    @Test public void archiveLinkCannotOverwriteAnExtractedFile() throws Exception {
        File archive = tmp.newFile("collision.tar");
        byte[] original = "keep".getBytes(StandardCharsets.UTF_8);
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new FileOutputStream(archive))) {
            addTarFile(tar, "link", original);
            addTarFile(tar, "target", new byte[]{42});
            addTarLink(tar, "link", "target", TarConstants.LF_SYMLINK);
        }
        File output = tmp.newFolder("collision");
        assertThrows(IOException.class, () -> ArchiveUtil.extract(archive, output));
        assertArrayEquals(original, Files.readAllBytes(new File(output, "link").toPath()));
    }

    @Test public void nestedArchivesStayPackedUntilExtractedSeparately() throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(buffer)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("inside.txt"));
            zip.write("packed".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        byte[] packed = buffer.toByteArray();
        File archive = tmp.newFile("outer.tar");
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new FileOutputStream(archive))) {
            addTarFile(tar, "nested.zip", packed);
            addTarFile(tar, "nested.jar", packed);
        }
        File output = tmp.newFolder("outer");
        ArchiveUtil.extract(archive, output);
        assertArrayEquals(packed, Files.readAllBytes(new File(output, "nested.zip").toPath()));
        assertArrayEquals(packed, Files.readAllBytes(new File(output, "nested.jar").toPath()));
        assertFalse(new File(output, "inside.txt").exists());
        assertFalse(new File(output, "nested").exists());

        File zipArchive = tmp.newFile("outer.zip");
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(new FileOutputStream(zipArchive))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("nested.zip"));
            zip.write(packed);
            zip.closeEntry();
        }
        File zipOutput = tmp.newFolder("outer-zip");
        ArchiveUtil.extract(zipArchive, zipOutput);
        assertArrayEquals(packed, Files.readAllBytes(new File(zipOutput, "nested.zip").toPath()));
        assertFalse(new File(zipOutput, "inside.txt").exists());
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
