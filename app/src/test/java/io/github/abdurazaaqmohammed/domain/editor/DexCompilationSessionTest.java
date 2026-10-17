package io.github.abdurazaaqmohammed.domain.editor;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.util.DexUtil;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.smali.SmaliOptions;
import com.android.tools.smali.smali2.Smali;
import modder.hub.dexeditor.utils.ClassTree;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class DexCompilationSessionTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private static final ClassTree.DexSaveProgress PROGRESS = new ClassTree.DexSaveProgress() {
        public void onProgress(int progress, int total) { }
        public void onTitle(String title) { }
        public void onMessage(String message) { }
    };
    private static String source(String name) {
        return ".class public Lqa/" + name + ";\n.super Ljava/lang/Object;\n";
    }
    private File dex(File directory, String name, String type, int version) throws Exception {
        File file = new File(directory, name);
        DexPool pool = new DexPool(Opcodes.forDexVersion(version));
        pool.internClass(Smali.assemble(source(type), new SmaliOptions(), version));
        pool.writeTo(new FileDataStore(file));
        return file;
    }

    @Test public void savesEachSelectedDexInItsOwnDirectoryAndFormat() throws Exception {
        File first = dex(tmp.newFolder(), "classes.dex", "First", 35);
        File second = dex(tmp.newFolder(), "classes2.dex", "Second", 39);
        ClassTree tree = new ClassTree(Arrays.asList(first.getPath(), second.getPath()), tmp.newFolder().getPath());
        tree.saveSmali("qa/First", source("First") + ".field public value:I\n");
        tree.saveSmali("qa/Second", source("Second") + ".field public value:I\n");
        tree.saveAllDexFiles(PROGRESS);
        assertEquals(35, DexUtil.verifyDexHeader(Files.readAllBytes(first.toPath()), 0));
        assertEquals(39, DexUtil.verifyDexHeader(Files.readAllBytes(second.toPath()), 0));
        assertTrue(new File(second.getPath() + ".bak").isFile());
        assertFalse(new File(first.getParentFile(), "classes2.dex").exists());
        assertTrue(tree.getPendingSmaliMap().isEmpty());
    }

    @Test public void invalidEditKeepsOriginalAndPendingTextForCorrection() throws Exception {
        File file = dex(tmp.newFolder(), "classes.dex", "First", 35);
        byte[] original = Files.readAllBytes(file.toPath());
        ClassTree tree = new ClassTree(Collections.singletonList(file.getPath()), tmp.newFolder().getPath());
        tree.saveSmali("qa/First", ".class broken\n");
        Exception error = assertThrows(Exception.class, () -> tree.saveAllDexFiles(PROGRESS));
        assertTrue(error.getMessage(), error.getMessage().startsWith("COMPILE_ERROR:qa/First:"));
        assertEquals(".class broken\n", tree.getPendingSmaliMap().get("qa/First"));
        assertArrayEquals(original, Files.readAllBytes(file.toPath()));
        assertEquals(1, file.getParentFile().list().length);
    }

    @Test public void cancellationBeforeDiskWriteDoesNotConsumePendingEdits() throws Exception {
        File file = dex(tmp.newFolder(), "classes.dex", "First", 35);
        byte[] original = Files.readAllBytes(file.toPath());
        ClassTree tree = new ClassTree(Collections.singletonList(file.getPath()), tmp.newFolder().getPath());
        String pending = source("First") + ".field public value:I\n";
        tree.saveSmali("qa/First", pending);
        try {
            assertThrows(java.io.IOException.class, () -> tree.saveAllDexFiles(new ClassTree.DexSaveProgress() {
                public void onProgress(int progress, int total) { }
                public void onTitle(String title) { }
                public void onMessage(String message) {
                    if (message.equals("Writing file...")) Thread.currentThread().interrupt();
                }
            }));
        } finally { Thread.interrupted(); }
        assertEquals(pending, tree.getPendingSmaliMap().get("qa/First"));
        assertArrayEquals(original, Files.readAllBytes(file.toPath()));
        assertEquals(1, file.getParentFile().list().length);
    }
}
