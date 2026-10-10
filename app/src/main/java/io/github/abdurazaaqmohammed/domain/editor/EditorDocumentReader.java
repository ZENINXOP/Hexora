package io.github.abdurazaaqmohammed.domain.editor;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Bounded, cancellable document loading. A partial document must never be writable. */
public final class EditorDocumentReader {
    public static final int MAX_CHARS = 4 * 1024 * 1024;
    public static final int MAX_LINES = 80_000;
    public static final int MAX_LINE_CHARS = 32_768;
    public static final int PREVIEW_CHARS = 65_536;
    public static final int PREVIEW_LINES = 2_000;
    public static final int PREVIEW_LINE_CHARS = 2_048;
    public static final long MAX_BYTES = 8L * 1024 * 1024;
    public enum Mode { EDIT, LARGE, PREVIEW, BINARY }

    public static final class Result {
        public final String text;
        public final Mode mode;
        public final Charset charset;
        public final byte[] bom;
        private Result(String text, Mode mode, Charset charset, byte[] bom) {
            this.text = text;
            this.mode = mode;
            this.charset = charset;
            this.bom = bom;
        }
        public boolean isReadOnly() { return mode == Mode.PREVIEW || mode == Mode.BINARY; }
        public boolean isLightweight() { return mode != Mode.EDIT; }
    }

    private EditorDocumentReader() { }

    // Does not own the stream; callers close it on success, failure and cancellation.
    public static Result read(InputStream source, long knownBytes) throws IOException {
        if (source == null) throw new IOException("Cannot open document");
        BufferedInputStream input = new BufferedInputStream(source, 8192);
        input.mark(8193);
        byte[] sample = new byte[8192];
        int count = 0;
        while (count < sample.length) {
            checkCancelled();
            int n = input.read(sample, count, sample.length - count);
            if (n < 0) break;
            if (n == 0) continue;
            count += n;
        }
        input.reset();
        Charset charset = StandardCharsets.UTF_8;
        int bomLength = 0;
        if (count >= 3 && (sample[0] & 255) == 0xef && (sample[1] & 255) == 0xbb && (sample[2] & 255) == 0xbf) {
            bomLength = 3;
        } else if (count >= 2 && (sample[0] & 255) == 0xff && (sample[1] & 255) == 0xfe) {
            charset = StandardCharsets.UTF_16LE; bomLength = 2;
        } else if (count >= 2 && (sample[0] & 255) == 0xfe && (sample[1] & 255) == 0xff) {
            charset = StandardCharsets.UTF_16BE; bomLength = 2;
        }
        byte[] bom = java.util.Arrays.copyOf(sample, bomLength);
        if (isBinary(new String(sample, bomLength, count - bomLength, charset))) {
            return new Result(hexPreview(sample, Math.min(count, 4096)), Mode.BINARY, charset, bom);
        }
        for (int i = 0; i < bomLength; i++) input.read();
        InputStreamReader reader = new InputStreamReader(input, charset);
        boolean preview = knownBytes > MAX_BYTES;
        // Keep room for editor lines, layout, undo, and a second document during a tab switch.
        int charLimit = (int) Math.min(MAX_CHARS, Math.max(PREVIEW_CHARS, Runtime.getRuntime().maxMemory() / 64));
        if (preview) charLimit = PREVIEW_CHARS;
        StringBuilder text = new StringBuilder(Math.min(charLimit, 8192));
        char[] buffer = new char[8192];
        int lines = 1, column = 0, maxColumn = 0;
        boolean previousCr = false;
        outer: while (true) {
            checkCancelled();
            int n = reader.read(buffer);
            if (n < 0) break;
            for (int i = 0; i < n; i++) {
                char c = buffer[i];
                if (c == 0 || (c < 32 && c != '\n' && c != '\r' && c != '\t' && c != '\f')) {
                    return new Result(hexPreview(sample, Math.min(count, 4096)), Mode.BINARY, charset, bom);
                }
                if (text.length() >= charLimit) { preview = true; break outer; }
                text.append(c);
                if (c == '\r' || c == '\n') {
                    if (!(c == '\n' && previousCr)) lines++;
                    column = 0;
                } else {
                    maxColumn = Math.max(maxColumn, ++column);
                }
                previousCr = c == '\r';
                if (lines > (preview ? PREVIEW_LINES : MAX_LINES)
                        || column > (preview ? PREVIEW_LINE_CHARS : MAX_LINE_CHARS)) {
                    preview = true; break outer;
                }
            }
        }
        String value = text.toString();
        if (preview) value = previewPrefix(value);
        Mode mode = preview ? Mode.PREVIEW
                : value.length() > 256 * 1024 || lines > 10_000 || maxColumn > 4096 ? Mode.LARGE : Mode.EDIT;
        return new Result(value, mode, charset, bom);
    }

