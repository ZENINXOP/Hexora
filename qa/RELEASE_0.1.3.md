# Hexora 0.1.3 release validation

October 17, 2026. AI-assisted implementation and validation: OpenAI Codex.
See [release changes](../docs/releases/0.1.3.md).

## Build and unit tests

JDK 17.0.14, Gradle wrapper 8.13, compile SDK 36, target SDK 28, minimum SDK 19.
The final app build and tests completed successfully using:

```text
:app:assembleRelease :app:testReleaseUnitTest :sdk:testDebugUnitTest --offline
```

All seven pack `assembleRelease` tasks also completed. App release tests:
**217 passed**; SDK tests: **13 passed**. Total **230 passed**, zero failures,
errors or skipped cases in the final XML results.

An initial build rejected resource references in the folder vector during
API 19 PNG generation. Literal-colour light/night drawables corrected this.
Three TAR tests then exposed their use of unmocked Android filesystem-link
APIs under the local JVM. The extraction implementation now has an internal
storage-policy overload; JVM tests select portable link materialization and
verify the resulting contents and deferred links. The public device path keeps
native link creation enabled. This does not claim native link syscall coverage.
An attempted test-only platform shim was discarded because `android.system`
belongs to the Java base module during Android unit-test compilation.

Existing editor AARs emit Kotlin metadata-version warnings from D8/lint. Release
vital lint, compilation, assembly and unit tests completed; a clean full lint run
is not claimed. Detailed failed attempts and the final success are retained in
ignored local build logs.

## Signing and packaging

- Package `com.hexora.zenin`, version 0.1.3/code 14; release, not debuggable.
- Main APK: **27,872,969 bytes**.
- SHA-256: `02dd20215c93b30dd4d29f4ceaebe4243b2ea3eccc5322c473b7c2eac23c82ae`.
- Certificate SHA-256, matching 0.1.2:
  `148849e024506dd27a9d070f1b45993c3c13cfd5f96af4bc0ca5d5019c54a20a`.
- Main app and all seven pack APKs were aligned with `zipalign -P 16`, then
  signed with the existing private Hexora release key. APK v1/v2/v3 signatures
  and final alignment were verified. No v4 sidecars are published.
- The supplied artwork is preserved byte-for-byte in the README branding file;
  launcher density/padding derivatives were generated from it. The 192px launcher
  asset was visually inspected. Folder drawables are black with light/night
  outlines. The Android themed icon uses the new folder/H motif.
- The exact app APK contains the same catalog bytes as the published signed
  catalog. Its detached RSA/SHA-256 signature was verified using the pinned
  public key. All seven newly signed pack APKs passed checksum, manifest-version,
  catalog-version and source-build-configuration checks.

Published assets are the main APK, its `.sha256`, `SHA256SUMS`, seven optional
pack APKs, `packs.json` and `packs.json.sig`. Private signing material and
credentials remain outside the source tree and release assets.

## Runtime validation

Installed the exact signed APK on a fresh Android 16/API 36 emulator. Four
functional checks passed with disposable files, and two theme checks passed:

- Open Rename for `app.apk.`, rename to another trailing-dot name, and reopen.
- Copy an item into its current folder: all choices appear; Replace preserves
  its bytes and creates no duplicate or recovery file.
- Keep both creates a numbered copy with identical bytes and preserves source.
- Rename over an existing file with Replace: destination contains source bytes,
  the old source is gone and no replacement backup is left.
- Light and dark folder-icon captures: black fill and visible outlines confirmed
  by visual inspection. README screenshots come from this signed APK.

The first UI driver treated an XML leaf as false during a wait; this was corrected.
Theme capture initially missed an offscreen Preferences menu entry and hit
transient UIAutomation registration/dump errors. Scrolling, retry handling and
serialized captures corrected the driver. Final results combine the four passing
functional cases and the two passing theme cases; failed attempts remain in logs.
The crash buffer contains UIAutomator errors, with no Hexora process crash recorded.

This is a focused smoke check, not full device coverage. Actual network pack
installation, native filesystem-link syscalls, root/Shizuku paths and signature
patcher startup compatibility are not covered by these six checks. Pack files,
checksums, versions, signer and catalog signatures were checked separately.

The first temporary read-only emulator clone had insufficient free storage for
installation. That attempt did not install Hexora. A separate fresh QA AVD was
created under ignored release artifacts; the owner's original AVD data and
physical devices were not changed.

Historical reports describe earlier evidence and retain their original
source-only status. Native signature-patcher compatibility and universal device
coverage are not claimed by the unit tests or file metadata checks.


Release evidence is in ignored `qa/artifacts/release-0.1.3/`, including final
build logs, test XML summaries, signature/alignment checks, pack audit, screenshots
and `final-smoke-results.json`. The release tag includes corresponding source,
not private signing material or generated QA/device data.
