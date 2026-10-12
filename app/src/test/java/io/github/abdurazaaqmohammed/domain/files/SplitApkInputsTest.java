package io.github.abdurazaaqmohammed.domain.files;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class SplitApkInputsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private File bundle(String... names) throws Exception {
        File file = folder.newFile();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < names.length; i++) {
                zip.putNextEntry(new ZipEntry(names[i])); zip.write(new byte[]{(byte)i}); zip.closeEntry();
            }
        }
        return file;
    }
    @Test public void nestedAndUppercaseApksFlattenWithoutObbOrPathTraversal() throws Exception {
        File input = bundle("nested/base.APK", "../config.en.apk", "Android/obb/game.obb", "manifest.json");
        File output = folder.newFolder();
        assertEquals(2, SplitApkInputs.extract(input, output, null));
        File[] files = output.listFiles();
        assertEquals(2, files.length);
        for (File file : files) {
            assertTrue(file.getName().endsWith(".apk"));
            assertEquals(output.getCanonicalFile(), file.getCanonicalFile().getParentFile());
        }
        assertArrayEquals(new byte[]{0}, Files.readAllBytes(new File(output, "0_base.apk").toPath()));
    }
    @Test public void selectionIsExactAndMissingSplitFails() throws Exception {
        File input = bundle("base.apk", "config.en.apk", "config.fr.apk");
        File output = folder.newFolder();
        assertEquals(2, SplitApkInputs.extract(input, output, Arrays.asList("base.apk", "config.en.apk")));
        assertThrows(IOException.class, () -> SplitApkInputs.extract(input, folder.newFolder(), List.of("missing.apk")));
    }
    @Test public void duplicateBasenamesDoNotOverwriteEachOther() throws Exception {
        File output = folder.newFolder();
        assertEquals(2, SplitApkInputs.extract(bundle("one/base.apk", "two/base.apk"), output, null));
        assertEquals(2, output.list().length);
    }
    @Test public void bundleWithoutApksFails() throws Exception {
        File input = bundle("manifest.json", "game.obb");
        assertThrows(IOException.class, () -> SplitApkInputs.extract(input, folder.newFolder(), null));
    }
}
