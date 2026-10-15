package io.github.abdurazaaqmohammed.domain.files;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.InflaterInputStream;

/** Reads the Android backup envelope, optionally preceded by Xiaomi's MIUI header. */
public final class AndroidBackup {
    private static final int HEADER_LIMIT = 64 * 1024;
    private AndroidBackup() { }

    public static boolean recognizes(InputStream input) throws IOException {
        byte[] prefix = new byte[15];
        int offset = 0, count;
        while (offset < prefix.length && (count = input.read(prefix, offset, prefix.length - offset)) != -1) offset += count;
        String magic = new String(prefix, 0, offset, StandardCharsets.US_ASCII);
        return magic.startsWith("MIUI BACKUP\n") || magic.startsWith("ANDROID BACKUP\n");
    }

    /** Caller owns and closes the returned stream. Encrypted backups fail explicitly. */
    public static InputStream openTar(InputStream source) throws IOException {
        BufferedInputStream input = new BufferedInputStream(source);
        boolean success = false;
        try {
            String magic = line(input);
            if ("MIUI BACKUP".equals(magic)) {
                int bytes = magic.length() + 1;
                while (bytes < HEADER_LIMIT) {
                    input.mark(512);
                    byte[] header = new byte[512];
                    int length = 0, count;
                    while (length < header.length && (count = input.read(header, length, header.length - length)) != -1) length += count;
                    input.reset();
                    if (length == 512 && tarHeader(header)) { success = true; return input; }
                    magic = line(input);
                    bytes += magic.length() + 1;
                    if ("ANDROID BACKUP".equals(magic)) break;
                }
            }
            if (!"ANDROID BACKUP".equals(magic)) throw new IOException("The Xiaomi backup header is invalid or unsupported.");
            int version;
            try { version = Integer.parseInt(line(input)); }
            catch (NumberFormatException e) { throw new IOException("Invalid Android backup version.", e); }
            if (version < 1 || version > 5) throw new IOException("Unsupported Android backup version: " + version);
            String compression = line(input), encryption = line(input);
            if (!"0".equals(compression) && !"1".equals(compression)) throw new IOException("Invalid backup compression flag.");
            if (!"none".equals(encryption)) throw new IOException("This backup is encrypted. Decrypt it with its backup password before opening.");
            InputStream result = "1".equals(compression) ? new InflaterInputStream(input) : input;
            success = true;
            return result;
        } finally { if (!success) input.close(); }
    }

    private static String line(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < 8192; i++) {
            int value = input.read();
            if (value == -1) throw new IOException("Truncated backup header.");
            if (value == '\n') return bytes.toString(StandardCharsets.UTF_8.name());
            bytes.write(value);
        }
        throw new IOException("Backup header line is too long.");
    }

    private static boolean tarHeader(byte[] header) {
        if (header[0] == 0) return false;
        long expected = 0;
        boolean digit = false;
        for (int i = 148; i < 156; i++) {
            int value = header[i] & 255;
            if (value == 0 || value == ' ') continue;
            if (value < '0' || value > '7') return false;
            expected = expected * 8 + value - '0';
            digit = true;
        }
        long sum = 0;
        for (int i = 0; i < 512; i++) sum += i >= 148 && i < 156 ? 32 : header[i] & 255;
        return digit && expected == sum;
    }
}
