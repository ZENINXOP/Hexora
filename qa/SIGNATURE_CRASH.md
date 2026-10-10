# Xian Esp startup crash investigation — 2026-10-10

AI-assisted contribution: OpenAI Codex. Diagnosis used read-only access to the connected phone and local APK inspection. No build, compilation, packaging, installation or app launch was performed.

## Observed failure

The crash buffer records three failures for `com.xianesp` version 2.1.0 at 12:27:35–12:27:38. Android cannot instantiate `com.xianesp.PackedApp`. Its `attachBaseContext` calls `boot`, which fails in native `xdec` with `IllegalStateException: XDEC:8`, wrapped as `packer boot failed [R7]`. The meaning of native stage 8 is not established; this is not a Java verifier error in the generated certificate helper.

The installed APK contains Hexora's signature-patch marker. Comparison with the original APK in the phone's Download/Nagram directory found only `classes.dex` changed among existing entries. All 114 other original entries, including `assets/p.bin`, the manifest, resources and three native libraries, have identical uncompressed bytes. New entries are signing metadata and Hexora's patch marker. This rules out corruption of those payload/library bytes by repacking; it does not rule out a packer rejecting a changed signature, DEX or archive layout.

The original Application declares a native byte-array decoder and byte-array-backed class-loader factories in its early bootstrap. These are outside the current Java certificate rewriter. The native libraries also contain a certificate-mismatch diagnostic, but the crash does not establish that it caused stage 8. No native protection was altered and the protected payload was not decrypted.

## Source correction

`SignaturePatchCompatibility` detects the combination of an implemented `attachBaseContext(Context)`, native byte-array decoder, and implemented byte-array-backed `ClassLoader` factory in the manifest Application. The patcher checks this before rewriting that class and aborts before helper assembly, signing or publication. The error explains that this startup path is unsupported, no output was saved and an existing patched copy is not repaired. Ordinary native cryptography alone is not rejected. The pattern is deliberately limited and is not comprehensive packer detection.

This corrects Hexora's handling of this unsupported input. **It does not make the already-patched Xian Esp runnable or implement its native verification/decryption support.** Use the original APK rather than the previously generated `_sigkill.apk`.

## Validation and limits

Java syntax/resource parsing, local dependency API inspection and whitespace checks were performed without compiling. A separate read-only DEX inspection confirms that the original Xian Esp has the detected structure; this does not execute the production Java detector. Six regression tests cover the matching structure and ordinary/unrelated alternatives, but have not been executed because builds remain disabled. Native acceptance requires an authorized build and must verify that this APK is rejected with no new output, and ordinary supported fixture APKs still patch successfully.

Local evidence (ignored artifacts): `artifacts/signature-crash/xian-crash.txt`, `inspection.json` and `source-checks.json`. APKs copied for inspection also remain in that ignored artifact folder. No device files were modified.
