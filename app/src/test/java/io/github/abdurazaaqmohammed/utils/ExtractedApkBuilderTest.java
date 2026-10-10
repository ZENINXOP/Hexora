package io.github.abdurazaaqmohammed.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

public class ExtractedApkBuilderTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private void put(File root, String path, byte[] bytes) throws Exception {
        File file = new File(root, path); file.getParentFile().mkdirs(); Files.write(file.toPath(), bytes);
    }
    @Test public void preservesFlatDexResourcesAssetsAndAlignment() throws Exception {
        File source = tmp.newFolder("project");
        put(source, "AndroidManifest.xml", new byte[]{3,0,8,0});
        put(source, "resources.arsc", new byte[]{2,0,12,0});
        put(source, "classes.dex", new byte[]{100,101,120,10});
        put(source, "classes2.dex", new byte[]{100,101,120,11});
        put(source, "assets/nested/data.bin", new byte[]{1,2,3,4,5});
        put(source, "META-INF/old.RSA", new byte[]{1});
        new File(source, "assets/empty").mkdirs();
        File output = new File(source, "project.apk");
        ExtractedApkBuilder.build(source, output);
        try (ZipFile zip = new ZipFile(output)) {
            assertNotNull(zip.getEntry("classes.dex"));
            assertNotNull(zip.getEntry("classes2.dex"));
            assertNotNull(zip.getEntry("assets/empty/"));
            assertNull(zip.getEntry("project.apk"));
            assertNull(zip.getEntry("META-INF/old.RSA"));
            assertArrayEquals(new byte[]{1,2,3,4,5},zip.getInputStream(zip.getEntry("assets/nested/data.bin")).readAllBytes());
        }
        assertNull(ApkZipAlignUtil.installIssue(output));
        assertEquals(7, Files.walk(source.toPath()).filter(Files::isRegularFile).count());
    }
    @Test public void refusesToOverwriteExistingOutput() throws Exception {
        File source = tmp.newFolder("project");
        put(source,"AndroidManifest.xml",new byte[]{1});
        File output = tmp.newFile("existing.apk"); Files.write(output.toPath(),new byte[]{42});
        assertThrows(IOException.class, () -> ExtractedApkBuilder.build(source,output));
        assertArrayEquals(new byte[]{42},Files.readAllBytes(output.toPath()));
    }
    @Test public void cancelledBuildDoesNotPublishOrLeaveTemporaryFiles() throws Exception {
        File source = tmp.newFolder("project");
        put(source,"AndroidManifest.xml",new byte[]{1});
        File output = new File(tmp.getRoot(),"cancelled.apk");
        Thread.currentThread().interrupt();
        try { assertThrows(IOException.class, () -> ExtractedApkBuilder.build(source,output)); }
        finally { Thread.interrupted(); }
        assertFalse(output.exists());
        assertEquals(1,tmp.getRoot().list().length);
    }
    @Test public void rebuildingDoesNotEmbedPreviousGeneratedApks() throws Exception {
        File source = tmp.newFolder("project");
        put(source,"AndroidManifest.xml",new byte[]{3,0,8,0});
        File first = new File(source,"project.apk");
        ExtractedApkBuilder.build(source,first);
        File second = new File(source,"project_1.apk");
        ExtractedApkBuilder.build(source,second);
        try (ZipFile zip = new ZipFile(second)) {
            assertNull(zip.getEntry("project.apk"));
            assertNull(zip.getEntry("project_1.apk"));
            assertNotNull(zip.getEntry("AndroidManifest.xml"));
        }
    }
}
