package io.github.abdurazaaqmohammed.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class SignatureBypassPatcherTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private File source() throws Exception {
        File input = temporary.newFile("original.apk");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(input))) {
            for (String name : new String[]{"classes.dex", "classes2.dex", "AndroidManifest.xml", "resources.arsc",
                    "assets/data.bin", "assets/untouched.RSA", "META-INF/services/sample", "META-INF/MANIFEST.MF", "META-INF/CERT.SF", "META-INF/CERT.RSA"}) {
                zip.putNextEntry(new ZipEntry(name)); zip.write(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            }
            byte[] bytes = {1, 2, 3, 4}; CRC32 crc = new CRC32(); crc.update(bytes);
            ZipEntry library = new ZipEntry("lib/arm64-v8a/libsample.so");
            library.setMethod(ZipEntry.STORED); library.setSize(bytes.length); library.setCrc(crc.getValue());
            zip.putNextEntry(library); zip.write(bytes); zip.closeEntry();
        }
        return input;
    }

    @Test public void repackPreservesOriginalAssetsOtherDexAndStoredLibraries() throws Exception {
        File input = source(); byte[] before = Files.readAllBytes(input.toPath());
        File replacement = temporary.newFile("patched.dex"); Files.write(replacement.toPath(), new byte[]{9, 8, 7});
        File output = temporary.newFile("output.apk");
        try (ZipFile zip = new ZipFile(input)) {
            SignatureBypassPatcher.repack(zip, Collections.singletonMap("classes.dex", replacement), output, "sample.app");
        }
        assertArrayEquals(before, Files.readAllBytes(input.toPath()));
        try (ZipFile original = new ZipFile(input); ZipFile result = new ZipFile(output)) {
            assertArrayEquals(new byte[]{9, 8, 7}, result.getInputStream(result.getEntry("classes.dex")).readAllBytes());
            for (String name : new String[]{"classes2.dex", "AndroidManifest.xml", "resources.arsc", "assets/data.bin",
                    "assets/untouched.RSA", "META-INF/services/sample", "lib/arm64-v8a/libsample.so"}) {
                assertArrayEquals(original.getInputStream(original.getEntry(name)).readAllBytes(),
                        result.getInputStream(result.getEntry(name)).readAllBytes());
            }
            assertEquals(ZipEntry.STORED, result.getEntry("lib/arm64-v8a/libsample.so").getMethod());
            assertNull(result.getEntry("META-INF/CERT.RSA")); assertNull(result.getEntry("META-INF/CERT.SF"));
            assertNull(result.getEntry("META-INF/MANIFEST.MF"));
            assertNotNull(result.getEntry("assets/hexora/signature-patch.properties"));
        }
    }

    @Test public void cancellationStopsRepackAndKeepsOriginal() throws Exception {
        File input = source(); byte[] before = Files.readAllBytes(input.toPath());
        try (ZipFile zip = new ZipFile(input)) {
            Thread.currentThread().interrupt();
            try {
                SignatureBypassPatcher.repack(zip, Collections.emptyMap(), temporary.newFile("cancelled.apk"), "sample.app");
                fail("Expected cancellation");
            } catch (java.io.IOException expected) {
                assertTrue(expected.getMessage().contains("cancelled"));
            } finally { Thread.interrupted(); }
        }
        assertArrayEquals(before, Files.readAllBytes(input.toPath()));
    }

    @Test public void snapshotIsIndependentFromSubsequentSourceEdits() throws Exception {
        File input = source(); byte[] before = Files.readAllBytes(input.toPath());
        File snapshot = SignatureBypassPatcher.stageSource(input, temporary.newFolder("work"));
        assertNotEquals(input.getCanonicalPath(), snapshot.getCanonicalPath());
        assertArrayEquals(before, Files.readAllBytes(snapshot.toPath()));
        Files.write(input.toPath(), new byte[]{5, 6, 7});
        assertArrayEquals(before, Files.readAllBytes(snapshot.toPath()));
    }
}
