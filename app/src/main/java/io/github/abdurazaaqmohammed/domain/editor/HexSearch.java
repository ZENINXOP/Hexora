package io.github.abdurazaaqmohammed.domain.editor;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.util.NavigableMap;

/** Buffered linear-time search over file bytes plus unsaved edits. OpenAI Codex assisted. */
public final class HexSearch {
    private HexSearch() {}

    public static long find(File file, long size, NavigableMap<Long, Integer> edits,
            byte[] pattern, long start, boolean forward) throws IOException {
        if (pattern.length == 0 || pattern.length > size) return -1;
        if (!forward && start < 0) return -1;
        byte[] needle = pattern.clone();
        if (!forward) for (int i = 0; i < needle.length / 2; i++) {
            byte value = needle[i];
            needle[i] = needle[needle.length - 1 - i];
            needle[needle.length - 1 - i] = value;
        }
        int[] prefix = new int[needle.length];
        for (int i = 1, j = 0; i < needle.length; i++) {
            if ((i & 4095) == 0) checkCancelled();
            while (j > 0 && needle[i] != needle[j]) j = prefix[j - 1];
            if (needle[i] == needle[j]) j++;
            prefix[i] = j;
        }
        long cursor = forward ? Math.max(0, start) : Math.min(start, size - needle.length) + needle.length - 1;
        if (cursor < 0 || cursor >= size) return -1;
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            byte[] buffer = new byte[64 * 1024];
            int matched = 0;
            while (cursor >= 0 && cursor < size) {
                checkCancelled();
                long offset = forward ? cursor : Math.max(0, cursor - buffer.length + 1);
                int count = (int) Math.min(buffer.length, forward ? size - cursor : cursor - offset + 1);
                input.seek(offset);
                input.readFully(buffer, 0, count);
                for (var edit : edits.subMap(offset, true, offset + count, false).entrySet())
                    buffer[(int) (edit.getKey() - offset)] = (byte) edit.getValue().intValue();
                for (int step = 0; step < count; step++) {
                    if ((step & 4095) == 0) checkCancelled();
                    int index = forward ? step : count - 1 - step;
                    byte value = buffer[index];
                    while (matched > 0 && value != needle[matched]) matched = prefix[matched - 1];
                    if (value == needle[matched]) matched++;
                    if (matched == needle.length) return forward ? offset + index - needle.length + 1 : offset + index;
                }
                cursor = forward ? offset + count : offset - 1;
            }
        }
        return -1;
    }

    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Search cancelled");
    }
}
