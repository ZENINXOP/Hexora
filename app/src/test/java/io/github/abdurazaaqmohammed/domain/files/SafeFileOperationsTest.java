package io.github.abdurazaaqmohammed.domain.files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class SafeFileOperationsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private static void writeText(java.nio.file.Path path, String text) throws IOException {
        Files.write(path, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static String readText(java.nio.file.Path path) throws IOException {
        return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test public void nestedCopyPreservesSameNamedFilesAndEmptyFolders() throws Exception {
        File src = tmp.newFolder("source");
        new File(src, "first/empty").mkdirs();
        new File(src, "second").mkdirs();
        writeText(new File(src, "first/note.txt").toPath(), "one");
        writeText(new File(src, "second/note.txt").toPath(), "two");
        File dest = new File(tmp.getRoot(), "destination");
        SafeFileOperations.copyDirectory(src, dest);
        SafeFileOperations.verifyCopy(src, dest);
        assertEquals("one", readText(new File(dest, "first/note.txt").toPath()));
        assertEquals("two", readText(new File(dest, "second/note.txt").toPath()));
        assertTrue(new File(dest, "first/empty").isDirectory());
        assertFalse(new File(dest, "note.txt").exists());
    }

    @Test public void rejectsCopyIntoDescendantBeforeCreatingAnything() throws Exception {
        File src = tmp.newFolder("source");
        File dest = new File(src, "nested/copy");
        assertThrows(IOException.class, () -> SafeFileOperations.copyDirectory(src, dest));
        assertFalse(dest.exists());
    }

    @Test public void canonicalAliasCannotBypassDescendantGuard() throws Exception {
        File src = tmp.newFolder("source");
        assertThrows(IOException.class, () -> SafeFileOperations.validateDestination(src, new File(src, "../source/copy")));
        assertThrows(IOException.class, () -> SafeFileOperations.validateDestination(src, src));
    }

    @Test public void copyNeverOverwritesExistingFile() throws Exception {
        File src = tmp.newFile("src");
        File dest = tmp.newFile("dest");
        writeText(src.toPath(), "source");
        writeText(dest.toPath(), "keep me");
        assertThrows(IOException.class, () -> SafeFileOperations.copyFile(src, dest));
        assertEquals("keep me", readText(dest.toPath()));
    }

    @Test public void failedCopyLeavesNoPublishedFileOrTemporaryFile() throws Exception {
        File dest = new File(tmp.getRoot(), "dest");
        assertThrows(IOException.class, () -> SafeFileOperations.copyFile(new File(tmp.getRoot(), "missing"), dest));
        assertFalse(dest.exists());
        assertEquals(0, tmp.getRoot().list().length);
    }

    @Test public void verifiesBytesRatherThanOnlyTotalLength() throws Exception {
        File src = tmp.newFile("src");
        File dest = tmp.newFile("dest");
        writeText(src.toPath(), "123");
        writeText(dest.toPath(), "456");
        assertThrows(IOException.class, () -> SafeFileOperations.verifyCopy(src, dest));
        assertTrue(src.exists());
    }

    @Test public void directoryVerificationUsesReturnedProviderFilesAndStreams() throws Exception {
        File source = tmp.newFolder("source");
        byte[] bytes = "provider content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(new File(source, "note.txt").toPath(), bytes);
        class ProviderFile extends File implements FileWorkProgress.ReadableFile {
            ProviderFile() { super(tmp.getRoot(), "virtual/note.txt"); }
            @Override public boolean isFile() { return true; }
            @Override public long length() { return bytes.length; }
            @Override public java.io.InputStream openInput() { return new java.io.ByteArrayInputStream(bytes); }
        }
        File providerDirectory = new File(tmp.getRoot(), "virtual") {
            @Override public boolean isDirectory() { return true; }
            @Override public File[] listFiles() { return new File[]{new ProviderFile()}; }
        };
        SafeFileOperations.verifyCopy(source, providerDirectory);
        assertFalse(new File(tmp.getRoot(), "virtual").exists());
    }

    @Test public void verificationDetectsMissingEmptyDirectory() throws Exception {
        File src = tmp.newFolder("src");
        new File(src, "empty").mkdir();
        File dest = tmp.newFolder("dest");
        assertThrows(IOException.class, () -> SafeFileOperations.verifyCopy(src, dest));
    }

    @Test public void copiesEmptyAndLargeBinaryFiles() throws Exception {
        File src = tmp.newFile("src");
        File dest = new File(tmp.getRoot(), "dest");
        SafeFileOperations.copyFile(src, dest);
        SafeFileOperations.verifyCopy(src, dest);
        byte[] bytes = new byte[2 * 1024 * 1024 + 13];
        new java.util.Random(42).nextBytes(bytes);
        Files.write(src.toPath(), bytes);
        File large = new File(tmp.getRoot(), "large");
        SafeFileOperations.copyFile(src, large);
        assertArrayEquals(bytes, Files.readAllBytes(large.toPath()));
    }

    @Test public void cancelledCopyRetainsSource() throws Exception {
        File src = tmp.newFile("src");
        writeText(src.toPath(), "keep");
        File dest = new File(tmp.getRoot(), "dest");
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class, () -> SafeFileOperations.copyFile(src, dest));
        } finally { Thread.interrupted(); }
        assertTrue(src.exists());
        assertFalse(dest.exists());
    }

    @Test public void cancellationAfterFinalChunkDoesNotPublishDestination() throws Exception {
        File src = tmp.newFile("src");
        writeText(src.toPath(), "keep original");
        File dest = new File(tmp.getRoot(), "dest");
        try (FileWorkProgress.Scope scope = FileWorkProgress.track(units -> Thread.currentThread().interrupt())) {
            assertThrows(IOException.class, () -> SafeFileOperations.copyFile(src, dest));
        } finally { Thread.interrupted(); }
        assertEquals("keep original", readText(src.toPath()));
        assertFalse(dest.exists());
        assertEquals(1, tmp.getRoot().list().length);
    }

    @Test public void channelCopyReportsEachByteExactlyOnce() throws Exception {
        File src = tmp.newFile("src");
        byte[] bytes = new byte[8 * 1024 * 1024 + 37];
        new java.util.Random(17).nextBytes(bytes);
        Files.write(src.toPath(), bytes);
        File dest = new File(tmp.getRoot(), "dest");
        long[] progress = {0};
        try (FileWorkProgress.Scope scope = FileWorkProgress.track(units -> progress[0] += units)) {
            SafeFileOperations.copyFile(src, dest);
        }
        assertEquals(bytes.length + 1L, progress[0]);
        assertArrayEquals(bytes, Files.readAllBytes(dest.toPath()));
    }

    @Test public void providerStreamCopyStillWorksWithoutAFileChannel() throws Exception {
        File src = tmp.newFile("src");
        byte[] bytes = new byte[256 * 1024 + 37];
        new java.util.Random(23).nextBytes(bytes);
        Files.write(src.toPath(), bytes);
        class StreamFile extends File implements FileWorkProgress.ReadableFile {
            StreamFile() { super(src.getPath()); }
            @Override public java.io.InputStream openInput() {
                return new java.io.ByteArrayInputStream(bytes);
            }
        }
        File dest = new File(tmp.getRoot(), "dest");
        SafeFileOperations.copyFile(new StreamFile(), dest);
        assertArrayEquals(bytes, Files.readAllBytes(dest.toPath()));
    }

    @Test public void rejectsInvalidNames() {
        for (String name : new String[]{"", " ", ".", "..", "../other", "a/b", "a\\b", "a\0b"}) {
            assertThrows(IOException.class, () -> SafeFileOperations.validateName(name));
        }
    }

    @Test public void acceptsUnicodeAndSpaces() throws Exception {
        SafeFileOperations.validateName("My notes 📝.txt");
    }

    @Test public void deletesNestedDirectoryAndReportsFailure() throws Exception {
        File src = tmp.newFolder("src");
        new File(src, "child").mkdir();
        new File(src, "child/note").createNewFile();
        SafeFileOperations.delete(src);
        assertFalse(src.exists());
        assertThrows(IOException.class, () -> SafeFileOperations.delete(src));
    }
    @Test public void unusedNamesHandleFoldersDotfilesAndExtensions() throws Exception {
        assertEquals("folder_1",SafeFileOperations.unusedFile(tmp.newFolder("folder")).getName());
        assertEquals("README_1",SafeFileOperations.unusedFile(tmp.newFile("README")).getName());
        assertEquals(".hidden_1",SafeFileOperations.unusedFile(tmp.newFile(".hidden")).getName());
        assertEquals("archive.tar_1.gz",SafeFileOperations.unusedFile(tmp.newFile("archive.tar.gz")).getName());
    }

    @Test public void atomicSavePreservesUserBackupAndCleansTemporaryFile() throws Exception {
        File document = tmp.newFile("notes.txt");
        File backup = tmp.newFile("notes.txt.bak");
        writeText(document.toPath(), "original");
        writeText(backup.toPath(), "backup");
        // Windows File.renameTo cannot replace an existing target. Use its atomic replacement API.
        SafeFileOperations.writeAtomically(document, "edited".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                (from, to) -> Files.move(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING));
        assertEquals("edited", new String(Files.readAllBytes(document.toPath()), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("backup", new String(Files.readAllBytes(backup.toPath()), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(2, tmp.getRoot().list().length);
    }

    @Test public void cancelledAtomicSaveRetainsOriginalAndBackup() throws Exception {
        File document = tmp.newFile("notes.txt");
        writeText(document.toPath(), "original");
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class, () -> SafeFileOperations.writeAtomically(document, new byte[]{1},
                    (from, to) -> { throw new AssertionError("Cancelled save must not publish"); }));
        } finally { Thread.interrupted(); }
        assertEquals("original", new String(Files.readAllBytes(document.toPath()), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(1, tmp.getRoot().list().length);
    }

    @Test public void backupRestoreSwapsBothVersionsWithoutTouchingOtherRecoveryFiles() throws Exception {
        File document = tmp.newFile("notes");
        File backup = tmp.newFile("notes.bak");
        File unrelated = tmp.newFile("notes_tmp_.bak");
        writeText(document.toPath(), "current"); writeText(backup.toPath(), "previous");
        writeText(unrelated.toPath(), "keep");
        SafeFileOperations.restoreBackup(backup,document);
        assertEquals("previous",new String(Files.readAllBytes(document.toPath()), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("current",new String(Files.readAllBytes(backup.toPath()), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("keep",new String(Files.readAllBytes(unrelated.toPath()), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(3,tmp.getRoot().list().length);
    }

    @Test public void restoreSupportsExtensionlessNamesInsideBakNamedFolder() throws Exception {
        File directory = tmp.newFolder("folder.bak");
        File backup = new File(directory,"README.bak"); writeText(backup.toPath(),"plain");
        File restored = new File(directory,"RENAMED-README");
        SafeFileOperations.restoreBackup(backup,restored);
        assertTrue(restored.exists()); assertFalse(backup.exists());
    }

    @Test public void invalidRestoreRetainsBackupAndDestination() throws Exception {
        File backup = tmp.newFile("notes.bak"); writeText(backup.toPath(),"keep");
        File folder = tmp.newFolder("folder");
        assertThrows(IOException.class, () -> SafeFileOperations.restoreBackup(backup,folder));
        assertThrows(IOException.class, () -> SafeFileOperations.restoreBackup(backup,backup));
        assertTrue(backup.isFile()); assertTrue(folder.isDirectory());
    }
}
