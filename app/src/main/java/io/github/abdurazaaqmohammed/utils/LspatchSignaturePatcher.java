package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.util.Base64;

import com.android.apksig.ApkVerifier;
import com.reandroid.apk.ApkModule;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.chunk.xml.ResXmlNode;

import io.github.abdurazaaqmohammed.MPManager.R;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Standalone integration of JingMatrix/LSPatch v1.2's prebuilt GPL-3.0 runtime.
 * Packaging contract adapted from upstream ApkPatcher (tag v1.2); runtime bytes unchanged.
 * Upstream source/license/provenance are in assets/signature/lspatch.
 * AI-assisted integration: OpenAI Codex. No universal compatibility is implied. */
public final class LspatchSignaturePatcher {
    static final String PREFIX = "assets/lspatch/";
    static final String ORIGINAL = PREFIX + "origin.apk";
    static final String CONFIG = PREFIX + "config.json";
    static final String FACTORY = "org.lsposed.lspatch.metaloader.LSPAppComponentFactoryStub";
    private static final String MARKER = "assets/hexora/signature-patch.properties";
    private static final String ASSETS = "signature/lspatch/";
    private static final String[] ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"};
    private static final String[] RUNTIME = {"metaloader.dex", "loader.dex",
            "so/arm64-v8a/liblspatch.so", "so/armeabi-v7a/liblspatch.so",
            "so/x86/liblspatch.so", "so/x86_64/liblspatch.so"};
    private static final String[] HASHES = {
            "6af8e8c959a1837099ea10974f87bf7847648a3c9cef18a9362ff83a83aa0c54",
            "16f544f449bb75e3147886314a5c3f66b0ef7da53e1e07715efbcbd77bbb70ac",
            "2cff54544c166523fae5ae8f522510a4f6bcf189a3d3b02d14087e8d572ffb97",
            "b64081ebd118ccb2ff123a2d55477fac665a8bfa83f833e6007f1baa2c6e973c",
            "038f023cc83496a08c5a12cc2c009abd93f4456591b3fcf36c8cf8ab5329d651",
            "0ad971ee6ca57593911fc1a719f5898786edc8bcbdcaa1b91ccb2bb7c0551c18"};
    private static final long MAX_INPUT = 1024L * 1024 * 1024;
    private static final long MAX_EXPANDED = 2L * 1024 * 1024 * 1024;

    private LspatchSignaturePatcher() { }

