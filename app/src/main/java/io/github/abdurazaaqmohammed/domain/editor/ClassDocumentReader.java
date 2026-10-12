package io.github.abdurazaaqmohammed.domain.editor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.io.ByteArrayOutputStream;

/** JVM instructions and descriptors, never a character decoding of class bytes. */
public final class ClassDocumentReader {
    public static final int MAX_CLASS_BYTES = 2 * 1024 * 1024;
    private static final int MAX_OUTPUT = 256 * 1024;

    private ClassDocumentReader() { }

    public static String read(InputStream input) throws IOException {
        byte[] bytes = readBytes(input, MAX_CLASS_BYTES);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        if (bytes.length > MAX_CLASS_BYTES) throw new IOException("Class file is too large to inspect safely");
        if (bytes.length < 10 || bytes[0] != (byte) 0xCA || bytes[1] != (byte) 0xFE
                || bytes[2] != (byte) 0xBA || bytes[3] != (byte) 0xBE) {
            throw new IOException("Invalid JVM class file (expected CAFEBABE)");
        }
        BoundedWriter output = new BoundedWriter();
        try {
            new ClassReader(bytes).accept(new TraceClassVisitor(new PrintWriter(output)), 0);
        } catch (OutputLimit e) {
            output.text.append("\n// Bytecode preview truncated; original class is unchanged.\n");
        } catch (Cancelled e) {
            throw new InterruptedIOException();
        } catch (RuntimeException e) {
            throw new IOException("Cannot inspect this JVM class: " + e.getMessage(), e);
        }
        return output.text.toString();
    }

    static byte[] readBytes(InputStream input, int maximum) throws IOException {
        if (input == null) throw new IOException("Cannot open class file");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer, 0, Math.min(buffer.length, maximum + 1 - bytes.size()))) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            if (count == 0) continue;
            bytes.write(buffer, 0, count);
            if (bytes.size() > maximum) break;
        }
        return bytes.toByteArray();
    }

    private static final class OutputLimit extends RuntimeException { }
    private static final class Cancelled extends RuntimeException { }
    private static final class BoundedWriter extends Writer {
        final StringBuilder text = new StringBuilder();
        @Override public void write(char[] chars, int start, int count) {
            if (Thread.currentThread().isInterrupted()) throw new Cancelled();
            if (text.length() + count > MAX_OUTPUT) throw new OutputLimit();
            text.append(chars, start, count);
        }
        @Override public void flush() { }
        @Override public void close() { }
    }
}
