package io.github.abdurazaaqmohammed.domain.editor;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.writer.io.DeferredOutputStreamFactory;
import com.android.tools.smali.dexlib2.writer.io.DexDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.smali.SmaliOptions;
import com.android.tools.smali.smali2.Smali;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class DexSaveWriterTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void writesLargeMethodUsingDiskScratchAndRetainsOriginalBackup() throws Exception {
        StringBuilder code = new StringBuilder(".class public Lqa/Large;\n.super Ljava/lang/Object;\n"
                + ".method public static run()V\n.registers 0\n");
        for (int i = 0; i < 40000; i++) code.append("nop\n");
        code.append("return-void\n.end method\n");
        DexPool pool = new DexPool(Opcodes.forDexVersion(35));
        pool.internClass(Smali.assemble(code.toString(), new SmaliOptions(), 35));
        File destination = tmp.newFile("classes.dex");
        Files.write(destination.toPath(), new byte[]{1, 2, 3});
        DexSaveWriter.write(pool, destination);
        byte[] bytes = Files.readAllBytes(destination.toPath());
        assertTrue(bytes.length > 64 * 1024);
        DexBackedDexFile dex = new DexBackedDexFile(Opcodes.forDexVersion(35), bytes);
        assertEquals("Lqa/Large;", dex.getClasses().iterator().next().getType());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(new File(tmp.getRoot(), "classes.dex.bak").toPath()));
        assertEquals(2, tmp.getRoot().list().length);
    }

    @Test public void failedWriteDoesNotReplaceOriginalOrLeaveScratch() throws Exception {
        File destination = tmp.newFile("classes.dex");
        Files.write(destination.toPath(), new byte[]{4, 5, 6});
        DexPool failing = new DexPool(Opcodes.forDexVersion(35)) {
            @Override public void writeTo(DexDataStore output, DeferredOutputStreamFactory scratch) throws IOException {
                try {
                    output.outputAt(0).write(new byte[]{9, 9});
                    scratch.makeDeferredOutputStream().write(new byte[70000]);
                    throw new IOException("disk full");
                } finally { output.close(); }
            }
        };
        assertThrows(IOException.class, () -> DexSaveWriter.write(failing, destination));
        assertArrayEquals(new byte[]{4, 5, 6}, Files.readAllBytes(destination.toPath()));
        assertEquals(1, tmp.getRoot().list().length);
    }

    @Test public void cancellationBeforePublishRetainsOriginal() throws Exception {
        File destination = tmp.newFile("classes.dex");
        Files.write(destination.toPath(), new byte[]{7});
        DexPool cancelling = new DexPool(Opcodes.forDexVersion(35)) {
            @Override public void writeTo(DexDataStore output, DeferredOutputStreamFactory scratch) throws IOException {
                Thread.currentThread().interrupt();
                super.writeTo(output, scratch);
            }
        };
        try {
            assertThrows(IOException.class, () -> DexSaveWriter.write(cancelling, destination));
        } finally { Thread.interrupted(); }
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(destination.toPath()));
        assertEquals(1, tmp.getRoot().list().length);
    }
}