    /** Also bounds already decoded XML, shared text, and restored unsaved content. */
    public static Result fromText(String value) throws IOException {
        if (value == null) value = "";
        int charLimit = (int) Math.min(MAX_CHARS, Math.max(PREVIEW_CHARS, Runtime.getRuntime().maxMemory() / 64));
        int lines = 1, column = 0, maxColumn = 0;
        boolean previousCr = false;
        for (int i = 0; i < value.length(); i++) {
            if ((i & 8191) == 0) checkCancelled();
            char c = value.charAt(i);
            if (c == '\n' || c == '\r') {
                if (!(c == '\n' && previousCr)) lines++;
                column = 0;
            } else maxColumn = Math.max(maxColumn, ++column);
            previousCr = c == '\r';
            if (i >= charLimit || lines > MAX_LINES || column > MAX_LINE_CHARS) {
                return new Result(previewPrefix(value), Mode.PREVIEW, StandardCharsets.UTF_8, new byte[0]);
            }
        }
        return new Result(value, value.length() > 256 * 1024 || lines > 10_000 || maxColumn > 4096
                ? Mode.LARGE : Mode.EDIT, StandardCharsets.UTF_8, new byte[0]);
    }

    public static byte[] encode(String text, Charset charset, byte[] bom) {
        byte[] body = text.getBytes(charset);
        if (bom.length == 0) return body;
        byte[] bytes = java.util.Arrays.copyOf(bom, bom.length + body.length);
        System.arraycopy(body, 0, bytes, bom.length, body.length);
        return bytes;
    }

    private static boolean isBinary(String value) {
        int controls = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == 0) return true;
            if (c < 32 && c != '\n' && c != '\r' && c != '\t' && c != '\f') controls++;
        }
        return controls > 0; // Source text should not contain binary control characters.
    }

    private static String previewPrefix(String value) {
        int end = Math.min(value.length(), PREVIEW_CHARS), lines = 1, column = 0;
        boolean previousCr = false;
        for (int i = 0; i < end; i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\r') {
                if (!(c == '\n' && previousCr)) lines++;
                column = 0;
            } else column++;
            previousCr = c == '\r';
            if (lines > PREVIEW_LINES || column > PREVIEW_LINE_CHARS) { end = i; break; }
        }
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }

    private static String hexPreview(byte[] bytes, int count) {
        char[] hex = "0123456789ABCDEF".toCharArray();
        StringBuilder out = new StringBuilder(count * 5);
        for (int offset = 0; offset < count; offset += 16) {
            for (int shift = 28; shift >= 0; shift -= 4) out.append(hex[(offset >>> shift) & 15]);
            out.append("  ");
            for (int j = 0; j < 16; j++) {
                if (offset + j < count) {
                    int b = bytes[offset + j] & 255;
                    out.append(hex[b >>> 4]).append(hex[b & 15]).append(' ');
                } else out.append("   ");
            }
            out.append(" |");
            for (int j = offset; j < Math.min(count, offset + 16); j++) {
                int b = bytes[j] & 255;
                out.append(b >= 32 && b < 127 ? (char) b : '.');
            }
            out.append("|\n");
        }
        return out.toString();
    }

    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Document load cancelled");
    }
}
