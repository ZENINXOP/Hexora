package io.github.abdurazaaqmohammed.utils;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.util.DexUtil;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.smali.SmaliOptions;
import com.android.tools.smali.smali2.Smali;
import com.reandroid.apk.ApkModuleRawEncoder;
import com.reandroid.apkeditor.compile.BuildOptions;
import com.reandroid.apkeditor.smali.SmaliCompiler;
import com.reandroid.archive.InputSource;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

/** Regressions for APK project dispatch and the selectable Smali folder compiler. */
public class ApkBuildRegressionTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private static final String SOURCE = ".class public Lqa/Build;\n.super Ljava/lang/Object;\n"
            + ".method public static value()I\n.registers 1\nconst/4 v0, 0x1\nreturn v0\n.end method\n";

    private File put(File dir, String path, byte[] bytes) throws Exception {
        File file = new File(dir, path);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), bytes);
        return file;
    }

    @Test public void extractedResourceOnlyApkUsesFlatBuilderEvenWithResDirectory() throws Exception {
        File dir = tmp.newFolder();
        put(dir, "AndroidManifest.xml", new byte[]{3, 0, 8, 0});
        put(dir, "res/layout/a.xml", new byte[]{3, 0, 8, 0});
        assertEquals(ApkProjectLayout.FLAT, ApkProjectLayout.detect(dir));
    }

    @Test public void recognizesAllDecoderManifestFormats() throws Exception {
        File raw = tmp.newFolder();
        put(raw, "AndroidManifest.xml.bin", new byte[]{3, 0, 8, 0});
        assertEquals(ApkProjectLayout.RAW, ApkProjectLayout.detect(raw));
        File json = tmp.newFolder();
        put(json, "AndroidManifest.xml.json", "{}".getBytes());
        new File(json, "resources").mkdir();
        assertEquals(ApkProjectLayout.JSON, ApkProjectLayout.detect(json));
        File xml = tmp.newFolder();
        put(xml, "AndroidManifest.xml", "<manifest/>".getBytes());
        // All decoder formats may contain this file; it cannot identify JSON.
        put(xml, "uncompressed-files.json", "[]".getBytes());
        assertEquals(ApkProjectLayout.XML, ApkProjectLayout.detect(xml));
        assertNull(ApkProjectLayout.detect(tmp.newFolder()));
    }

    @Test public void binaryManifestWithDecoderPayloadUsesRawEncoder() throws Exception {
        File dir = tmp.newFolder();
        put(dir, "AndroidManifest.xml", new byte[]{3, 0, 8, 0});
        new File(dir, "root").mkdir();
        assertEquals(ApkProjectLayout.RAW, ApkProjectLayout.detect(dir));
    }

    @Test public void folderCompilerProducesRealDexWithBothLibraries() throws Exception {
        for (String library : new String[]{BuildOptions.DEX_LIB_JF, BuildOptions.DEX_LIB_INTERNAL}) {
            File dir = tmp.newFolder();
            put(dir, "smali/classes/qa/Build.smali", SOURCE.getBytes());
            BuildOptions options = new BuildOptions();
            options.dexLib = library;
            options.noCache = true;
            List<InputSource> built = new SmaliCompiler(options).buildDexFiles(new ApkModuleRawEncoder(), dir);
            assertEquals(1, built.size());
            File dex = new File(dir, ".cache/classes.dex");
            byte[] bytes = Files.readAllBytes(dex.toPath());
            int version = DexUtil.verifyDexHeader(bytes, 0);
            assertEquals("Lqa/Build;", new DexBackedDexFile(Opcodes.forDexVersion(version), bytes)
                    .getClasses().iterator().next().getType());
            assertEquals(0, dir.listFiles(file -> file.getName().startsWith(".hexora-")).length);
        }
    }

    @Test public void failedSmaliCompileKeepsCachedDexAndStamp() throws Exception {
        File dir = tmp.newFolder();
        File smali = put(dir, "smali/classes/qa/Build.smali", SOURCE.getBytes());
        BuildOptions options = new BuildOptions();
        options.dexLib = BuildOptions.DEX_LIB_JF;
        options.noCache = true;
        SmaliCompiler compiler = new SmaliCompiler(options);
        compiler.buildDexFiles(new ApkModuleRawEncoder(), dir);
        File dex = new File(dir, ".cache/classes.dex");
        byte[] original = Files.readAllBytes(dex.toPath());
        File stamp = new File(dir, ".cache/classes.dex.stamp");
        long timestamp = stamp.lastModified();
        Files.write(smali.toPath(), ".class broken\n".getBytes());
        assertThrows(java.io.IOException.class, () -> compiler.buildDexFiles(new ApkModuleRawEncoder(), dir));
        assertArrayEquals(original, Files.readAllBytes(dex.toPath()));
        assertEquals(timestamp, stamp.lastModified());
        assertEquals(2, dex.getParentFile().list().length);
    }

    @Test public void rebuildPreservesCachedDexFormatInsteadOfUsingMinSdk() throws Exception {
        for (String library : new String[]{BuildOptions.DEX_LIB_JF, BuildOptions.DEX_LIB_INTERNAL}) {
            File dir = tmp.newFolder();
            put(dir, "smali/classes/qa/Build.smali", SOURCE.getBytes());
            File cache = new File(dir, ".cache/classes.dex");
            cache.getParentFile().mkdirs();
            DexPool pool = new DexPool(Opcodes.forDexVersion(39));
            pool.internClass(Smali.assemble(SOURCE, new SmaliOptions(), 39));
            pool.writeTo(new FileDataStore(cache));
            BuildOptions options = new BuildOptions();
            options.dexLib = library;
            options.noCache = true;
            new SmaliCompiler(options).buildDexFiles(new ApkModuleRawEncoder(), dir);
            assertEquals(39, DexUtil.verifyDexHeader(Files.readAllBytes(cache.toPath()), 0));
        }
    }

    @Test public void dexVersionIsNotTreatedAsAndroidApiLevel() throws Exception {
        String source = ".class public Lqa/Modern;\n.super Ljava/lang/Object;\n"
                + ".method public static type()V\n.registers 1\nconst-method-type v0, ()V\nreturn-void\n.end method\n";
        assertNotNull(Smali.assemble(source, new SmaliOptions(), 39));
        assertThrows(Exception.class, () -> Smali.assemble(source, new SmaliOptions(), 35));
    }
}
