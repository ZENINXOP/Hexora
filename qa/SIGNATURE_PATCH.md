# APK signature-check patch — 2026-10-10

Status: source implementation only. No Gradle build, compilation, helper assembly, APK packaging, installation, unit-test execution or native runtime verification was performed. AI-assisted contribution: OpenAI Codex; upstream notices retained.

Broader-engine follow-up: the dialog now also offers pinned LSPatch v1.2 advanced and experimental arm64 native-read modes. The static Java path described below remains available. See [LSPATCH_SIGNATURE.md](LSPATCH_SIGNATURE.md) for provenance, behavior, limits and source-only validation; no claim of universal or Xian Esp compatibility is made.

Follow-up: an installed Xian Esp output fails in its native packed bootstrap (`PackedApp.xdec`, `XDEC:8`). Added a limited structural compatibility guard that rejects this loader pattern before publishing an APK. This does not repair the target app or add native packer support. See [SIGNATURE_CRASH.md](SIGNATURE_CRASH.md) for observed evidence and validation limits.

## User flow

Open an APK's info dialog → More → **Kill signature verification** → **Patch and sign**. The existing signing-key authentication flow supplies the selected key. The input must be an original, validly signed standalone APK with `classes.dex`. Split APKs/bundles must be merged first. Unsigned, already-patched and unsupported inputs report an error rather than claiming success.

The operation stages an independent input snapshot, scans all conventional top-level DEX files, patches supported references, adds small runtime certificate helpers to the primary DEX, strips stale signature entries, aligns and signs the new APK with V1/V2/V3, and checks its signature and alignment before publishing. Output uses an unused `_sigkill.apk` name beside a writable normal source; cache/protected sources export to `Download/Hexora`. Existing files are reserved without overwriting. Temporary files are cleaned on success/failure/cancellation. The original source is never written by this workflow.

## Coverage and limits

- Direct reads of `PackageInfo.signatures` return the original certificate for the APK's own package, preserving null when signature flags were not requested. Other package results pass through.
- Direct reads of `PackageInfo.signingInfo` mark the owning package's `SigningInfo` objects in a synchronized weak map. Direct `getApkContentsSigners`, `getSigningCertificateHistory`, `hasMultipleSigners` and `hasPastSigningCertificates` calls use original signer/history data only for those marked objects. Other objects call their real Android methods.
- Legacy and API 28 helpers are separate classes. Multiple signer/history semantics are represented; legacy reads use the oldest certificate for a rotation lineage. Original method registers, labels, debug metadata and exception handlers are carried through the DEX mutable instruction model.
- Direct PackageManager `checkSignatures`/`hasSigningCertificate`, reflection, JNI/native checks, APK digest checks, encrypted/packed DEX, dynamically loaded code, Play Integrity and server checks are outside this implementation. Android's installer verification is unchanged. Successful output signing is not proof that an app's protection is bypassed.
- DEX memory limits are heap-dependent (8–32 MiB per original DEX); primary method/reference overflow, insufficient storage and malformed inputs fail without publishing an APK. V4 sidecars are not generated. APK payloads/resources remain byte-identical except changed DEX, stale signature metadata and the added patch marker; uncompressed entries remain stored before alignment.
- Added the built-in menu action without shifting plug-in dispatch incorrectly: plug-in/external offsets derive from the built-in list length, with null entries filtered before titles and dispatch are generated.

## Verification

Source parsing passed for six affected/new Java files, including the two test classes. Strings parse with unique names; app resource references resolve; helper method/label/register/call structure was inspected without assembly. Relevant DEX, certificate lineage, APK and datastore API declarations were checked in the local dependency sources. `git diff --check` passed. [Source-check record](artifacts/signature-patch/source-checks.json).

Eleven JUnit tests were added, **not executed**: field read registers/aliasing, branch targets, try/catch ranges, high register invoke ranges, modern capture/boolean results, unrelated fields, stored libraries/assets/secondary DEX preservation, cancellation and source snapshots. Helper runtime behavior and complete APK patch/sign/install flows are also untested.

The existing app APK remains SHA-256 `649146dfa9cc5ba894e37d8ef43b74bdc8fdbad8007bbe0bc1bc04efa71f6ad2`; it does not contain this source update.

## Pending native acceptance after an authorized build

1. Run the new JUnit tests. Assemble/inspect the generated helper for single-signer, multi-signer and rotation fixtures; verify no unresolved tokens or old-API class loading regressions.
2. Patch owned fixtures covering legacy reads, API 28 current/history/boolean APIs, secondary DEX checks, unrelated package queries and missing signature flags. Install/launch the outputs and compare results with the original app. Repeat on API 19, 28 and current Android.
3. Hash original APKs before/after. Check resources/assets/native library bytes, 16 KiB alignment, certificate schemes, startup behavior and supported check outcomes. A signed output alone is insufficient acceptance.
4. Check unsigned, malformed, modified/invalid signature, split, no-supported-check, repeated patch, DEX overflow/memory limit and no-space failures. Confirm no published output on failure and no original edits.
5. Exercise Hide/notifications, Back cancellation, activity recreation, repeated operations, output-name collisions, staged archive/protected inputs, and built-in/in-process/external plug-in actions.
