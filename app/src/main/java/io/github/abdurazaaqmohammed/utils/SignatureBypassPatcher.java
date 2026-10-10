package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.os.Environment;
import com.android.apksig.ApkVerifier;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.reandroid.apk.ApkModule;
import io.github.abdurazaaqmohammed.MPManager.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Local, copy-only APK patching. No system hooks or changes to Android's verifier.
 * AI-assisted contribution: OpenAI Codex. */
public final class SignatureBypassPatcher {
    private static final String MARKER = "assets/hexora/signature-patch.properties";
    private static final long DEX_LIMIT = Math.min(32L * 1024 * 1024,
            Math.max(8L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16));

    public static final class Result {
        public final File file;
        public final int checks;
        private Result(File file, int checks) { this.file = file; this.checks = checks; }
    }

    private SignatureBypassPatcher() { }

    public static Result patch(Context context, File source, String displayName,
                               SignWrapper signer, ProgressManager progress) throws Exception {
        File work = new File(context.getCacheDir(), "signature-patch-" + UUID.randomUUID());
        if (!work.mkdir()) throw new IOException("Cannot create patch workspace.");
        try (ZipFile zip = new ZipFile(stageSource(source, work))) {
            File inputApk = new File(work, "input.apk");
            if (zip.getEntry(MARKER) != null) throw new IOException(context.getString(R.string.sigkill_already_patched));
            if (zip.getEntry("classes.dex") == null || zip.getEntry("AndroidManifest.xml") == null)
                throw new IOException(context.getString(R.string.sigkill_standalone_required));
            String packageName;
            String applicationClassName;
            try (ApkModule module = ApkModule.loadApkFile(inputApk)) {
                if (module.getAndroidManifest().isSplit())
                    throw new IOException(context.getString(R.string.sigkill_standalone_required));
                packageName = module.getPackageName();
                applicationClassName = module.getAndroidManifest().getApplicationClassName();
            }
            if (packageName == null || !packageName.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))
                throw new IOException("Invalid APK package name.");

            progress.setText(context.getString(R.string.sigkill_reading_certificate));
            ApkVerifier.Result original = new ApkVerifier.Builder(inputApk).build().verify();
            if (!original.isVerified() || original.getSignerCertificates().isEmpty())
                throw new IOException(context.getString(R.string.sigkill_original_required));
            List<X509Certificate> current = original.getSignerCertificates();
            List<X509Certificate> history = original.getSigningCertificateLineage() == null
                    ? current : original.getSigningCertificateLineage().getCertificatesInLineage();
            if (history.isEmpty()) history = current;
            if (current.size() > 16 || history.size() > 32) throw new IOException("Too many signing certificates.");

            List<String> entries = new ArrayList<>();
            Set<String> names = new HashSet<>();
            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                String name = all.nextElement().getName();
                if (!names.add(name)) throw new IOException("Duplicate ZIP entry: " + name);
                if (name.matches("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")) entries.add(name);
            }
            if (entries.size() > 128) throw new IOException("Too many DEX files.");
            Collections.sort(entries);
            SignatureCheckRewriter rewriter = new SignatureCheckRewriter();
            Map<String, File> replacements = new HashMap<>();
            for (int i = 0; i < entries.size(); i++) {
                checkCancelled();
                String entry = entries.get(i);
                progress.setText(context.getString(R.string.sigkill_patching_entry, entry));
                DexBackedDexFile dex = new DexBackedDexFile(null, readDex(zip, entry));
                DexPool pool = new DexPool(dex.getOpcodes());
                int before = rewriter.legacyReads + rewriter.modernReads + rewriter.modernCalls;
                for (ClassDef type : dex.getClasses()) {
                    checkCancelled();
                    if (SignaturePatchCompatibility.hasNativePackedBootstrap(type, applicationClassName))
                        throw new IOException(context.getString(R.string.sigkill_native_packed, applicationClassName));
                    if (type.getType().equals(SignatureCheckRewriter.BASE) || type.getType().equals(SignatureCheckRewriter.MODERN))
                        throw new IOException(context.getString(R.string.sigkill_already_patched));
                    pool.internClass(rewriter.rewrite(type));
                }
                if (before != rewriter.legacyReads + rewriter.modernReads + rewriter.modernCalls) {
                    File changed = new File(work, entry);
                    writePool(pool, changed);
                    replacements.put(entry, changed);
                }
                progress.setProgress(i + 1, entries.size() + 3);
            }
            if (rewriter.legacyReads == 0 && (rewriter.modernReads == 0 || rewriter.modernCalls == 0))
                throw new IOException(context.getString(R.string.sigkill_no_checks));

