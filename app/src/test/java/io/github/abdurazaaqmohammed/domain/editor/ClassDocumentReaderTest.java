package io.github.abdurazaaqmohammed.domain.editor;

import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import static org.junit.Assert.*;

public class ClassDocumentReaderTest {
    private byte[] fixture() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "OwnedGreeting", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "add", "(II)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ILOAD, 0); method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitInsn(Opcodes.IADD); method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(2, 2); method.visitEnd(); writer.visitEnd();
        return writer.toByteArray();
    }
    @Test public void displaysMethodsDescriptorsAndInstructionsWithoutChangingBytes() throws Exception {
        byte[] bytes = fixture(), original = bytes.clone();
        String text = ClassDocumentReader.read(new ByteArrayInputStream(bytes));
        assertTrue(text.contains("OwnedGreeting")); assertTrue(text.contains("add(II)I"));
        assertTrue(text.contains("ILOAD 0")); assertTrue(text.contains("IADD"));
        assertTrue(text.contains("MAXSTACK = 2")); assertArrayEquals(original, bytes);
    }
    @Test public void convertsSameJvmClassToReadableJava() throws Exception {
        String java = ClassJavaDecompiler.read(new ByteArrayInputStream(fixture()));
        assertTrue(java.contains("class OwnedGreeting")); assertTrue(java.contains("int add("));
        assertTrue(java.contains("return")); assertTrue(java.contains(" + "));
    }
    @Test public void rejectsInvalidTruncatedAndOversizedFiles() throws Exception {
        for (byte[] bytes : new byte[][]{new byte[0], "not a class".getBytes(),
                new byte[]{(byte)0xCA,(byte)0xFE,(byte)0xBA,(byte)0xBE,0,0,0,52,0,5},
                new byte[ClassDocumentReader.MAX_CLASS_BYTES + 1]}) {
            assertThrows(IOException.class, () -> ClassDocumentReader.read(new ByteArrayInputStream(bytes)));
        }
    }
    @Test public void previewsCannotBeSavedAsClassBytes() throws Exception {
        EditorDocumentReader.Result result = EditorDocumentReader.readOnlyText(
                ClassDocumentReader.read(new ByteArrayInputStream(fixture())));
        assertTrue(result.isReadOnly()); assertEquals(EditorDocumentReader.Mode.PREVIEW, result.mode);
        assertEquals(SyntaxFormat.JVM, SyntaxFormat.forFilename("preview.jvmb"));
        assertTrue(new SyntaxLexer(SyntaxFormat.JVM).tokenize("  ILOAD 0", null).tokens.stream()
                .anyMatch(t -> t.kind == SyntaxLexer.Kind.KEYWORD));
    }
}
