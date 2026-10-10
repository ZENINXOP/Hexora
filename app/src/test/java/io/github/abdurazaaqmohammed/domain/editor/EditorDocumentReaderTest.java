package io.github.abdurazaaqmohammed.domain.editor;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class EditorDocumentReaderTest {
    private EditorDocumentReader.Result read(String text) throws Exception {
        return EditorDocumentReader.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), -1);
    }

    @Test public void preservesLineEndingsAndMissingFinalNewline() throws Exception {
        String text = "first\r\nsecond\rthird\nlast";
        EditorDocumentReader.Result result = read(text);
        assertEquals(text, result.text);
        assertEquals(EditorDocumentReader.Mode.EDIT, result.mode);
        assertFalse(result.isReadOnly());
        assertEquals("", read("").text);
    }

    @Test public void fullLargeTextRemainsEditableWithoutAnalysis() throws Exception {
        String text = "a short line with enough characters\n".repeat(10_001);
        EditorDocumentReader.Result result = read(text);
        assertEquals(text, result.text);
        assertEquals(EditorDocumentReader.Mode.LARGE, result.mode);
        assertTrue(result.isLightweight());
        assertFalse(result.isReadOnly());
    }

    @Test public void boundsUnknownLengthStreamsBeforeAllocatingUnboundedText() throws Exception {
        final int[] bytesRead = {0};
        InputStream unlimited = new InputStream() {
            public int read() { bytesRead[0]++; return 'a'; }
            public int read(byte[] bytes, int offset, int length) {
                java.util.Arrays.fill(bytes, offset, offset + length, (byte) 'a');
                bytesRead[0] += length;
                return length;
            }
        };
        EditorDocumentReader.Result result = EditorDocumentReader.read(unlimited, -1);
        assertTrue(result.isReadOnly());
        assertEquals(EditorDocumentReader.Mode.PREVIEW, result.mode);
        assertTrue(result.text.length() <= EditorDocumentReader.PREVIEW_LINE_CHARS);
        assertTrue(bytesRead[0] < EditorDocumentReader.MAX_LINE_CHARS + 32_768);
    }

    @Test public void largeKnownFilesReadOnlyABoundedPrefix() throws Exception {
        final int[] bytesRead = {0};
        InputStream unlimited = new InputStream() {
            public int read() { bytesRead[0]++; return 'x'; }
        };
        EditorDocumentReader.Result result = EditorDocumentReader.read(unlimited, 500L * 1024 * 1024);
        assertEquals(EditorDocumentReader.Mode.PREVIEW, result.mode);
        assertTrue(result.isReadOnly());
        assertTrue(bytesRead[0] < 32_768);
    }

    @Test public void lineCountLimitDoesNotMistakeCrLfForTwoLines() throws Exception {
        String text = "x\r\n".repeat(40_000);
        assertFalse(read(text).isReadOnly());
        EditorDocumentReader.Result result = read("x\n".repeat(EditorDocumentReader.MAX_LINES + 1));
        assertTrue(result.isReadOnly());
        assertTrue(result.text.lines().count() <= EditorDocumentReader.PREVIEW_LINES);
    }

    @Test public void charLimitProducesAnExplicitReadOnlyPreview() throws Exception {
        String text = ("a".repeat(1000) + "\n").repeat(EditorDocumentReader.MAX_CHARS / 1000 + 1);
        EditorDocumentReader.Result result = read(text);
        assertEquals(EditorDocumentReader.Mode.PREVIEW, result.mode);
        assertTrue(result.isReadOnly());
        assertTrue(result.text.length() <= EditorDocumentReader.PREVIEW_CHARS);
        assertTrue(text.startsWith(result.text));
    }

    @Test public void binaryLibrariesDoNotBecomeHugeUneditableLines() throws Exception {
        byte[] bytes = new byte[]{0x7f, 'E', 'L', 'F', 2, 1, 1, 0, 10, 32, 65};
        EditorDocumentReader.Result result = EditorDocumentReader.read(new ByteArrayInputStream(bytes), bytes.length);
        assertEquals(EditorDocumentReader.Mode.BINARY, result.mode);
        assertTrue(result.isReadOnly());
        assertTrue(result.text.contains("7F 45 4C 46"));
        assertTrue(result.text.contains("00000000"));
    }

    @Test public void detectsBinaryControlsAfterTheInitialSample() throws Exception {
        byte[] bytes = ("text\n".repeat(2000) + "\0binary").getBytes(StandardCharsets.UTF_8);
        assertEquals(EditorDocumentReader.Mode.BINARY,
                EditorDocumentReader.read(new ByteArrayInputStream(bytes), -1).mode);
    }

    @Test public void extensionDoesNotTurnTextLibrariesIntoBinary() throws Exception {
        // Classification is based on contents, so .lib/.so names can still contain text.
        assertEquals(EditorDocumentReader.Mode.EDIT, read("LIBRARY sample\nEXPORTS\n  hello").mode);
    }

    @Test public void bomTextRoundTripsWithoutCorruptingOriginalEncoding() throws Exception {
        String text = "Unicode αβ\r\n你好";
        for (java.nio.charset.Charset charset : new java.nio.charset.Charset[]{
                StandardCharsets.UTF_8, StandardCharsets.UTF_16LE, StandardCharsets.UTF_16BE}) {
            byte[] bom = charset.equals(StandardCharsets.UTF_8) ? new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf}
                    : charset.equals(StandardCharsets.UTF_16LE) ? new byte[]{(byte) 0xff, (byte) 0xfe}
                    : new byte[]{(byte) 0xfe, (byte) 0xff};
            byte[] original = EditorDocumentReader.encode(text, charset, bom);
            EditorDocumentReader.Result result = EditorDocumentReader.read(new ByteArrayInputStream(original), original.length);
            assertEquals(text, result.text);
            assertFalse(result.isReadOnly());
            assertArrayEquals(original, EditorDocumentReader.encode(result.text, result.charset, result.bom));
        }
    }

    @Test public void sharedOrDecodedContentUsesTheSameLimits() throws Exception {
        EditorDocumentReader.Result result = EditorDocumentReader.fromText("x".repeat(EditorDocumentReader.MAX_LINE_CHARS + 1));
        assertEquals(EditorDocumentReader.Mode.PREVIEW, result.mode);
        assertTrue(result.isReadOnly());
        assertEquals(EditorDocumentReader.PREVIEW_LINE_CHARS, result.text.length());
    }

    @Test public void previewDoesNotSplitUnicodeSurrogatePairs() throws Exception {
        String text = "x".repeat(EditorDocumentReader.PREVIEW_LINE_CHARS - 1) + "😀"
                + "x".repeat(EditorDocumentReader.MAX_LINE_CHARS);
        String preview = EditorDocumentReader.fromText(text).text;
        assertFalse(Character.isHighSurrogate(preview.charAt(preview.length() - 1)));
    }

    @Test public void cancelledLoadsStopBeforeReading() throws Exception {
        Thread.currentThread().interrupt();
        try {
            read("old tab");
            fail("Expected cancellation");
        } catch (InterruptedIOException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertEquals("next tab", read("next tab").text);
    }

    @Test public void doesNotCloseCallerOwnedStream() throws Exception {
        final boolean[] closed = {false};
        ByteArrayInputStream input = new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)) {
            @Override public void close() { closed[0] = true; }
        };
        assertEquals("ok", EditorDocumentReader.read(input, -1).text);
        assertFalse(closed[0]);
    }
}
