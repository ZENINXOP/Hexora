# Broader APK signature compatibility — 2026-10-10

AI-assisted integration: OpenAI Codex. Status: source integration with imported, unchanged upstream runtime assets. **No build, compilation, APK packaging, signing, installation or target-app execution was performed.** The five read-only Python asset checks passed; the six new JUnit tests have not been run. Xian Esp startup remains unverified.

## Research and chosen implementation

| Project | Findings | Decision |
| --- | --- | --- |
| [JingMatrix/LSPatch v1.2](https://github.com/JingMatrix/LSPatch/releases/tag/v1.2) | GPL-3.0; Android 9+; embedded loader initializes hooks before the original app. Level 2 combines certificate-query hooks and common native APK-read redirects. Optional level 3 adds arm64 direct syscall handling; upstream explicitly warns that self-checksumming packers can detect the native changes. | Integrate the pinned standalone runtime. |
| [LSPosed/LSPatch](https://github.com/LSPosed/LSPatch) | Original GPL-3.0 repository is archived. | Prefer the maintained JingMatrix fork. |
| [aimardcr/APKKiller](https://github.com/aimardcr/APKKiller) | JNI/reflection bootstrap and APK-path spoofing; documentation gives no all-app guarantee. No repository license identified in its GitHub metadata/root listing during this review. | Research only; no code or binaries copied. |
| [L-JINBIN/ApkSignatureKiller](https://github.com/L-JINBIN/ApkSignatureKiller) | PackageManager-proxy approach; no repository license identified in GitHub metadata during this review. | Research only; no code or binaries copied. |

Primary implementation references: pinned [upstream patcher](https://github.com/JingMatrix/LSPatch/blob/v1.2/patch/src/main/java/org/lsposed/patch/ApkPatcher.java), [runtime signature hooks](https://github.com/JingMatrix/LSPatch/blob/v1.2/patch-loader/src/main/java/org/lsposed/lspatch/loader/SigBypass.java), [bootstrap](https://github.com/JingMatrix/LSPatch/blob/v1.2/patch-loader/src/main/java/org/lsposed/lspatch/loader/LSPApplication.java) and [constants](https://github.com/JingMatrix/LSPatch/blob/v1.2/share/java/src/main/java/org/lsposed/lspatch/share/Constants.java). These establish a broader approach, not evidence that Xian Esp works.

## App behavior

APK info → More → Kill signature verification now offers three methods in one scrollable, theme-aware dialog:

- **Java checks · lightweight** retains the existing DEX rewriter, API 19+ compatibility and its native-bootstrap guard.
- **Advanced · LSPatch** uses upstream level 2. It is selected initially on Android 9+ and creates an unused `_sigkill_advanced.apk` name.
- **Native reads · experimental, arm64** uses upstream level 3, chosen explicitly. It requires an input APK whose native-library ABI set is exactly `arm64-v8a`, preventing a Java-only or multi-ABI output from silently selecting an architecture where this extra coverage is unavailable. Output uses `_sigkill_native.apk`.

Advanced modes require a valid original signed standalone APK with a single signer and no signing-key rotation. Splits, previously patched inputs, duplicate ZIP names, unsupported native-only ABIs, oversized archives and insufficient cache space fail without publishing an output. Bounds: 1 GiB staged input, 2 GiB total expanded entries and 65,000 original entries. The selected signing key is authenticated through the existing flow. Originals and existing output files are preserved.

The new path stages a snapshot, records its certificate and original app component factory, raises output minSdk to at least 28, and sets the outer component factory to LSPatch's metaloader. Configuration is stored in both the manifest metadata and runtime asset using the upstream schema, including pinned release metadata. Missing original factories are omitted from JSON rather than serialized as null because the loader tests key presence. The original Application class is retained; the runtime supplies its original DEX from the embedded APK. No extra permissions, modules or manager dependency are added.

The **complete original signed APK**, including its signing block, is embedded unchanged. Outer payload entries are copied separately instead of using upstream nested ZIP links, trading additional disk space for compatibility with Hexora's current streaming ZIP/signing stack. Runtime native libraries and the embedded original are stored and aligned to 16 KiB. The alignment extra field records its multiple in the format recognized by the local apksig signer. Output is signed with V1/V2/V3, then checked for signature validity, required resource storage, native alignment and the SHA-256 of the embedded original before atomic publication. Cancellation and cleanup reuse the existing copy-only workflow.

## Dependency provenance and notices

Imported six runtime payloads from the official `lspatch-v1.2-487-release.jar`:

- Release SHA-256 from GitHub's release API: `d238fdc414d121b7fa454d8b4ccf420df3a8c97d563761861ff92bd9c5da2165`; downloaded bytes match.
- LSPatch commit: `0dc50f42503711b14f5e2bb217c2bdd6321ce5be`.
- Vector/core submodule commit: `e00c5c5038bbcc14e0b382ab893301d6993e6989`.
- Per-file sizes and hashes: `app/src/main/assets/signature/lspatch/provenance.json`; production checks independently pin all six hashes.
- Both DEX headers/checksums were checked. Native ELF machine IDs match all four declared ABIs, and every load segment declares at least 16 KiB alignment.
- The pinned repository GPL license, included release license files and integration notice are retained. Notices are also copied into generated outputs. About → libraries credits JingMatrix/LSPosed. No upstream runtime bytes were modified or executed.

The GitHub source update includes the corresponding upstream source in
[`third_party/lspatch`](../third_party/lspatch/README.md): an unmodified source
archive with all 13 pinned recursive submodules, build scripts and notices.
Its manifest records the archive hash and all 14 repository commits. The two
unavailable original libxposed URLs were resolved through JingMatrix mirrors
at the exact pinned commits. Preserve the source and notices when distributing
Hexora or outputs containing this runtime. This source publication does not
include a newly built APK or establish runtime compatibility.

## Coverage limits

No verified universal method was found. The runtime can cover paths unavailable to the original static Java rewrite, including queries made from dynamically loaded code and some JNI/native APK reads. Packed-code encryption, self-checksums, custom verification, anti-hook behavior, isolated processes, newer Android internals, hardware-backed attestation and remote verification can still defeat it. A valid signature and well-formed output do not establish successful startup. Multiple-signer/history behavior is intentionally rejected in the advanced path rather than pretending upstream's one-signature configuration represents it.

Xian Esp's original APK inspected earlier has arm64-only libraries and thus meets the experimental mode's ABI condition. Its `XDEC:8` cause has not been decoded, and no output has been generated or launched in this session. Its success cannot be asserted.

## Validation

Compiler-error follow-up: replaced the manifest child enhanced-for loop with an explicit `Iterator<ResXmlNode>`. The local `ResXmlElement` hierarchy exposes `iterator()` through `NodeTree` but does not implement `java.lang.Iterable`. Java syntax parsing did not catch this type error. The correction was checked against those local declarations without rebuilding.

Run the read-only checks without compiling: `py -3 -B qa/verify_lspatch_assets.py`. Five checks passed for pinned provenance, payload hashes, DEX integrity, ELF architecture/page alignment and notices. Java syntax parsing, resource XML/default-string references and local manifest/ZIP/signer API inspection also passed. Existing Hexora APK SHA-256 remains `649146dfa9cc5ba894e37d8ef43b74bdc8fdbad8007bbe0bc1bc04efa71f6ad2`.

Six JUnit tests were added for Unicode library alignment after compressed data, all page offsets/alignment metadata, unchanged embedded original bytes, cancellation, conservative ABI selection and misleading declared entry sizes. **Not executed.** Source parsing is not Java type checking. No runtime compatibility claims follow from these checks.

After an explicitly authorized build, run the JUnit tests and native acceptance on API 28/current Android. Verify both themes and large fonts; absent/relative/custom component factories; packed/native and ordinary Java fixtures; all four ABIs for level 2; arm64 eligibility and explicit selection for level 3; failure/cancellation cleanup, repeated patch rejection, selected-key signing and original hashes. Confirm actual 4/16 KiB mapping and post-sign offsets on device. For Xian Esp, compare fresh startup logs from the original and each advanced output, without overwriting its existing APK or app data.
