# Hexora 0.1.4 release validation

October 17, 2026. AI-assisted implementation and validation: OpenAI Codex.
See [release changes](../docs/releases/0.1.4.md).

## Build and unit tests

JDK 17.0.14, Gradle 8.13, compile SDK 36, target SDK 28, minimum SDK 19.
The release build and unit tests completed successfully with:

```text
:app:assembleRelease :app:testReleaseUnitTest :sdk:testDebugUnitTest --console=plain
```

App tests: **252 passed**. SDK tests: **13 passed**. Total: **265 passed**,
zero failures, errors or skipped tests in the final XML results. SDK tests were
up-to-date; the app test task executed for the changed sources.

Two initial offline attempts failed because lint dependency jars were missing
from the local cache. The online retry resolved them and completed release vital
lint, compilation, tests and assembly. The bundled editor AARs still emit Kotlin
metadata-version diagnostics; these did not fail this build. Existing manifest
namespace and Gradle deprecation warnings remain. A clean full lint run is not
claimed. Original attempt logs are retained under ignored QA artifacts. A further
offline rebuild and app-test execution passed after removing the obsolete
Android-specific selection-row layout.

Changed/new Java and XML source parsing and `git diff --check` also passed.

## Signing and assets

- Package `com.hexora.zenin`, version 0.1.4/code 15, release/non-debuggable.
- APK: **27,956,291 bytes**.
- SHA-256: `ace2488d8a041967e67215fbe9f3f1852598128ad5c08c2ef969cfcf163b8669`.
- Certificate SHA-256, matching 0.1.3:
  `148849e024506dd27a9d070f1b45993c3c13cfd5f96af4bc0ca5d5019c54a20a`.
- APK aligned with `zipalign -P 16`; final alignment and v1/v2/v3 signatures
  verified. No v4 sidecar is published.
- Main APK contains the exact published catalog bytes. The seven optional pack
  APKs, catalog and detached signature are byte-identical to the previously
  verified 0.1.3 release assets. Every pack matches its catalog SHA-256.
- Release assets include the main APK, its `.sha256`, `SHA256SUMS`, seven
  optional pack APKs, `packs.json` and `packs.json.sig`.

Signing configuration and private keys remain outside the repository and assets.

## Runtime scope

The exact signed APK successfully updated the signed 0.1.3 app on the existing
isolated Android 16/API 36 release QA emulator using `adb install -r`.
The emulator SDK path required correction before startup. No physical device
or owner AVD was used. Focused UI smoke results are retained with the release
artifacts.

The first visual pass found white selection-menu rows in Dark mode. An obsolete
`layout-v21` override was still using a different card widget and layout. An
intermediate local background fix exposed that widget mismatch as a cast crash
in QA. Removing the obsolete variant makes all versions use the shared Material
layout. Failed captures and crash logs are retained separately from the final
verification run; those intermediate APKs were not published.

Both final UI scenarios passed on the exact signed APK: Light and Dark editor
preferences, font choice dialogs, selection-menu opening, checkbox toggling and
back navigation. Screenshot pixel checks confirmed white Light rows and dark
Dark rows. Both selection-menu captures were also visually inspected for
readable icons, labels and checkmarks. The final run recorded no Hexora process
crash. Results and captures are in `smoke-verified/`; this is a focused smoke
check, not full UI or device coverage.

This validation does not reproduce the original unspecified APK-build crash,
exercise root/Shizuku services, profile performance across devices or cover every
APK resource/compiler variant. Historical source-only reports retain their
original status; this report records the new release build and validation.

Local evidence is in ignored `qa/artifacts/release-0.1.4/`, including signing
verification, manifest checks, checksum audit and UI captures. Build attempts are
in ignored `qa/artifacts/release-0.1.4-build*.log`.
