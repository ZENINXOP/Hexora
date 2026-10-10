package io.github.abdurazaaqmohammed.utils;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.zip.CRC32;

import static org.junit.Assert.*;

/** Tests for runtime mapping and conservative ABI selection; no Android runtime required.
 * AI-assisted contribution: OpenAI Codex. */
public class SignaturePatchArchiveTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void storedUnicodeLibraryIsPageAlignedAfterCompressedData() throws Exception {
        File output = temporary.newFile("alignment.zip");
        byte[] bytes = new byte[70000];
        new java.util.Random(37).nextBytes(bytes);
        CRC32 crc = new CRC32(); crc.update(bytes);
        String library = "assets/lspatch/so/arm64-v8a/测试.so";
        try (SignaturePatchArchive writer = new SignaturePatchArchive(output)) {
            writer.add("assets/prefix", new ByteArrayInputStream(bytes), bytes.length, crc.getValue(), false, 0);
            writer.add(library, new ByteArrayInputStream(bytes), bytes.length, crc.getValue(), true, 16384);
        }
        try (org.apache.commons.compress.archivers.zip.ZipFile zip =
                     new org.apache.commons.compress.archivers.zip.ZipFile(output)) {
            ZipArchiveEntry entry = zip.getEntry(library);
            assertEquals(0, entry.getMethod());
            assertEquals(0, entry.getDataOffset() % 16384);
            assertArrayEquals(bytes, zip.getInputStream(entry).readAllBytes());
        }
    }

    @Test public void alignmentCoversEveryPossiblePageOffsetAndUtf8NameLength() {
        for (int alignment : new int[]{4, 16384}) {
            for (String name : Arrays.asList("resources.arsc", LspatchSignaturePatcher.ORIGINAL, "assets/文档.so")) {
                for (int offset = 0; offset < alignment; offset++) {
                    byte[] extra = SignaturePatchArchive.alignmentExtra(offset, name, alignment);
                    assertEquals(0, (offset + 30 + name.getBytes(StandardCharsets.UTF_8).length + extra.length) % alignment);
                    assertEquals(extra.length - 4, (extra[2] & 255) | (extra[3] & 255) << 8);
                    assertEquals(alignment, (extra[4] & 255) | (extra[5] & 255) << 8);
                }
            }
        }
    }

    @Test public void fullOriginalBytesAreKeptWithoutReencoding() throws Exception {
        byte[] original = {0x50, 0x4b, 3, 4, 0, -1, 7, 10, 13};
        File input = temporary.newFile("original.bin");
        java.nio.file.Files.write(input.toPath(), original);
        File output = temporary.newFile("embedded.zip");
        try (SignaturePatchArchive writer = new SignaturePatchArchive(output)) {
            writer.addFile(LspatchSignaturePatcher.ORIGINAL, input, true, 16384);
        }
        try (org.apache.commons.compress.archivers.zip.ZipFile zip =
                     new org.apache.commons.compress.archivers.zip.ZipFile(output)) {
            ZipArchiveEntry entry = zip.getEntry(LspatchSignaturePatcher.ORIGINAL);
            assertArrayEquals(original, zip.getInputStream(entry).readAllBytes());
            assertEquals(0, entry.getDataOffset() % 16384);
        }
        assertArrayEquals(original, java.nio.file.Files.readAllBytes(input.toPath()));
    }

    @Test public void cancellationStopsBeforeStartingAnEntry() throws Exception {
        File output = temporary.newFile("cancelled.zip");
        try (SignaturePatchArchive writer = new SignaturePatchArchive(output)) {
            Thread.currentThread().interrupt();
            try {
                writer.add("data", new ByteArrayInputStream(new byte[]{1}), 1, 0, false, 0);
                fail("Expected cancellation");
            } catch (java.io.IOException expected) {
                assertTrue(expected.getMessage().contains("cancelled"));
            } finally { Thread.interrupted(); }
        }
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(output)) { assertEquals(0, zip.size()); }
    }

    @Test public void experimentalNativeModeRequiresExistingArm64OnlyApk() {
        assertTrue(LspatchSignaturePatcher.nativeModeSupported(Collections.singleton("arm64-v8a")));
        assertFalse(LspatchSignaturePatcher.nativeModeSupported(Collections.emptySet()));
        assertFalse(LspatchSignaturePatcher.nativeModeSupported(Collections.singleton("x86_64")));
        assertFalse(LspatchSignaturePatcher.nativeModeSupported(new HashSet<>(Arrays.asList("arm64-v8a", "armeabi-v7a"))));
    }

    @Test public void misleadingEntrySizeStopsStreamingEvenForCompressedEntries() throws Exception {
        try (SignaturePatchArchive writer = new SignaturePatchArchive(temporary.newFile("bad-size.zip"))) {
            try {
                writer.add("assets/payload", new ByteArrayInputStream(new byte[]{1, 2, 3}), 1, 0, false, 0);
                fail("Expected a size mismatch");
            } catch (java.io.IOException expected) {
                assertTrue(expected.getMessage().contains("declared size"));
            }
        }
    }
}