            File helperDir = new File(work, "helper");
            if (!helperDir.mkdir()) throw new IOException("Cannot create helper workspace.");
            writeHelper(context, helperDir, "OriginalCertificates", packageName, current, history);
            if (rewriter.modernReads > 0 || rewriter.modernCalls > 0)
                writeHelper(context, helperDir, "SigningInfoCompat", packageName, current, history);
            // Assemble only the small helper at runtime; original methods use the binary DEX model.
            File miniDex;
            synchronized (FastDexPatch.class) {
                checkCancelled();
                miniDex = FastDexPatch.assembleMiniDex(context, helperDir, 19, null);
            }
            byte[] primary = replacements.containsKey("classes.dex")
                    ? readFile(replacements.get("classes.dex")) : readDex(zip, "classes.dex");
            DexBackedDexFile dex = new DexBackedDexFile(null, primary);
            DexPool pool = new DexPool(dex.getOpcodes());
            for (ClassDef type : dex.getClasses()) pool.internClass(type);
            for (ClassDef type : DexFileFactory.loadDexFile(miniDex, Opcodes.getDefault()).getClasses()) pool.internClass(type);
            File primaryOutput = new File(work, "primary.dex");
            writePool(pool, primaryOutput);
            replacements.put("classes.dex", primaryOutput);

