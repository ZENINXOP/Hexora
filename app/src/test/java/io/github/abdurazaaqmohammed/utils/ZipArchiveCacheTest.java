package io.github.abdurazaaqmohammed.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;

import static org.junit.Assert.*;

public class ZipArchiveCacheTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File archive(String... names) throws IOException {
        File file = temp.newFile();
        writeArchive(file, names);
        return file;
    }

    private void writeArchive(File file, String... names) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                if (!name.endsWith("/")) zip.write(("content:" + name).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private Set<String> names(List<ZipEntryInfo> entries) {
        Set<String> result = new HashSet<>();
        for (ZipEntryInfo entry : entries) {
            assertTrue("Duplicate child: " + entry.getName(), result.add(entry.getName()));
        }
        return result;
    }

    private void put(ZipOutputStream zip, String path, long time, int size) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(time);
        zip.putNextEntry(entry);
        if (!path.endsWith("/")) zip.write(new byte[size]);
        zip.closeEntry();
    }

    private ZipEntryInfo child(ZipArchiveCache.Archive archive, String parent, String name) {
        for (ZipEntryInfo entry : archive.children(parent)) if (entry.getName().equals(name)) return entry;
        throw new AssertionError("Missing archive child: " + parent + name);
    }

    @Test public void implicitApkFoldersHaveDescendantDatesAndExpandedSizes() throws Exception {
        File file = temp.newFile("implicit.apk");
        long older = 1791540000000L, newer = older + 60000;
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            put(zip, "res/layout/main.xml", older, 13);
            put(zip, "res/drawable/icon.png", newer, 29);
            put(zip, "classes.dex", older, 17);
        }
        ZipArchiveCache.Archive index = ZipArchiveCache.get(file);
        ZipEntryInfo res = child(index, "", "res");
        assertEquals(42, res.getSize());
        assertTrue(Math.abs(newer - res.getLastModified()) < 2000);
        assertEquals(13, child(index, "res/", "layout").getSize());
        assertTrue(Math.abs(older - child(index, "res/", "layout").getLastModified()) < 2000);
        ZipEntryInfo dex = child(index, "", "classes.dex");
        assertEquals(17, dex.getSize());
        assertTrue(Math.abs(older - dex.getLastModified()) < 2000);
    }

    @Test public void explicitFolderDatesSurviveBeforeOrAfterChildren() throws Exception {
        long folderTime = 1791540000000L, childTime = folderTime + 60000;
        for (boolean folderFirst : new boolean[]{true, false}) {
            File file = temp.newFile();
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
                if (folderFirst) put(zip, "assets/", folderTime, 0);
                put(zip, "assets/nested/data.bin", childTime, 31);
                if (!folderFirst) put(zip, "assets/", folderTime, 0);
                put(zip, "empty/", folderTime, 0);
            }
            ZipArchiveCache.Archive index = ZipArchiveCache.get(file);
            ZipEntryInfo assets = child(index, "", "assets");
            assertTrue(Math.abs(folderTime - assets.getLastModified()) < 2000);
            assertEquals(31, assets.getSize());
            ZipEntryInfo empty = child(index, "", "empty");
            assertEquals(0, empty.getSize());
            assertTrue(Math.abs(folderTime - empty.getLastModified()) < 2000);
            assertEquals(new HashSet<>(Arrays.asList("assets", "empty")), names(index.children("")));
        }
    }

    @Test public void entryDatesNeverFallBackToArchiveFilesystemDate() throws Exception {
        File file = temp.newFile();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            put(zip, "assets/data.bin", 0L, 23);
            put(zip, "empty/", 0L, 0);
        }
        long archiveTime = 1791540000000L;
        assertTrue(file.setLastModified(archiveTime));
        ZipArchiveCache.Archive index = ZipArchiveCache.get(file);
        assertEquals(23, child(index, "", "assets").getSize());
        try (net.lingala.zip4j.ZipFile zip = new net.lingala.zip4j.ZipFile(file)) {
            long entryTime = Math.max(0L, zip.getFileHeader("assets/data.bin").getLastModifiedTimeEpoch());
            assertEquals(entryTime, child(index, "", "assets").getLastModified());
            assertEquals(entryTime, child(index, "assets/", "data.bin").getLastModified());
            assertEquals(Math.max(0L, zip.getFileHeader("empty/").getLastModifiedTimeEpoch()),
                    child(index, "", "empty").getLastModified());
        }
        assertNotEquals(archiveTime, child(index, "", "assets").getLastModified());
    }

    @Test public void listsDirectChildrenAndSynthesizesMissingFolders() throws Exception {
        File file = archive("docs/deep/notes.txt", "docs/other.txt", "docs/", "empty/", "root.txt");
        ZipArchiveCache.Archive index = ZipArchiveCache.get(file);
        assertEquals(new HashSet<>(Arrays.asList("docs", "empty", "root.txt")), names(index.children("")));
        assertEquals(new HashSet<>(Arrays.asList("deep", "other.txt")), names(index.children("docs")));
        assertEquals(names(index.children("docs")), names(index.children("docs/")));
        assertTrue(index.children("empty").isEmpty());
        assertTrue(index.children("missing").isEmpty());
        assertEquals("docs/deep/notes.txt", index.children("docs/deep").get(0).getFullPath());
    }

    @Test public void normalizesBackslashesWithoutChangingExtractionLookup() throws Exception {
        File file = archive("docs\\nested\\note.txt");
        ZipArchiveCache.Archive index = ZipArchiveCache.get(file);
        assertEquals(new HashSet<>(Arrays.asList("note.txt")), names(index.children("docs\\nested")));
        File output = temp.newFile();
        index.copyEntry("docs/nested/note.txt", output);
        assertEquals("content:docs\\nested\\note.txt", new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void reusesParsedHeadersAndCanCopyMoreThanOnce() throws Exception {
        File file = archive("first.txt", "nested/second.txt");
        ZipArchiveCache.Archive index = ZipArchiveCache.get(file);
        assertSame(index, ZipArchiveCache.get(file));
        for (String path : Arrays.asList("first.txt", "nested/second.txt", "first.txt")) {
            File output = temp.newFile();
            index.copyEntry(path, output);
            assertEquals("content:" + path, new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8));
        }
        try {
            index.children("").clear();
            fail("Shared folder lists must be read-only");
        } catch (UnsupportedOperationException expected) { }
    }

    @Test public void reloadsModifiedArchivesAndSupportsExplicitInvalidation() throws Exception {
        File file = archive("old.txt");
        ZipArchiveCache.Archive original = ZipArchiveCache.get(file);
        writeArchive(file, "new-and-longer-name.txt");
        ZipArchiveCache.Archive updated = ZipArchiveCache.get(file);
        assertNotSame(original, updated);
        assertFalse(updated.contains("old.txt"));
        assertTrue(updated.contains("new-and-longer-name.txt"));
        ZipArchiveCache.invalidate(file);
        assertNotSame(updated, ZipArchiveCache.get(file));
    }

    @Test public void simultaneousPaneRequestsShareOneIndex() throws Exception {
        File file = archive("one/two/three.txt");
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ZipArchiveCache.Archive> first = workers.submit(() -> { start.await(); return ZipArchiveCache.get(file); });
            Future<ZipArchiveCache.Archive> second = workers.submit(() -> { start.await(); return ZipArchiveCache.get(file); });
            start.countDown();
            assertSame(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
        }
    }

    @Test public void failedArchiveCanBeRetriedAndMissingEntryDoesNotCreateFile() throws Exception {
        File file = temp.newFile();
        Files.write(file.toPath(), "not a zip".getBytes(StandardCharsets.UTF_8));
        try {
            ZipArchiveCache.get(file);
            fail("Corrupt ZIP must be rejected");
        } catch (IOException expected) { }
        writeArchive(file, "valid.txt");
        ZipArchiveCache.Archive index = ZipArchiveCache.get(file);
        File output = new File(temp.getRoot(), "missing-output.txt");
        try {
            index.copyEntry("missing.txt", output);
            fail("Missing entry must be rejected");
        } catch (IOException expected) { }
        assertFalse(output.exists());
        assertTrue(index.contains("valid.txt"));
    }
}
