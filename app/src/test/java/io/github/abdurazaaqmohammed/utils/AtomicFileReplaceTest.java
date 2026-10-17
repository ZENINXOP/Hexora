package io.github.abdurazaaqmohammed.utils;

import com.reandroid.utils.io.AtomicFileReplace;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class AtomicFileReplaceTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void replacesExistingFileWithoutRecoveryDebris() throws Exception {
        File original = tmp.newFile("original.dex");
        File staged = tmp.newFile("new.dex");
        Files.write(original.toPath(), new byte[]{1});
        Files.write(staged.toPath(), new byte[]{2});
        AtomicFileReplace.replace(staged, original);
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(original.toPath()));
        assertFalse(staged.exists());
        assertEquals(1, tmp.getRoot().list().length);
    }

    @Test public void failedPublishRollsOriginalBack() throws Exception {
        File original = tmp.newFile("original.dex");
        Files.write(original.toPath(), new byte[]{1});
        File stagedPath = tmp.newFile("new.dex");
        Files.write(stagedPath.toPath(), new byte[]{2});
        File failingStage = new File(stagedPath.getPath()) {
            @Override public boolean renameTo(File target) { return false; }
        };
        assertThrows(IOException.class, () -> AtomicFileReplace.replace(failingStage, original));
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(original.toPath()));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(stagedPath.toPath()));
        assertEquals(2, tmp.getRoot().list().length);
    }

    @Test public void rejectsPublishingFileOverItself() throws Exception {
        File file = tmp.newFile("classes.dex");
        Files.write(file.toPath(), new byte[]{3});
        assertThrows(IOException.class, () -> AtomicFileReplace.replace(file, file));
        assertArrayEquals(new byte[]{3}, Files.readAllBytes(file.toPath()));
    }
}