            progress.setText(context.getString(R.string.sigkill_saving));
            File unsigned = new File(work, "unsigned.apk");
            repack(zip, replacements, unsigned, packageName);
            ApkZipAlignUtil.ensureInstallable(unsigned);
            progress.setProgress(entries.size() + 1, entries.size() + 3);
            progress.setText(context.getString(R.string.sigkill_signing));
            File signed = new File(work, "signed.apk");
            // Use the selected key, with standalone V1/V2/V3 signatures and no V4 sidecar.
            signer.signApk(unsigned, signed, true, true, true, false);
            if (!new ApkVerifier.Builder(signed).build().verify().isVerified())
                throw new IOException(context.getString(R.string.sigkill_invalid_output));
            String alignmentIssue = ApkZipAlignUtil.installIssue(signed);
            if (alignmentIssue != null) throw new IOException(alignmentIssue);
            progress.setProgress(entries.size() + 2, entries.size() + 3);
            File output = publish(context, source, displayName, signed);
            progress.setProgress(entries.size() + 3, entries.size() + 3);
            return new Result(output, rewriter.legacyReads + (rewriter.modernReads > 0 ? rewriter.modernCalls : 0));
        } finally {
            deleteWorkspace(work);
        }
    }

    static File stageSource(File source, File work) throws IOException {
        File snapshot = new File(work, "input.apk");
        try (InputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(snapshot)) {
            copy(input, output);
        }
        return snapshot;
    }

    private static void writeHelper(Context context, File directory, String name, String pkg,
                                    List<X509Certificate> current, List<X509Certificate> history) throws Exception {
        String template;
        try (InputStream input = context.getAssets().open("signature/" + name + ".smali")) {
            template = new String(readBounded(input, 128 * 1024), StandardCharsets.UTF_8);
        }
        List<X509Certificate> legacy = current.size() == 1 && history.size() > 1
                ? Collections.singletonList(history.get(0)) : current;
        template = template.replace("@@PACKAGE@@", pkg)
                .replace("@@CURRENT@@", certificateArray(current))
                .replace("@@HISTORY@@", certificateArray(history))
                .replace("@@LEGACY@@", certificateArray(legacy))
                .replace("@@MULTIPLE@@", current.size() > 1 ? "0x1" : "0x0")
                .replace("@@PAST@@", current.size() == 1 && history.size() > 1 ? "0x1" : "0x0")
                .replace("@@HISTORY_RETURN@@", current.size() > 1 ? "const/4 v0, 0x0\n    return-object v0"
                        : "invoke-static {}, " + SignatureCheckRewriter.BASE + "->history()[Landroid/content/pm/Signature;\n    move-result-object v0\n    return-object v0");
        if (template.contains("@@")) throw new IOException("Unresolved helper template.");
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".smali"))) {
            output.write(template.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String certificateArray(List<X509Certificate> certificates) throws Exception {
        StringBuilder smali = new StringBuilder("const/16 v0, ").append(certificates.size())
                .append("\n    new-array v0, v0, [Landroid/content/pm/Signature;\n");
        int index = 0;
        for (X509Certificate certificate : certificates) {
            byte[] bytes = certificate.getEncoded();
            if (bytes.length > 16 * 1024) throw new IOException("Signing certificate is too large.");
            char[] hex = new char[bytes.length * 2];
            char[] digits = "0123456789abcdef".toCharArray();
            for (int i = 0; i < bytes.length; i++) { hex[i * 2] = digits[(bytes[i] & 255) >>> 4]; hex[i * 2 + 1] = digits[bytes[i] & 15]; }
            smali.append("    const-string v1, \"").append(hex).append("\"\n")
                    .append("    new-instance v2, Landroid/content/pm/Signature;\n")
                    .append("    invoke-direct {v2, v1}, Landroid/content/pm/Signature;-><init>(Ljava/lang/String;)V\n")
                    .append("    const/16 v3, ").append(index++).append("\n")
                    .append("    aput-object v2, v0, v3\n");
        }
        return smali.append("    return-object v0").toString();
    }

    private static byte[] readDex(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.getSize() > DEX_LIMIT) throw new IOException("DEX exceeds the patcher's memory limit: " + name);
        try (InputStream input = zip.getInputStream(entry)) { return readBounded(input, DEX_LIMIT); }
    }

    private static byte[] readFile(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) { return readBounded(input, DEX_LIMIT * 2); }
    }

    private static byte[] readBounded(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            checkCancelled();
            if ((long) output.size() + count > limit) throw new IOException("Patch input exceeds the memory limit.");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void writePool(DexPool pool, File output) throws IOException {
        MemoryDataStore data = new MemoryDataStore();
        try (FileOutputStream stream = new FileOutputStream(output)) {
            pool.writeTo(data);
            stream.write(data.getBuffer(), 0, data.getSize());
        } finally {
            data.close();
        }
    }

    static boolean signatureEntry(String name) {
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        return upper.startsWith("META-INF/") && (upper.equals("META-INF/MANIFEST.MF")
                || upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC"));
    }

    static void repack(ZipFile source, Map<String, File> replacements, File output, String pkg) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
            Enumeration<? extends ZipEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                checkCancelled();
                ZipEntry original = entries.nextElement();
                if (signatureEntry(original.getName())) continue;
                ZipEntry entry = new ZipEntry(original.getName());
                if (original.getTime() >= 0) entry.setTime(original.getTime());
                File replacement = replacements.get(entry.getName());
                // Keep native libraries/resources uncompressed when their originals were stored.
                if (replacement == null && original.getMethod() == ZipEntry.STORED) {
                    entry.setMethod(ZipEntry.STORED); entry.setSize(original.getSize()); entry.setCrc(original.getCrc());
                }
                zip.putNextEntry(entry);
                try (InputStream input = replacement == null ? source.getInputStream(original) : new FileInputStream(replacement)) {
                    copy(input, zip);
                }
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(MARKER));
            zip.write(("version=1\nimplementation=Hexora / OpenAI Codex\npackage=" + pkg + "\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static File publish(Context context, File source, String name, File signed) throws IOException {
        return publish(context, source, name, signed, "_sigkill");
    }

    static File publish(Context context, File source, String name, File signed, String suffix) throws IOException {
        File directory = source.getAbsoluteFile().getParentFile();
        String parent = directory.getCanonicalPath();
        String cache = context.getCacheDir().getCanonicalPath();
        if (!directory.canWrite() || parent.equals(cache) || parent.startsWith(cache + File.separator))
            directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Hexora");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create output folder: " + directory);
        String base = new File(name).getName();
        if (base.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) base = base.substring(0, base.length() - 4);
        if (base.isEmpty()) base = "app";
        File output;
        int index = 0;
        do { output = new File(directory, base + suffix + (index == 0 ? "" : "_" + index) + ".apk"); index++; }
        while (!output.createNewFile());
        File staging = null;
        boolean published = false;
        try {
            staging = File.createTempFile(".hexora-signature-", ".pending", directory);
            try (InputStream input = new FileInputStream(signed); FileOutputStream stream = new FileOutputStream(staging)) {
                copy(input, stream); stream.getFD().sync();
            }
            checkCancelled();
            if (!staging.renameTo(output)) throw new IOException("Cannot publish patched APK.");
            published = true;
            return output;
        } finally {
            if (!published) output.delete();
            if (staging != null && staging.exists()) staging.delete();
        }
    }

    static void copy(InputStream input, java.io.OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024]; int count;
        while ((count = input.read(buffer)) != -1) { checkCancelled(); output.write(buffer, 0, count); }
    }

    static void checkCancelled() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Signature patch cancelled.");
    }

    static void deleteWorkspace(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteWorkspace(child);
        file.delete();
    }
}