    public static File patch(Context context, File source, String displayName, SignWrapper signer,
                             ProgressManager progress, int level) throws Exception {
        if (level != 2 && level != 3) throw new IllegalArgumentException("Unsupported LSPatch level");
        if (!source.isFile() || source.length() == 0 || source.length() > MAX_INPUT)
            throw new IOException(context.getString(R.string.sigkill_advanced_size));
        File work = new File(context.getCacheDir(), "signature-advanced-" + UUID.randomUUID());
        if (!work.mkdir()) throw new IOException("Cannot create advanced patch workspace.");
        try {
            checkSpace(context, work, source.length() * 3 + 16L * 1024 * 1024);
            File input = SignatureBypassPatcher.stageSource(source, work);
            if (input.length() > MAX_INPUT) throw new IOException(context.getString(R.string.sigkill_advanced_size));
            try (ZipFile original = new ZipFile(input)) {
                Set<String> nativeAbis = validateEntries(context, original);
                if (level == 3 && !nativeModeSupported(nativeAbis))
                    throw new IOException(context.getString(R.string.sigkill_native_arm64_required));
                progress.setText(context.getString(R.string.sigkill_reading_certificate));
                ApkVerifier.Result verified = new ApkVerifier.Builder(input).build().verify();
                if (!verified.isVerified() || verified.getSignerCertificates().isEmpty())
                    throw new IOException(context.getString(R.string.sigkill_original_required));
                if (verified.getSignerCertificates().size() != 1
                        || (verified.getSigningCertificateLineage() != null
                        && verified.getSigningCertificateLineage().getCertificatesInLineage().size() > 1))
                    throw new IOException(context.getString(R.string.sigkill_advanced_signers));

                String signature = hex(verified.getSignerCertificates().get(0).getEncoded());
                byte[] manifest;
                byte[] config;
                String packageName;
                try (ApkModule module = ApkModule.loadApkFile(input)) {
                    AndroidManifestBlock xml = module.getAndroidManifest();
                    if (xml.isSplit()) throw new IOException(context.getString(R.string.sigkill_standalone_required));
                    packageName = module.getPackageName();
                    if (packageName == null || !packageName.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))
                        throw new IOException("Invalid APK package name.");
                    String factory = originalFactory(xml, packageName);
                    config = config(signature, factory, level).toString().getBytes(StandardCharsets.UTF_8);
                    modifyManifest(xml, config);
                    manifest = xml.getBytes();
                }
                progress.setProgress(1, 4);
                File[] runtime = stageRuntime(context, work);
                long expanded = 0;
                Enumeration<? extends ZipEntry> entries = original.entries();
                while (entries.hasMoreElements()) expanded += Math.max(0, entries.nextElement().getSize());
                checkSpace(context, work, input.length() * 2 + expanded * 2 + 32L * 1024 * 1024);
                progress.setText(context.getString(R.string.sigkill_advanced_packing));
                File unsigned = new File(work, "unsigned.apk");
                repack(original, input, unsigned, manifest, config, runtime, packageName, level, context);
                String issue = ApkZipAlignUtil.installIssue(unsigned);
                if (issue != null) throw new IOException(issue);
                verifyNativeAlignment(unsigned);
                progress.setProgress(2, 4);
                progress.setText(context.getString(R.string.sigkill_signing));
                File signed = new File(work, "signed.apk");
                SignatureBypassPatcher.checkCancelled();
                signer.signApk(unsigned, signed, true, true, true, false);
                if (!new ApkVerifier.Builder(signed).build().verify().isVerified())
                    throw new IOException(context.getString(R.string.sigkill_invalid_output));
                issue = ApkZipAlignUtil.installIssue(signed);
                if (issue != null) throw new IOException(issue);
                verifyNativeAlignment(signed);
                verifyOriginal(signed, input);
                progress.setProgress(3, 4);
                File output = SignatureBypassPatcher.publish(context, source, displayName, signed,
                        level == 3 ? "_sigkill_native" : "_sigkill_advanced");
                progress.setProgress(4, 4);
                return output;
            }
        } finally {
            SignatureBypassPatcher.deleteWorkspace(work);
        }
    }

    static boolean nativeModeSupported(Set<String> nativeAbis) {
        // Restrict the output to apps that already require arm64; asset-only libraries do
        // not constrain Android's installer ABI choice for Java-only or multi-ABI APKs.
        return nativeAbis.equals(Collections.singleton("arm64-v8a"));
    }

    private static Set<String> validateEntries(Context context, ZipFile zip) throws IOException {
        Set<String> names = new HashSet<>();
        Set<String> abis = new HashSet<>();
        long expanded = 0;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            SignatureBypassPatcher.checkCancelled();
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!names.add(name)) throw new IOException("Duplicate ZIP entry: " + name);
            if (name.startsWith(PREFIX) || name.equals(MARKER))
                throw new IOException(context.getString(R.string.sigkill_already_patched));
            if (entry.getSize() < 0 || (expanded += entry.getSize()) > MAX_EXPANDED || names.size() > 65000)
                throw new IOException(context.getString(R.string.sigkill_advanced_size));
            if (name.startsWith("lib/") && name.endsWith(".so")) {
                String[] parts = name.split("/");
                if (parts.length == 3) abis.add(parts[1]);
            }
        }
        if (!names.contains("AndroidManifest.xml") || !names.contains("classes.dex"))
            throw new IOException(context.getString(R.string.sigkill_standalone_required));
        if (!abis.isEmpty() && Collections.disjoint(abis, Arrays.asList(ABIS)))
            throw new IOException("This APK has no native ABI supported by the advanced runtime.");
        return abis;
    }

    static JSONObject config(String signature, String originalFactory, int level) throws Exception {
        JSONObject config = new JSONObject();
        config.put("useManager", false);
        config.put("debuggable", false);
        config.put("sigBypassLevel", level);
        config.put("originalSignature", signature);
        // Omit absent factories: the upstream loader uses has(), so JSON null is incorrect.
        if (originalFactory != null) config.put("appComponentFactory", originalFactory);
        config.put("injectDex", false);
        config.put("injectDocumentsProvider", false);
        config.put("addedPermissions", new org.json.JSONArray());
        // Release metadata read from the pinned jar's LSPConfig.class without execution.
        JSONObject upstream = new JSONObject();
        upstream.put("API_CODE", 102);
        upstream.put("VERSION_CODE", 487);
        upstream.put("VERSION_NAME", "1.2");
        upstream.put("CORE_VERSION_CODE", 7306);
        upstream.put("CORE_VERSION_NAME", "canary-3106");
        upstream.put("CORE_VERSION_HASH", "e00c5c5038bbcc14e0b382ab893301d6993e6989");
        config.put("lspConfig", upstream);
        return config;
    }

    static String originalFactory(AndroidManifestBlock xml, String packageName) throws IOException {
        ResXmlElement application = xml.getApplicationElement();
        if (application == null) throw new IOException("Missing manifest application.");
        ResXmlAttribute factory = application.searchAttributeByResourceId(0x0101057a);
        if (factory == null) return null;
        String name = factory.getValueAsString();
        if (name == null || name.isEmpty()) throw new IOException("Invalid application component factory.");
        if (name.startsWith(".")) return packageName + name;
        return name.indexOf('.') < 0 ? packageName + "." + name : name;
    }

    static void modifyManifest(AndroidManifestBlock xml, byte[] config) throws IOException {
        ResXmlElement app = xml.getApplicationElement();
        if (app == null) throw new IOException("Missing manifest application.");
        app.getOrCreateAndroidAttribute("appComponentFactory", 0x0101057a).setValueAsString(FACTORY);
        app.getOrCreateAndroidAttribute("debuggable", 0x0101000f).setValueAsBoolean(false);
        Integer minimum = xml.getMinSdkVersion();
        if (minimum == null || minimum < 28) xml.setMinSdkVersion(28);
        Iterator<ResXmlNode> children = app.iterator();
        while (children.hasNext()) {
            ResXmlNode child = children.next();
            if (child instanceof ResXmlElement && ((ResXmlElement) child).equalsName("meta-data")) {
                ResXmlAttribute name = ((ResXmlElement) child).searchAttributeByResourceId(0x01010003);
                if (name != null && "lspatch".equals(name.getValueAsString()))
                    throw new IOException("Existing LSPatch metadata: start from the original APK.");
            }
        }
        ResXmlElement metadata = app.newElement("meta-data");
        metadata.getOrCreateAndroidAttribute("name", 0x01010003).setValueAsString("lspatch");
        metadata.getOrCreateAndroidAttribute("value", 0x01010024)
                .setValueAsString(Base64.encodeToString(config, Base64.NO_WRAP));
        xml.refresh();
    }

    private static File[] stageRuntime(Context context, File work) throws Exception {
        File[] files = new File[RUNTIME.length];
        for (int i = 0; i < RUNTIME.length; i++) {
            File file = new File(work, "runtime-" + i);
            try (InputStream input = context.getAssets().open(ASSETS + RUNTIME[i]);
                 FileOutputStream output = new FileOutputStream(file)) {
                SignatureBypassPatcher.copy(input, output);
            }
            try (InputStream input = new FileInputStream(file)) {
                if (!hex(digest(input)).equals(HASHES[i])) throw new IOException("LSPatch runtime checksum mismatch: " + RUNTIME[i]);
            }
            files[i] = file;
        }
        return files;
    }

    private static void repack(ZipFile source, File original, File output, byte[] manifest, byte[] config,
                               File[] runtime, String pkg, int level, Context context) throws IOException {
        try (SignaturePatchArchive zip = new SignaturePatchArchive(output)) {
            addBytes(zip, "AndroidManifest.xml", manifest, true, 4);
            zip.addFile("classes.dex", runtime[0], false, 0);
            addBytes(zip, CONFIG, config, false, 0);
            zip.addFile(PREFIX + "loader.dex", runtime[1], false, 0);
            for (int i = 2; i < RUNTIME.length; i++) zip.addFile(PREFIX + RUNTIME[i], runtime[i], true, 16384);
            // Store the complete signed original, including its signing block, byte for byte.
            // Unlike upstream's nested ZIP links, copying entries trades disk space for a
            // streaming implementation using the application's existing ZIP/signing stack.
            zip.addFile(ORIGINAL, original, true, 16384);
            Enumeration<? extends ZipEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.equals("AndroidManifest.xml") || name.matches("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")
                        || SignatureBypassPatcher.signatureEntry(name)) continue;
                boolean store = entry.getMethod() == ZipEntry.STORED || ApkZipAlignUtil.mustStore(name);
                int alignment = store && name.endsWith(".so") ? 16384 : store ? 4 : 0;
                try (InputStream input = source.getInputStream(entry)) {
                    zip.add(name, input, entry.getSize(), entry.getCrc(), store, alignment);
                }
            }
            addBytes(zip, MARKER, ("version=2\nimplementation=Hexora / OpenAI Codex\nengine=JingMatrix/LSPatch-v1.2\nlevel="
                    + level + "\npackage=" + pkg + "\n").getBytes(StandardCharsets.UTF_8), false, 0);
            for (String notice : new String[]{"UPSTREAM-LICENSE.txt", "BUNDLED-APACHE-LICENSE.txt",
                    "BUNDLED-META-INF-LICENSE.txt", "NOTICE.txt", "provenance.json"}) {
                try (InputStream input = context.getAssets().open(ASSETS + notice)) {
                    zip.add("assets/hexora/lspatch/" + notice, input, -1, 0, false, 0);
                }
            }
        }
    }

    private static void addBytes(SignaturePatchArchive zip, String name, byte[] bytes, boolean store, int alignment) throws IOException {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(bytes);
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            zip.add(name, input, bytes.length, crc.getValue(), store, alignment);
        }
    }

    static void verifyNativeAlignment(File apk) throws IOException {
        int runtimeLibraries = 0;
        boolean originalPresent = false;
        try (org.apache.commons.compress.archivers.zip.ZipFile zip =
                     new org.apache.commons.compress.archivers.zip.ZipFile(apk)) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                SignatureBypassPatcher.checkCancelled();
                ZipArchiveEntry entry = entries.nextElement();
                boolean runtime = entry.getName().startsWith(PREFIX + "so/") && entry.getName().endsWith("/liblspatch.so");
                if (entry.getName().equals(ORIGINAL)) {
                    originalPresent = true;
                    if (entry.getMethod() != ZipEntry.STORED || entry.getDataOffset() < 0 || entry.getDataOffset() % 16384 != 0)
                        throw new IOException("Embedded original APK is not stored and 16 KiB aligned.");
                }
                if (runtime) runtimeLibraries++;
                if (runtime || (entry.getName().endsWith(".so") && entry.getMethod() == ZipEntry.STORED)) {
                    if (entry.getMethod() != ZipEntry.STORED || entry.getDataOffset() < 0 || entry.getDataOffset() % 16384 != 0)
                        throw new IOException("Native library is not stored and 16 KiB aligned: " + entry.getName());
                }
            }
        }
        if (runtimeLibraries != ABIS.length) throw new IOException("Missing advanced runtime libraries.");
        if (!originalPresent) throw new IOException("Missing embedded original APK.");
    }

    private static void verifyOriginal(File apk, File original) throws Exception {
        try (ZipFile zip = new ZipFile(apk); InputStream expected = new FileInputStream(original)) {
            ZipEntry entry = zip.getEntry(ORIGINAL);
            if (entry == null || entry.getSize() != original.length()) throw new IOException("Missing embedded original APK.");
            try (InputStream actual = zip.getInputStream(entry)) {
                if (!MessageDigest.isEqual(digest(expected), digest(actual)))
                    throw new IOException("Embedded original APK was changed.");
            }
        }
    }

    private static byte[] digest(InputStream input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            SignatureBypassPatcher.checkCancelled();
            digest.update(buffer, 0, count);
        }
        return digest.digest();
    }

    private static String hex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = digits[(bytes[i] & 255) >>> 4];
            result[i * 2 + 1] = digits[bytes[i] & 15];
        }
        return new String(result);
    }

    private static void checkSpace(Context context, File work, long needed) throws IOException {
        long available = work.getUsableSpace();
        if (available > 0 && available < needed)
            throw new IOException(context.getString(R.string.sigkill_advanced_space));
    }
}
