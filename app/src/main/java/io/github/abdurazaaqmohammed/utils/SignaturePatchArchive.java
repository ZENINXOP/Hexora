package io.github.abdurazaaqmohammed.utils;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Streaming ZIP writer with explicit alignment for APK-mapped runtime libraries.
 * AI-assisted contribution: OpenAI Codex. */
final class SignaturePatchArchive implements Closeable {
    private final CountingOutput output;
    private final ZipOutputStream zip;

    SignaturePatchArchive(File file) throws IOException {
        output = new CountingOutput(new FileOutputStream(file));
        zip = new ZipOutputStream(output);
    }

    void addFile(String name, File file, boolean stored, int alignment) throws IOException {
        CRC32 crc = new CRC32();
        if (stored) {
            try (InputStream input = new FileInputStream(file)) {
                byte[] bytes = new byte[64 * 1024];
                int count;
                while ((count = input.read(bytes)) != -1) {
                    SignatureBypassPatcher.checkCancelled();
                    crc.update(bytes, 0, count);
                }
            }
        }
        try (InputStream input = new FileInputStream(file)) {
            add(name, input, file.length(), crc.getValue(), stored, alignment);
        }
    }

    void add(String name, InputStream input, long size, long crc, boolean stored, int alignment) throws IOException {
        SignatureBypassPatcher.checkCancelled();
        ZipEntry entry = new ZipEntry(name);
        // A fixed DOS-range time prevents automatic timestamp extras changing the data offset.
        entry.setTime(946684800000L);
        if (stored) {
            if (size < 0 || size >= 0xffffffffL) throw new IOException("ZIP entry is too large: " + name);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(size);
            entry.setCrc(crc);
            if (alignment > 1) entry.setExtra(alignmentExtra(output.count, name, alignment));
        }
        zip.putNextEntry(entry);
        byte[] buffer = new byte[64 * 1024];
        long copied = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            SignatureBypassPatcher.checkCancelled();
            copied += count;
            // Known-size entries must match the inspected directory; unknown-size
            // inputs are only the small bundled license/provenance streams.
            if (copied > (size >= 0 ? size : 128 * 1024))
                throw new IOException("Archive entry exceeds its declared size: " + name);
            zip.write(buffer, 0, count);
        }
        if (size >= 0 && copied != size) throw new IOException("Archive entry size mismatch: " + name);
        zip.closeEntry();
        if (output.count >= 0xffffffffL) throw new IOException("Advanced output exceeds the ZIP size limit.");
    }

    static byte[] alignmentExtra(long offset, String name, int alignment) {
        if (alignment != 4 && alignment != 16384) throw new IllegalArgumentException("Unsupported APK alignment");
        long header = offset + 30 + name.getBytes(StandardCharsets.UTF_8).length;
        int padding = (int) ((alignment - (header + 6) % alignment) % alignment);
        byte[] extra = new byte[padding + 6];
        // apksig reads the first two payload bytes as the alignment multiple;
        // record it so signing can preserve alignment when entry offsets move.
        extra[0] = 0x35;
        extra[1] = (byte) 0xd9;
        extra[2] = (byte) (padding + 2);
        extra[3] = (byte) ((padding + 2) >>> 8);
        extra[4] = (byte) alignment;
        extra[5] = (byte) (alignment >>> 8);
        return extra;
    }

    @Override public void close() throws IOException { zip.close(); }

    private static final class CountingOutput extends FilterOutputStream {
        long count;
        CountingOutput(OutputStream output) { super(output); }
        @Override public void write(int value) throws IOException { out.write(value); count++; }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
            count += length;
        }
    }
}
