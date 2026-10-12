package io.github.abdurazaaqmohammed.domain.files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class ArchiveDocumentSaverTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private File archive() throws Exception {
        File archive = temp.newFile("sample.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            for (String name : List.of("docs/first.txt", "docs/second.txt", "keep.txt", "AndroidManifest.xml")) {
                zip.putNextEntry(new ZipEntry(name)); zip.write(name.getBytes()); zip.closeEntry();
            }
        }
        return archive;
    }
    private File saved() throws Exception {
        File file = temp.newFile(); Files.write(file.toPath(), "saved contents".getBytes()); return file;
    }
    private final SafeFileOperations.AtomicReplacement replace = (source, target) ->
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    private void noStagedFiles() {
        assertFalse(Arrays.stream(temp.getRoot().listFiles()).anyMatch(f -> f.getName().startsWith(".hexora-")));
    }
    @Test public void publishesWholeBatchPreservesSiblingsAndOriginalBackup() throws Exception {
        File archive = archive(); byte[] original = Files.readAllBytes(archive.toPath()); File saved = saved();
        File backup = ArchiveDocumentSaver.save(archive, List.of(
                new ArchiveDocumentSaver.Update("docs/first.txt", saved, true),
                new ArchiveDocumentSaver.Update("docs/second.txt", saved, true),
                new ArchiveDocumentSaver.Update("AndroidManifest.xml", saved, true)), null, replace);
        assertArrayEquals(original, Files.readAllBytes(backup.toPath()));
        try (net.lingala.zip4j.ZipFile zip = new net.lingala.zip4j.ZipFile(archive)) {
            for (String name : List.of("docs/first.txt", "docs/second.txt")) {
                try (var input = zip.getInputStream(zip.getFileHeader(name))) { assertEquals("saved contents", new String(input.readAllBytes())); }
            }
            try (var input = zip.getInputStream(zip.getFileHeader("keep.txt"))) { assertEquals("keep.txt", new String(input.readAllBytes())); }
            assertEquals(net.lingala.zip4j.model.enums.CompressionMethod.STORE, zip.getFileHeader("AndroidManifest.xml").getCompressionMethod());
        }
        noStagedFiles();
    }
    @Test public void missingSecondEntryDoesNotPublishFirstEdit() throws Exception {
        File archive=archive(); byte[] original=Files.readAllBytes(archive.toPath()); File saved=saved();
        assertThrows(IOException.class, () -> ArchiveDocumentSaver.save(archive, List.of(
                new ArchiveDocumentSaver.Update("docs/first.txt",saved,true),
                new ArchiveDocumentSaver.Update("missing.txt",saved,true)), null, replace));
        assertArrayEquals(original,Files.readAllBytes(archive.toPath())); noStagedFiles();
    }
    @Test public void signingFailureRetainsOriginalAndEditedSource() throws Exception {
        File archive=archive(); byte[] original=Files.readAllBytes(archive.toPath()); File saved=saved();
        assertThrows(IOException.class, () -> ArchiveDocumentSaver.save(archive,
                List.of(new ArchiveDocumentSaver.Update("docs/first.txt",saved,true)),
                staged -> { throw new IOException("Signing failed"); }, replace));
        assertArrayEquals(original,Files.readAllBytes(archive.toPath())); assertEquals("saved contents",new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8)); noStagedFiles();
    }
    @Test public void publicationFailureRetainsOriginalAndBackup() throws Exception {
        File archive=archive(); byte[] original=Files.readAllBytes(archive.toPath());
        assertThrows(IOException.class, () -> ArchiveDocumentSaver.save(archive,
                List.of(new ArchiveDocumentSaver.Update("docs/first.txt",saved(),true)), null,
                (source,target) -> { throw new IOException("Cannot replace"); }));
        assertArrayEquals(original,Files.readAllBytes(archive.toPath()));
        assertArrayEquals(original,Files.readAllBytes(new File(temp.getRoot(),"sample.zip.bak").toPath())); noStagedFiles();
    }
    @Test public void corruptArchiveIsNotReplaced() throws Exception {
        File archive=temp.newFile("bad.zip"); byte[] original="invalid zip".getBytes(); Files.write(archive.toPath(),original);
        assertThrows(IOException.class, () -> ArchiveDocumentSaver.save(archive,
                List.of(new ArchiveDocumentSaver.Update("file.txt",saved(),false)), null, replace));
        assertArrayEquals(original,Files.readAllBytes(archive.toPath())); noStagedFiles();
    }
    @Test public void missingStagedSourceDoesNotChangeArchive() throws Exception {
        File archive=archive(); byte[] original=Files.readAllBytes(archive.toPath());
        assertThrows(IOException.class, () -> ArchiveDocumentSaver.save(archive,
                List.of(new ArchiveDocumentSaver.Update("docs/first.txt",new File(temp.getRoot(),"missing"),true)), null, replace));
        assertArrayEquals(original,Files.readAllBytes(archive.toPath())); noStagedFiles();
    }
    @Test public void doesNotOverwriteAnOlderBackup() throws Exception {
        File archive=archive(); File oldBackup=temp.newFile("sample.zip.bak"); Files.write(oldBackup.toPath(),"older recovery".getBytes(StandardCharsets.UTF_8));
        File backup=ArchiveDocumentSaver.save(archive,List.of(new ArchiveDocumentSaver.Update("docs/first.txt",saved(),true)), null, replace);
        assertNotEquals(oldBackup,backup); assertEquals("older recovery",new String(Files.readAllBytes(oldBackup.toPath()), StandardCharsets.UTF_8)); noStagedFiles();
    }
    @Test public void invalidEntryPathRetainsOriginal() throws Exception {
        File archive=archive(); byte[] original=Files.readAllBytes(archive.toPath());
        assertThrows(IOException.class, () -> ArchiveDocumentSaver.save(archive,
                List.of(new ArchiveDocumentSaver.Update("../outside.txt",saved(),false)), null, replace));
        assertArrayEquals(original,Files.readAllBytes(archive.toPath())); noStagedFiles();
    }
    @Test public void anotherWritersChangeIsNotOverwritten() throws Exception {
        File archive=archive(); byte[] changed="another writer replaced the file".getBytes();
        assertThrows(IOException.class, () -> ArchiveDocumentSaver.save(archive,
                List.of(new ArchiveDocumentSaver.Update("docs/first.txt",saved(),true)),
                staged -> Files.write(archive.toPath(),changed), replace));
        assertArrayEquals(changed,Files.readAllBytes(archive.toPath())); noStagedFiles();
    }
}
