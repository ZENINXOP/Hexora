package io.github.abdurazaaqmohammed.domain.editor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.plugins.input.java.JavaInputPlugin;

/** Uses the existing JADX engine with its JVM input plugin; never executes input classes. */
public final class ClassJavaDecompiler {
    public static final int MAX_BYTES = 512 * 1024;
    private ClassJavaDecompiler() { }

    public static String read(InputStream input) throws IOException {
        byte[] bytes = ClassDocumentReader.readBytes(input, MAX_BYTES);
        if (bytes.length > MAX_BYTES) throw new IOException("Class file is too large for Java conversion; use the bytecode viewer");
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        JadxArgs args = new JadxArgs();
        args.setSkipResources(true);
        args.setThreadsCount(1);
        args.setShowInconsistentCode(true);
        try (JadxDecompiler decompiler = new JadxDecompiler(args)) {
            decompiler.addCustomLoad(JavaInputPlugin.loadSingleClass(bytes, "preview.class"));
            decompiler.load();
            if (decompiler.getClasses().isEmpty()) throw new IOException("No JVM class found");
            JavaClass cls = decompiler.getClasses().get(0);
            cls.decompile();
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            return cls.getCode();
        } catch (RuntimeException e) {
            throw new IOException("Cannot convert this class to Java: " + e.getMessage(), e);
        }
    }
}
