# Hexora 0.1.2 release validation

Release date: October 13, 2026. AI-assisted implementation and validation:
OpenAI Codex. See [the complete changes from 0.1.1](../docs/releases/0.1.2.md).

## Build and signing

Built offline with JDK 17 and Gradle 8.13:

```text
:app:assembleRelease :app:testReleaseUnitTest :sdk:testDebugUnitTest
```

- 187 app release unit tests and 13 SDK unit tests: **200 passed**, zero failures
  or errors. This is a release build, with `android:debuggable` absent.
- Package `com.hexora.zenin`; version 0.1.2/code 13; minimum SDK 19, target SDK 28,
  compile SDK 36. Provider authorities use the new package.
- Release APK DEX strings contain `https://t.me/Hexora_x`; the old channel URL
  is absent. README and About use the new channel.
- Aligned with `zipalign -P 16`, then signed using the existing private release
  key stored outside the source repository. Alignment verification passed.
- APK v1, v2 and v3 signatures verify. Certificate SHA-256 matches the actual
  downloaded 0.1.1 release APK:
  `148849e024506dd27a9d070f1b45993c3c13cfd5f96af4bc0ca5d5019c54a20a`.
- Artifact: `Hexora.0.1.2.apk`, **27,651,496 bytes**.
- SHA-256: `eeb394e9326fff4b5115aa9a237e8e5fe3d4871f548b80bdabe7bbccb9c3ab7e`.
- Downloaded previous release hash was independently checked against its retained
  release metadata: `90d384a6d1787d7f70aa42d163c90e476a57a0cd3ea317059d88c4c4dd60dec6`.

The first release build stopped on `ExpiredTargetSdkVersion`. The final build
disables that specific Google Play target-level lint check because this GitHub
sideload release intentionally preserves the owner's target SDK 28 choice.
Other release vital checks remain enabled. Lint emitted Kotlin metadata-version
diagnostics for the existing bundled editor AARs; Gradle's build, tests and
release vital tasks completed successfully. This report does not claim a clean
full lint run.

## Earlier focused device validation

MuMu Android 15/API 35 and disposable owned fixtures were used. The vivo was not
connected for the latest checks. Historical logs retain original failures and
corrected runs; they are not represented as one uninterrupted full-suite run.

- [Telegram and split merge](TELEGRAM_SPLIT_MERGE_20261012.md): 16 native checks.
- [Scripts, encrypted ZIPs and DEX actions](FIRST_BATCH_20261013.md): 19 native checks.
- [Clipboard, archive search and DEX package rename](SECOND_BATCH_20261013.md):
  12 native checks across focused runs.
- [Package rename](PACKAGE_RENAME_20261013.md): Downloads, granted shared-file
  import/editor display and Minimize/Resume smoke.
- [Editor/class/AAR checks](RUNTIME_20261011.md) and
  [archive/editor fixes](BUG_SWEEP_20261011.md) retain detailed earlier evidence.

## Signed release on MuMu

Installed the exact signed release on `emulator-5554`; the installed APK hash
matched the distributed APK. Six focused native scenarios passed on that build:

- File/folder clipboard copy, collisions and empty directories.
- Multiple-file Cut/Paste with source removal and exact destination bytes.
- Archive content search, opening a result, editing/saving and original backup.
- Password-protected archive content search.
- Invalid-regex validation and recovery.
- DEX package rename, typed references, original/current comparison, serialization
  and unchanged original backup.

About opens and displays version 0.1.2 and the Telegram Channel action. The
initial About driver looked in More instead of the sidebar title; that driver
failure was retained and the correct sidebar-title route passed separately on
the same signed APK. Captured crash output contains MuMu UIAutomator shutdown
crashes, with no Hexora process crash recorded.

The previous QA APK and all its private file/preference bytes were backed up
before the certificate switch and restored afterward. The old
`app.hexora.manager` installation was not removed or changed.
The restored debug QA installation was then updated in place to include the new
channel URL; its data was retained.

Local build, signature, alignment and device evidence is in
`qa/artifacts/release-0.1.2/`, excluded from Git. Private signing material,
credentials and emulator private-data backups are not publication assets.

The published assets are the signed APK and its SHA-256 checksum. The release
tag identifies the corresponding complete source, including tests, dependency
licenses and existing pinned LSPatch corresponding source.
