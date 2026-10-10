package io.github.abdurazaaqmohammed.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds a directly extracted APK, preserving top-level DEX and binary resources. */
public final class ExtractedApkBuilder {
    private ExtractedApkBuilder() { }

    public static void build(File directory, File output) throws IOException {
        if (!new File(directory, "AndroidManifest.xml").isFile()) throw new IOException("Missing AndroidManifest.xml");
        if (output.exists()) throw new IOException("Output already exists: " + output);
        File temporary = File.createTempFile(".hexora-build-", ".tmp", output.getAbsoluteFile().getParentFile());
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(temporary))) {
                add(directory, directory, temporary, output, zip, new HashSet<>());
            }
            ApkZipAlignUtil.ensureInstallable(temporary);
            if (output.exists() || !temporary.renameTo(output)) throw new IOException("Cannot publish APK: " + output);
        } finally { if (temporary.exists()) temporary.delete(); }
    }

    private static void add(File root, File file, File temporary, File output, ZipOutputStream zip,
                            Set<String> ancestors) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Build cancelled.");
        if (file.equals(temporary) || file.equals(output)) return;
        if (root.equals(file.getParentFile()) && file.getName().matches(java.util.regex.Pattern.quote(root.getName())
                + "(?:_\\d+)?(?:_signed(?:_\\d+)?)?\\.apk")) return;
        String canonical = file.getCanonicalPath();
        if (!canonical.equals(root.getCanonicalPath()) && !canonical.startsWith(root.getCanonicalPath() + File.separator)) {
            throw new IOException("Linked file is outside the project: " + file);
        }
        String path = root.toURI().relativize(file.toURI()).getPath();
        if (file.isDirectory()) {
            if (!ancestors.add(canonical)) throw new IOException("Directory cycle: " + file);
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot read directory: " + file);
            if (!path.isEmpty() && children.length == 0) {
                zip.putNextEntry(new ZipEntry(path.endsWith("/") ? path : path + "/"));
                zip.closeEntry();
            }
            for (File child : children) add(root, child, temporary, output, zip, ancestors);
            ancestors.remove(canonical);
        } else {
            String upper = path.toUpperCase(Locale.ROOT);
            // Existing signatures become invalid after editing. The caller may sign the output.
            if (upper.startsWith("META-INF/") && (upper.endsWith(".SF") || upper.endsWith(".RSA")
                    || upper.endsWith(".DSA") || upper.endsWith(".EC") || upper.equals("META-INF/MANIFEST.MF"))) return;
            ZipEntry entry = new ZipEntry(path);
            entry.setTime(file.lastModified());
            zip.putNextEntry(entry);
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Build cancelled.");
                    zip.write(buffer, 0, count);
                }
            }
            zip.closeEntry();
        }
    }
}
