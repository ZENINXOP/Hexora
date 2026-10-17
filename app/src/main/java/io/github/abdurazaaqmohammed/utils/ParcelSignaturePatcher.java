package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import com.android.apksig.ApkVerifier;
import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.reandroid.apk.ApkModule;
import io.github.abdurazaaqmohammed.MPManager.R;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Independent PackageInfo creator runtime; does not use an LSPatch loader or native hooks.
 * AI-assisted contribution: OpenAI Codex. See assets/signature/parcel/NOTICE.txt. */
public final class ParcelSignaturePatcher {
    private static final String RUNTIME = "Lio/github/abdurazaaqmohammed/signature/runtime/";
    private static final String BYPASS = "Lorg/lsposed/hiddenapibypass/";
    private static final String BOOTSTRAP = RUNTIME + "BootstrapApplication;";
    private static final String MARKER = "assets/hexora/signature-patch.properties";
    private static final long DEX_LIMIT = Math.min(32L * 1024 * 1024,
            Math.max(8L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16));

    private ParcelSignaturePatcher() { }

    public static File patch(Context context, File source, String displayName,
                             SignWrapper signer, ProgressManager progress) throws Exception {
        File work = new File(context.getCacheDir(), "signature-parcel-" + UUID.randomUUID());
        if (!work.mkdir()) throw new IOException("Cannot create certificate runtime workspace.");
        try {
            File input = SignatureBypassPatcher.stageSource(source, work);
            try (ZipFile zip = new ZipFile(input)) {
                List<String> dexEntries = validate(context, zip);
                ApkVerifier.Result verified = new ApkVerifier.Builder(input).build().verify();
                if (!verified.isVerified() || verified.getSignerCertificates().isEmpty())
                    throw new IOException(context.getString(R.string.sigkill_original_required));
                if (verified.getSignerCertificates().size() != 1
                        || (verified.getSigningCertificateLineage() != null
                        && verified.getSigningCertificateLineage().getCertificatesInLineage().size() > 1))
                    throw new IOException(context.getString(R.string.sigkill_advanced_signers));
                String certificate = hex(verified.getSignerCertificates().get(0).getEncoded());
                String pkg, application;
                byte[] manifest;
                try (ApkModule module = ApkModule.loadApkFile(input)) {
                    if (module.getAndroidManifest().isSplit())
                        throw new IOException(context.getString(R.string.sigkill_standalone_required));
                    pkg = module.getPackageName();
                    if (pkg == null || !pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))
                        throw new IOException("Invalid APK package name.");
                    application = applicationName(pkg, module.getAndroidManifest().getApplicationClassName());
                    module.getAndroidManifest().setApplicationClassName(
                            BOOTSTRAP.substring(1, BOOTSTRAP.length() - 1).replace('/', '.'));
                    Integer minimum = module.getAndroidManifest().getMinSdkVersion();
                    module.getAndroidManifest().setMinSdkVersion(minimum == null ? 28 : Math.max(28, minimum));
                    module.getAndroidManifest().refresh();
                    manifest = module.getAndroidManifest().getBytes();
                }
                checkApplication(context, zip, dexEntries, application);
                progress.setProgress(1, 4);
                progress.setText(context.getString(R.string.sigkill_parcel_packing));
                Map<String, ClassDef> runtime = runtimeClasses(context);
                File helper = new File(work, "bootstrap");
                if (!helper.mkdir()) throw new IOException("Cannot create bootstrap workspace.");
                String superclass = "L" + application.replace('.', '/') + ";";
                String smali = ".class public " + BOOTSTRAP + "\n.super " + superclass
                        + "\n.method static constructor <clinit>()V\n .registers 2\n"
                        + " const-string v0, \"" + pkg + "\"\n const-string v1, \"" + certificate + "\"\n"
                        + " invoke-static {v0, v1}, " + RUNTIME + "ParcelCertificates;->install(Ljava/lang/String;Ljava/lang/String;)V\n"
                        + " return-void\n.end method\n.method public constructor <init>()V\n .registers 1\n"
                        + " invoke-direct {p0}, " + superclass + "-><init>()V\n return-void\n.end method\n";
                try (FileOutputStream stream = new FileOutputStream(new File(helper, "BootstrapApplication.smali"))) {
                    stream.write(smali.getBytes(StandardCharsets.UTF_8));
                }
                File bootstrap;
                synchronized (FastDexPatch.class) {
                    SignatureBypassPatcher.checkCancelled();
                    bootstrap = FastDexPatch.assembleMiniDex(context, helper, 28, null);
                }
                // API 28 has native multidex support. Keep all original DEX bytes intact
                // and avoid overflowing an application's already-full primary method table.
                DexPool pool = new DexPool(Opcodes.forApi(28));
                for (ClassDef type : runtime.values()) pool.internClass(type);
                for (ClassDef type : DexFileFactory.loadDexFile(bootstrap, Opcodes.forApi(28)).getClasses()) pool.internClass(type);
                File dex = new File(work, "runtime.dex");
                MemoryDataStore data = new MemoryDataStore();
                try (FileOutputStream stream = new FileOutputStream(dex)) {
                    pool.writeTo(data);
                    stream.write(data.getBuffer(), 0, data.getSize());
                } finally { data.close(); }

                File unsigned = new File(work, "unsigned.apk");
                repack(context, zip, unsigned, manifest, dex, "classes" + (dexEntries.size() + 1) + ".dex", pkg);
                ApkZipAlignUtil.ensureInstallable(unsigned);
                progress.setProgress(2, 4);
                progress.setText(context.getString(R.string.sigkill_signing));
                File signed = new File(work, "signed.apk");
                SignatureBypassPatcher.checkCancelled();
                signer.signApk(unsigned, signed, true, true, true, false);
                if (!new ApkVerifier.Builder(signed).build().verify().isVerified())
                    throw new IOException(context.getString(R.string.sigkill_invalid_output));
                String alignmentIssue = ApkZipAlignUtil.installIssue(signed);
                if (alignmentIssue != null) throw new IOException(alignmentIssue);
                progress.setProgress(3, 4);
                File output = SignatureBypassPatcher.publish(context, source, displayName, signed, "_sigkill_parcel");
                progress.setProgress(4, 4);
                return output;
            }
        } finally { SignatureBypassPatcher.deleteWorkspace(work); }
    }

    static String applicationName(String pkg, String name) throws IOException {
        if (name == null || name.isEmpty()) return "android.app.Application";
        String result = name.startsWith(".") ? pkg + name : name.contains(".") ? name : pkg + "." + name;
        if (!result.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
            throw new IOException("Unsupported Application class name.");
        return result;
    }

    private static List<String> validate(Context context, ZipFile zip) throws IOException {
        List<String> dex = new ArrayList<>();
        Set<String> names = new HashSet<>();
        long expanded = 0;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            SignatureBypassPatcher.checkCancelled();
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!names.add(name)) throw new IOException("Duplicate ZIP entry: " + name);
            if (name.equals(MARKER) || name.startsWith("assets/lspatch/"))
                throw new IOException(context.getString(R.string.sigkill_already_patched));
            if (entry.getSize() < 0 || (expanded += entry.getSize()) > 2L * 1024 * 1024 * 1024 || names.size() > 65000)
                throw new IOException(context.getString(R.string.sigkill_advanced_size));
            if (name.matches("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")) dex.add(name);
        }
        if (!names.contains("AndroidManifest.xml") || !names.contains("classes.dex") || dex.size() > 128)
            throw new IOException(context.getString(R.string.sigkill_standalone_required));
        for (int i = 2; i <= dex.size(); i++)
            if (!names.contains("classes" + i + ".dex")) throw new IOException("The APK has a gap in its DEX sequence.");
        return dex;
    }

    private static void checkApplication(Context context, ZipFile zip, List<String> entries, String application) throws IOException {
        boolean found = application.equals("android.app.Application");
        String descriptor = "L" + application.replace('.', '/') + ";";
        for (String entry : entries) for (ClassDef type : readDex(zip, entry).getClasses()) {
            SignatureBypassPatcher.checkCancelled();
            if (type.getType().startsWith(RUNTIME) || type.getType().startsWith(BYPASS))
                throw new IOException("The APK already defines a certificate runtime. Start from the original APK.");
            if (!type.getType().equals(descriptor)) continue;
            found = true;
            if (SignaturePatchCompatibility.hasNativePackedBootstrap(type, application))
                throw new IOException(context.getString(R.string.sigkill_parcel_packed));
            if (AccessFlags.FINAL.isSet(type.getAccessFlags()) || !AccessFlags.PUBLIC.isSet(type.getAccessFlags()))
                throw new IOException("This Application class cannot be extended. Use Java checks instead.");
            boolean constructor = false;
            for (Method method : type.getDirectMethods())
                if (method.getName().equals("<init>") && method.getParameterTypes().isEmpty()
                        && (AccessFlags.PUBLIC.isSet(method.getAccessFlags()) || AccessFlags.PROTECTED.isSet(method.getAccessFlags())))
                    constructor = true;
            if (!constructor) throw new IOException("The Application needs an accessible empty constructor. Use Java checks instead.");
        }
        if (!found) throw new IOException("The original Application is missing from the standalone APK.");
    }

    private static Map<String, ClassDef> runtimeClasses(Context context) throws IOException {
        Map<String, ClassDef> classes = new LinkedHashMap<>();
        List<String> hosts = new ArrayList<>();
        hosts.add(context.getApplicationInfo().sourceDir);
        if (context.getApplicationInfo().splitSourceDirs != null)
            Collections.addAll(hosts, context.getApplicationInfo().splitSourceDirs);
        for (String host : hosts) try (ZipFile zip = new ZipFile(host)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.matches("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")) continue;
                for (ClassDef type : readDex(zip, name).getClasses())
                    if (type.getType().startsWith(RUNTIME) || type.getType().startsWith(BYPASS)) {
                        ClassDef portable = ParcelRuntimeDex.portable(type);
                        classes.put(portable.getType(), portable);
                    }
            }
        }
        if (!classes.containsKey(RUNTIME + "ParcelCertificates;") || !classes.containsKey(RUNTIME + "ParcelCertificates$Creator;")
                || !classes.containsKey(BYPASS + "HiddenApiBypass;"))
            throw new IOException("The certificate runtime is missing from this Hexora installation.");
        return classes;
    }

    private static DexBackedDexFile readDex(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.getSize() > DEX_LIMIT) throw new IOException("DEX exceeds the patcher memory limit: " + name);
        try (InputStream stream = zip.getInputStream(entry)) { return new DexBackedDexFile(null, bounded(stream, DEX_LIMIT)); }
    }

    private static byte[] bounded(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            SignatureBypassPatcher.checkCancelled();
            if ((long) result.size() + count > limit) throw new IOException("Runtime input exceeds its memory limit.");
            result.write(buffer, 0, count);
        }
        return result.toByteArray();
    }

    private static String hex(byte[] bytes) throws IOException {
        if (bytes.length > 16 * 1024) throw new IOException("Signing certificate is too large.");
        char[] hex = new char[bytes.length * 2], digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) { hex[i * 2] = digits[(bytes[i] & 255) >>> 4]; hex[i * 2 + 1] = digits[bytes[i] & 15]; }
        return new String(hex);
    }

    private static void repack(Context context, ZipFile source, File output, byte[] manifest, File dex, String dexName, String pkg) throws IOException {
        try (SignaturePatchArchive zip = new SignaturePatchArchive(output)) {
            Enumeration<? extends ZipEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (SignatureBypassPatcher.signatureEntry(name)) continue;
                if (name.equals("AndroidManifest.xml"))
                    zip.add(name, new ByteArrayInputStream(manifest), manifest.length, 0, false, 1);
                else try (InputStream input = source.getInputStream(entry)) {
                    boolean stored = entry.getMethod() == ZipEntry.STORED;
                    zip.add(name, input, entry.getSize(), entry.getCrc(), stored,
                            stored ? name.startsWith("lib/") && name.endsWith(".so") ? 16384 : 4 : 1);
                }
            }
            zip.addFile(dexName, dex, false, 1);
            byte[] marker = ("version=2\nengine=parcel\nimplementation=Hexora / OpenAI Codex\npackage=" + pkg + "\n").getBytes(StandardCharsets.UTF_8);
            zip.add(MARKER, new ByteArrayInputStream(marker), marker.length, 0, false, 1);
            for (String name : new String[]{"NOTICE.txt", "HiddenApiBypass-LICENSE.txt"})
                try (InputStream input = context.getAssets().open("signature/parcel/" + name)) {
                    zip.add("assets/hexora/parcel/" + name, input, -1, 0, false, 1);
                }
        }
    }
}
