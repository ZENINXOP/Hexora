package io.github.abdurazaaqmohammed.domain.files;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;

import static org.junit.Assert.*;

public class AndroidBackupTest {
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static byte[] read(InputStream input) throws IOException {
        try (InputStream source = input) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = source.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    @Test public void ordinaryBakIsNotAnAndroidBackup() throws Exception {
        assertFalse(AndroidBackup.recognizes(new ByteArrayInputStream(bytes("ordinary original file"))));
        assertFalse(AndroidBackup.recognizes(new ByteArrayInputStream(new byte[0])));
    }

    @Test public void readsUncompressedAndroidBackup() throws Exception {
        assertArrayEquals(bytes("tar bytes"), read(AndroidBackup.openTar(
                new ByteArrayInputStream(bytes("ANDROID BACKUP\n5\n0\nnone\ntar bytes")))));
    }

    @Test public void readsXiaomiHeaderAndCompressedAndroidPayload() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(bytes("MIUI BACKUP\n2\napp.package\nExample app\n0\nANDROID BACKUP\n5\n1\nnone\n"));
        try (DeflaterOutputStream compressor = new DeflaterOutputStream(output)) { compressor.write(bytes("tar payload")); }
        assertTrue(AndroidBackup.recognizes(new ByteArrayInputStream(output.toByteArray())));
        assertArrayEquals(bytes("tar payload"), read(AndroidBackup.openTar(new ByteArrayInputStream(output.toByteArray()))));
    }

    @Test public void xiaomiRawTarPayloadRetainsItsFirstHeader() throws Exception {
        byte[] tar = new byte[1024];
        System.arraycopy(bytes("apps/example/file"), 0, tar, 0, 17);
        java.util.Arrays.fill(tar, 148, 156, (byte) ' ');
        long sum = 0;
        for (int i = 0; i < 512; i++) sum += tar[i] & 255;
        System.arraycopy(bytes(String.format(java.util.Locale.ROOT, "%06o\0 ", sum)), 0, tar, 148, 8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(bytes("MIUI BACKUP\n2\nexample\nExample\n0\n"));
        output.write(tar);
        assertArrayEquals(tar, read(AndroidBackup.openTar(new ByteArrayInputStream(output.toByteArray()))));
    }

    @Test public void malformedAndEncryptedHeadersFailClearly() {
        for (String header : new String[]{"ANDROID BACKUP\n", "ANDROID BACKUP\n99\n0\nnone\n",
                "ANDROID BACKUP\n5\n2\nnone\n", "MIUI BACKUP\nnot a payload\n"})
            assertThrows(IOException.class, () -> AndroidBackup.openTar(new ByteArrayInputStream(bytes(header))));
        IOException error = assertThrows(IOException.class, () -> AndroidBackup.openTar(
                new ByteArrayInputStream(bytes("ANDROID BACKUP\n5\n1\nAES-256\n"))));
        assertTrue(error.getMessage().contains("encrypted"));
    }
}
