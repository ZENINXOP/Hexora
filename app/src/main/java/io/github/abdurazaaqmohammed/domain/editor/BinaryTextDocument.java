package io.github.abdurazaaqmohammed.domain.editor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Complete decoded binary text on disk; only one bounded page enters the editor. */
public final class BinaryTextDocument {
    private final File cache;
    private final List<Long> offsets;
    public final Charset charset;

    private BinaryTextDocument(File cache, List<Long> offsets, Charset charset) {
        this.cache = cache;
        this.offsets = offsets;
        this.charset = charset;
    }

    // The caller owns the input. Every byte is decoded, including bytes after NUL padding.
    public static BinaryTextDocument create(InputStream input, Charset charset, byte[] bom,
                                             File cacheDirectory) throws IOException {
        File cache = File.createTempFile("library-text-", ".utf8", cacheDirectory);
        boolean complete = false;
        try (FileOutputStream output = new FileOutputStream(cache)) {
            for (int i = 0; i < bom.length; i++) input.read();
            InputStreamReader reader = new InputStreamReader(input, charset);
            List<Long> offsets = new ArrayList<>();
            offsets.add(0L);
            long written = 0;
            StringBuilder page = new StringBuilder(65_536);
            char[] buffer = new char[8192];
            int lines = 0, count;
            while ((count = reader.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                for (int i = 0; i < count; i++) {
                    char c = buffer[i];
                    if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') continue;
                    page.append(c);
                    if (c == '\n') lines++;
                    if ((page.length() >= 65_536 || lines >= 2000) && !Character.isHighSurrogate(c)) {
                        byte[] bytes = page.toString().getBytes(StandardCharsets.UTF_8);
                        output.write(bytes);
                        written += bytes.length;
                        offsets.add(written);
                        page.setLength(0);
                        lines = 0;
                    }
                }
            }
            if (page.length() > 0) {
                byte[] bytes = page.toString().getBytes(StandardCharsets.UTF_8);
                output.write(bytes);
                written += bytes.length;
                offsets.add(written);
            }
            if (offsets.size() == 1) offsets.add(0L);
            complete = true;
            return new BinaryTextDocument(cache, offsets, charset);
        } finally {
            if (!complete) cache.delete();
        }
    }

    public int pageCount() { return offsets.size() - 1; }

    public EditorDocumentReader.Result readPage(int page) throws IOException {
        if (page < 0 || page >= pageCount()) throw new IOException("Text page is unavailable");
        long start = offsets.get(page), end = offsets.get(page + 1);
        byte[] bytes = new byte[(int) (end - start)];
        try (RandomAccessFile input = new RandomAccessFile(cache, "r")) {
            input.seek(start);
            input.readFully(bytes);
        }
        return EditorDocumentReader.binaryPage(new String(bytes, StandardCharsets.UTF_8), charset);
    }

    public void close() { cache.delete(); }
}
