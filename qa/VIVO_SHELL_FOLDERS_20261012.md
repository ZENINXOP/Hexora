# Vivo shell and folder fixes — October 12, 2026

AI-assisted implementation and validation: OpenAI Codex. Changes remain local.

## Installed result

- Device: vivo I2508, Android 16 / API 36, serial `10BG5G0QSB001NP`.
- Hexora 0.1.2 / version code 13, compile SDK 36, target SDK 28 as selected
  by the owner. Legacy read/write storage declarations and runtime requests
  now apply on modern phones too; all-files access no longer skips them.
- Same-certificate update installed with ADB; app data was retained.
- APK: `qa/dist/Hexora.0.1.2-sdk28-shell-folder-fix.apk`.
- Installed base APK and saved APK SHA-256 both:
  `2e314d46e93f4e3b6cb69f6eb0bed1f52a78e5e5550851a12867bc1e33d2bc49`.
- APK signature verification passes V1/V2. This is the local debug-certificate
  APK, not an owner-release-certificate publication.

## Source fixes

- Tapping `.sh` (including uppercase `.SH` and ZIP entries) opens Edit/Execute.
  Explicit Open with → Script executor keeps the archive entry's save metadata.
- The executor offers app, available Shizuku, and available root modes. App
  scripts use their source directory as the working directory, so relative
  reads work; stdin closes, output/error streams drain separately, exit codes
  are retained, and the existing 120-second execution limit remains.
- Typed restricted paths reach the folder-access workflow instead of trying
  to create a directory the app cannot stat. Elevated directory checks use
  the selected backend instead of always consulting root. Locate requests
  also reach this workflow for restricted directories.
- Shizuku `canRead()` no longer claims direct app-UID access. File opening
  materializes Shizuku-backed entries on a worker before passing paths to
  editors; failures propagate instead of silently returning the inaccessible
  original. Binary file bytes stream across the UID boundary into unique,
  capped, app-owned cache files; shell no longer redirects into private cache.
- Shizuku listings retain wrapper metadata when filtering, follow symlinks,
  and identify parent folders as directories. Grant callbacks retry the
  pending folder. Restored editor paths use the existing access-aware reader.

## Verification

`:app:assembleDebug :app:testDebugUnitTest --offline` passes with 161 tests,
zero failures, errors or skips. `git diff --check` passes for touched tracked
files. APK identity on the device was compared with the distributed APK.

Native ADB/UIAutomator checks use owned Download fixtures and record actual
script output and archive bytes:

| Check | Result |
| --- | --- |
| Script with a space in its filename; relative sibling read; working directory; stdout/stderr; exit 7; unchanged source | PASS |
| Uppercase `.SH` → Edit | PASS |
| ZIP entry → Execute; output and exit 0 | PASS |
| Dotted folder → nested empty folder; parent navigation; separate empty folder | PASS |
| ZIP entry → Open with → Script executor → Edit → Save → Yes; resulting ZIP contains edited script bytes | PASS |
| Legacy runtime permission denial returns to browser without a prompt loop | PASS |
| Legacy runtime permission acceptance restores read/write grants and browser | PASS |

Evidence is under `qa/artifacts/vivo-shell-folders/`: `runtime-final/`,
`installed-final/`, `manual-archive-retest/`, permission screenshots/XML,
and `build-installed-final.log`. The first attempts remain visible. Initial
driver failures were a `/sdcard` vs `/storage/emulated/0` assertion, ADB omitting
empty pushed directories, and an unquoted `#` being interpreted by the remote
shell; the driver was corrected and each affected case retested successfully.

## Protected PUBG folder remains blocked

The reported `Android/data/com.pubg.imobile/files/login-identifier.txt` exists
and is 32 bytes according to the ADB shell. Hexora's UID receives Permission
denied even after the SDK 28 update and both legacy runtime grants. Vivo's
system folder picker rejects the requested tree and returns to primary storage
with Use this folder disabled. This is a device permission restriction; the
file is not missing. No game data or login-identifier bytes were modified.

The installed MT Manager (`bin.mt.plus`, version 2.26.10, target 28) lists
Android/data's top-level entries, but testing this same PUBG child path shows
its system-grant prompt and an empty listing too. This comparison does not
establish how another MT version or previously granted folder behaves.

The owner explicitly chose to keep SDK 28 without installing Shizuku. No
Shizuku app/service was installed or started. Protected-file opening/saving,
Shizuku execution/staging and root execution are therefore **not validated**
on this phone; the reported protected-file read is **blocked**, not a pass.
SDK 28 should not be presented as granting unrestricted Android/data access.

Android's [storage documentation](https://developer.android.com/about/versions/11/privacy/storage)
describes app-specific directory restrictions; the [Shizuku API documentation](https://github.com/RikkaApps/Shizuku-API)
explains the separate shell/root UID used for supported elevated access.
