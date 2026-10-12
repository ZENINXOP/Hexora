# Application package rename — 2026-10-13

Requested application ID: `com.hexora.zenin` (previously `app.hexora.manager`).
Version remains 0.1.2 / 13; compile SDK 36, target SDK 28, minimum SDK 19.

Changed the Gradle application ID, derived the editor resume action from
`BuildConfig.APPLICATION_ID`, and updated the owned sharing fixture and active
ADB test tools. Preference paths in the test tools now derive from their package
constant. Java source namespaces and plugin protocol actions retain their existing
names so component classes and plugin compatibility remain valid.

Validation:

- Offline debug build succeeded; 187 JVM tests passed, zero failures/errors.
- APK manifest reports `com.hexora.zenin`; all three provider authorities use it.
- APK signature verifies with v1 and v2 schemes.
- Installed on MuMu `emulator-5554`, alongside the previous package; original
  installation and its private data were preserved.
- Granted storage access to the new installation and confirmed Downloads opens.
- Owned Telegram-shaped content URI with a temporary read grant imported into
  the new package's private `files/Shared files` directory. Actual file bytes and
  the text editor display both showed `URI_GRANT_OK`.
- Editor Minimize → sidebar Resume editor returned to the same document.
- Installed APK SHA-256 matches the distributed artifact:
  `e21ae791277b01b46e0739f3dab71c6b00b375afd159cafe8aced56894869a13`.

APK: `qa/dist/Hexora.0.1.2-com.hexora.zenin.apk`.
Build, manifest, signature and UI evidence: `qa/artifacts/package-rename-20261013/`.
The existing feature suites were not rerun in full for this identity-only change.

AI-assisted implementation and validation: OpenAI Codex.
