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

    /** A complete page supplied by the disk-backed binary text viewer. */
    public static Result binaryPage(String text, Charset charset) {
        return new Result(text, Mode.BINARY, charset, new byte[0]);
    }

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
        Charset charset = sampleCharset(sample, count);
        int bomLength = sampleBomLength(sample, count);
        byte[] bom = java.util.Arrays.copyOf(sample, bomLength);
        if (isBinary(new String(sample, bomLength, count - bomLength, charset))) {
            return new Result(binaryTextPreview(input, charset, bomLength), Mode.BINARY, charset, bom);
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
                    return new Result(binaryTextPreview(new java.io.ByteArrayInputStream(sample, 0, count),
                            charset, bomLength), Mode.BINARY, charset, bom);
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

    public static Result readOnlyText(String value) throws IOException {
        Result bounded = fromText(value);
        return new Result(bounded.text, Mode.PREVIEW, StandardCharsets.UTF_8, new byte[0]);
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

    /** A bounded routing hint; read() still checks the complete editable document. */
    public static boolean isTextSample(byte[] bytes, int count) {
        int bomLength = sampleBomLength(bytes, count);
        return !isBinary(new String(bytes, bomLength, count - bomLength, sampleCharset(bytes, count)));
    }

    private static Charset sampleCharset(byte[] bytes, int count) {
        if (count >= 2 && (bytes[0] & 255) == 0xff && (bytes[1] & 255) == 0xfe) return StandardCharsets.UTF_16LE;
        if (count >= 2 && (bytes[0] & 255) == 0xfe && (bytes[1] & 255) == 0xff) return StandardCharsets.UTF_16BE;
        return StandardCharsets.UTF_8;
    }

    private static int sampleBomLength(byte[] bytes, int count) {
        if (count >= 3 && (bytes[0] & 255) == 0xef && (bytes[1] & 255) == 0xbb && (bytes[2] & 255) == 0xbf) return 3;
        return sampleCharset(bytes, count).equals(StandardCharsets.UTF_8) ? 0 : 2;
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

    /** Text viewer decodes bytes; only Hex editor presents offsets and hexadecimal columns. */
    private static String binaryTextPreview(InputStream input, Charset charset, int bomLength) throws IOException {
        for (int i = 0; i < bomLength; i++) input.read();
        InputStreamReader reader = new InputStreamReader(input, charset);
        StringBuilder text = new StringBuilder(8192);
        char[] buffer = new char[4096];
        int lines = 1, column = 0, decodedChars = 0;
        boolean previousCr = false;
        outer: while (true) {
            checkCancelled();
            int count = reader.read(buffer);
            if (count < 0) break;
            for (int i = 0; i < count; i++) {
                char c = buffer[i];
                if (++decodedChars > PREVIEW_CHARS) break outer;
                if (text.length() >= PREVIEW_CHARS) break outer;
                // MT's text presentation hides binary padding/control bytes instead of
                // replacing every NUL with a large diamond. Count them towards the read cap.
                if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') continue;
                if (c == '\r' || c == '\n') {
                    if (!(c == '\n' && previousCr)) lines++;
                    column = 0;
                } else column++;
                if (lines > PREVIEW_LINES || column > PREVIEW_LINE_CHARS) break outer;
                text.append(c);
                previousCr = c == '\r';
            }
        }
        if (text.length() > 0 && Character.isHighSurrogate(text.charAt(text.length() - 1))) {
            text.setLength(text.length() - 1);
        }
        return text.toString();
    }

    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Document load cancelled");
    }
}
