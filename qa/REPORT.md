# Hexora native QA and UI report

> Broader signature-engine follow-up (2026-10-10): integrated pinned JingMatrix/LSPatch v1.2 runtime assets with advanced and opt-in arm64 native-read modes beside the Java patch. Five read-only dependency checks passed; source checks passed. No build, APK packaging, installation or target-app execution; Xian Esp compatibility is unverified. See [LSPATCH_SIGNATURE.md](LSPATCH_SIGNATURE.md).

> Signature crash follow-up (2026-10-10): read-only phone logs locate Xian Esp's startup failure in its native packed loader (`XDEC:8`). Source now rejects this unsupported bootstrap pattern before publishing another output. The target app is not repaired; no build or runtime validation was performed. See [SIGNATURE_CRASH.md](SIGNATURE_CRASH.md).

> Latest APK tools follow-up (2026-10-10): added APK info → More → Kill signature verification for supported Java certificate API references, using a separate signed output copy. Source checks passed; the new tests and APK/runtime workflow have not been run. See [SIGNATURE_PATCH.md](SIGNATURE_PATCH.md).

> Latest loading UI follow-up (2026-10-10): rainbow GIF replaced with a native single-accent progress indicator, known-total percentages and batched status updates. Source checks passed; no build or native runtime verification. See [LOADING_UI.md](LOADING_UI.md).

> Latest source-only UI follow-up (2026-10-10): file icon tiles and the Open with chooser now follow the supplied reference layout, including Search and Type filters. No build or native runtime validation was run. See [OPEN_WITH_UI.md](OPEN_WITH_UI.md) and its labelled design preview.

> Latest source-only follow-up (2026-10-10): editor large-file protection, minimize-only recents, and Save state have been changed after the last APK. These changes are not built or runtime-validated. See [EDITOR_PERFORMANCE.md](EDITOR_PERFORMANCE.md). Earlier passing results apply to their recorded APKs.


Test environment: MuMu, Android 15 / API 35, 900 × 1600 and 1600 × 900;
package `app.hexora.manager`, version 0.1.1. Work performed October 9–10, 2026.
Build: JDK 17, Gradle 8.13, debug configuration. No release keystore was used.

## Changes

- Clearer dual-pane paths, empty states, consistent file rows and compact settings.
  The compile action is in the toolbar and no longer covers file rows.
- Distinct document icons for 30 groups: PY, XML, JAVA, TXT, KT, JS, TS, JSON,
  HTML, CSS, SH, SQL/database, MD, CFG, YML, SMALI, C, C++, RS, GO, PHP, RB,
  LOG, CSV, DOC, XLS, PPT, fonts, certificates and binary files. Color and visible
  labels distinguish types. Icons are drawn locally at display resolution and
  work in normal folders, ZIP entries and FTP listings. Existing media thumbnails,
  APK icons, archive icons and unknown-file fallback remain supported.
- Common source/configuration files, including uppercase `.PY`, open in the
  native text editor. Ordinary Java documents now receive Java highlighting
  while remaining editable. Common source and configuration files now select
  syntax colors automatically; File Options → Syntax offers automatic detection,
  plain text and supported languages, with separate choices for each document.
- Folder enumeration/stat/sorting and search run off the UI thread, with stale
  results discarded. File rows cache sizing/date formatting. Pane animation is
  shorter, and detached views stop animating.
- Checked recursive copy/move/delete preserve nested and empty folders. Duplicate
  names, self/descendant destinations, overwrite collisions and failed writes
  receive explicit errors. Source data is verified before fallback move deletion.
- Editor saves encode before writing and publish atomically, preserving `.bak`.
  Backup restore honors edited names and safely swaps existing versions, including
  extensionless files inside folders whose names contain `.bak`.
- ZIP folder copy/rename and ZIP-to-ZIP staging preserve paths and empty folders.
  Archive extraction rejects traversal and TAR links. Archive creation publishes
  only completed output and does not overwrite an existing file.
- Compression choices persist and remain usable on MuMu. ZIP-only password and
  compression controls are hidden for formats whose writer does not implement them.
- Cancelled operation dialogs release waiting workers; multi-selection controls
  activate correctly; both panes refresh after changes.
- FTP validates ports and host input, uses binary transfers, closes connections,
  resolves remote working directories, checks server replies and verifies a
  downloaded/uploaded file before deleting its source during a move.
- Flat extracted APK projects can be rebuilt with their DEX/binary resources,
  alignment and optional signing. Repeated builds avoid embedding earlier output.
- Android 15 audio playback declares its required foreground-service permission.
  Error dialogs show a short message with optional full details.

## Validation

`assembleDebug`, `app:testDebugUnitTest` and `sdk:testDebugUnitTest` pass:
**114 tests, zero failures/errors/skips** (101 app, 13 SDK). Tests cover copy/move
integrity, atomic save/restore, names/collisions, archive paths/formats, search,
ZIP rename, APK rebuilding, extension classification and 19 syntax lexer/state
regressions, alongside existing tests.

The original broad validation contained **67 native scenarios, all PASS**, on
the previous APK (preserved as `qa/dist/Hexora-before-dex-ui-debug.apk`), with
original attempts retained in `qa/artifacts/validated-results.json`. Some scenarios
validate only screen opening, as specified in the table. The updated APK passes
host `apksigner verify` and `zipalign -c -P 16 -v 4`. The final AndroidRuntime error
capture is empty; no new app crash was observed in the concluding tests.

Native ADB/UIAutomator suites assert actual file bytes, hashes, ZIP entries,
permission UI and activity state. A screen-open check establishes only that scope;
it does not certify every action on that screen. Earlier failures remain in logs
and iteration results. Final retests are recorded separately.

| Feature / screen | Status | Verified scope |
|---|---|---|
| Main file manager | PASS | Both panes, navigation/history, sync, new file/folder, sort/filter, empty folders |
| File operations | PASS | Nested/empty-folder copy, 16 MB hash-checked copy, move, rename, collision refusal, delete cancel/confirm |
| Multi-selection | PASS | Select/invert and actual copy of selected folders |
| Search/bookmarks | PASS | Filename/content search, result navigation, bookmarks/history UI |
| File icons/themes | PASS | Screenshot review in light/dark, ZIP entries; light/dark/black settings |
| Text editor | PASS | Open/edit/save, undo/redo controls, backup integrity, Java colors and editable save, uppercase Python open |
| Editor preferences | PASS | Real preferences screen, font/theme and editor customization options visible |
| Text comparison | PASS | Two-pane selection, common and changed lines rendered from two real files |
| DEX comparison | PASS | Two-pane selection/options and identical sample DEX files compare equal |
| Hex editor | PASS | 16 MB file opens; 16-byte file edit saves `AB` and preserves original backup |
| Properties/checksums | PASS | Actual byte size and SHA-256 comparison |
| ZIP | PASS | Browse, nested/folder rename, local/ZIP-to-ZIP folder copy, single-file move out, extract, create, cancelled add |
| Same-archive copy/move | PASS | File move and nested/empty-folder copy; pane refresh waits until archive mutation completes |
| 7z / TAR | PASS | Native 7z create/extract and TAR extract; gzip/bzip2/xz and malformed-path cases in unit tests |
| APK inspection/resources | PASS | APK metadata, ZIP contents, binary manifest viewing, ARSC table browser |
| APK build/sign/verification | PASS | Native flat project build, invalid XML leaves original intact, rebuilt APK signing; native alignment/v1/v2 verified |
| DEX editor | PASS | Properties, sample class/package browser, class opening, themes, tab switching, edits/undo, Smali assembly/session save, rapid Back and rotation; advanced transformations are outside this pass |
| ARSC Plus / translation / querier | PASS | Actual screen opens from ARSC action menu; editing/recompilation not certified |
| FTP server/client | PASS | Invalid oversized port, authenticated local server/client, 16 MB download hash and remote-source removal after move |
| Audio player | PASS | Mini-player, full player and play/pause control after fixing Android 15 service permission |
| Image viewer/editor | PASS | Image display/properties and editor screen; image export not certified |
| APK extractor | PASS | Installed-app list screen; extraction actions not certified |
| Plugins / Wi-Fi / storage screens | PASS | Native sidebar navigation and screen loading; no cache deletion/network reconfiguration performed |
| Permission denial | PASS | UID storage denial, permission guidance and recovery after restoring access |
| Large directory responsiveness | PASS | 2,500 files; 269 recorded frames, 2 janky (0.74%), median 5 ms, p95 17 ms on MuMu |
| Android lint | FAIL | Last recorded pre-syntax run: 139 errors / 3,011 warnings, predominantly existing API compatibility, translations and lint configuration issues; bundled Kotlin metadata mismatch also reported. Not rerun for this syntax follow-up |
| Root / Shizuku | BLOCKED | No `su` binary or Shizuku manager in this emulator; elevated operations unverified |
| Other Android versions / real hardware | BLOCKED | No additional device matrix supplied; minimum-SDK compatibility is not certified |
| AI / optional tool packs / external plugins | BLOCKED | No credentials or configured packs/services; external actions not certified |
| Standalone clipboard cut/paste | NOT IMPLEMENTED | Two-pane Copy/Move available; a separate clipboard workflow was not added |
| FTP folder transfer / direct remote preview | NOT IMPLEMENTED | File transfers supported; extract/download first guidance for unsupported cases |
| Source syntax colors | PASS | Actual Python/XML/JSON/JS/Kotlin/SQL/CSS/YAML/HTML pixels, manual/automatic/plain selection, highlight toggle, light editor theme, live multiline editing/undo, UTF-8 save/backup and tab isolation; lexical coverage limits in `SYNTAX.md` |
| Encrypted archive reading | NOT IMPLEMENTED | No password-entry reading workflow certified; do not infer it from ZIP creation controls |

## DEX editor opening and theme follow-up

The updated APK removes the overlapping class-list/editor slide and horizontal
page scrolling during programmatic class switches. Visibility and toolbar state
change together; Back no longer gets blocked by a partially translated editor.
Cursor position restoration happens before the first code frame rather than
jumping after a fixed delay.

Each Smali editor now owns its syntax analyzer and color scheme. Only grammar
and instruction data are shared/preloaded. Initial backgrounds, code colors,
line numbers, selection and completion surfaces follow the saved app theme,
including light, dark and black. The activity keeps BaseActivity's saved theme
instead of overriding it after creation. Class tabs and the cursor/method header
have updated typography and surfaces. Destroyed editor views release their
resources and reject stale asynchronous loads.

First-loaded Smali also initializes its original-content baseline. Undoing back
to that content now clears the modified marker correctly.

The DEX follow-up build passed **95 unit tests** and **10 targeted native checks**:
three DEX themes, switching classes, unsaved edit/switch/undo, eight rapid
open/Back cycles, rotation, Smali assembly/session save and undo, Java highlighting/
edit/save/backup, and uppercase Python-file opening. No app crash or Smali language
initialization error was observed. Whole-DEX export, large DEX stress and advanced
transformations were not certified by these targeted checks.

Evidence: `dex-ui-results.json`, `dex-ui-final-attempt-results.json`,
`dex-ui-java-retest-results.json`, `dex-ui-logcat.txt`, `dex-code-*.png` and
`dex-opening-gfxinfo.txt`. Preliminary driver failures remain recorded: an extra
Back press opened the unsaved-changes dialog with MuMu's hardware keyboard;
the Java test selected a different file in the crowded fixture folder. The driver
now detects the class list before another Back and uses an isolated Java folder.
The affected checks passed on retest. At the end of that follow-up, the aggregate
contained 75 latest scenarios across the broad validation and DEX follow-up;
they were not all repeated on one APK.

## Syntax-color follow-up

The editor now selects syntax from the filename, including uppercase extensions,
using a separate background incremental analyzer for each editor. Java keeps its
bundled language; DEX Smali keeps its TextMate analyzer. New lexical coloring covers
Python, XML, JSON, JavaScript, TypeScript, Kotlin, Groovy/Gradle, HTML, CSS, YAML,
configuration files, Shell, SQL, Markdown, C/C++, Rust, Go, PHP, Ruby and standalone
Smali. Plain text and unknown extensions stay plain. Coloring does not rewrite the
document or apply code formatting.

Keywords/comments retain the editor preset's palette. Strings, numbers, properties,
tags/types and functions receive distinct colors with light/dark contrast. The
highlighting preference now actually enables/disables file syntax. File Options →
Syntax offers Automatic, Plain Text and the supported formats; overrides are keyed
by file path, URI or archive entry instead of just the tab title. The file-tabs
button now has an accessibility label.

The updated build passes **114 unit tests** and **16 targeted native scenarios**.
Actual CodeEditor screenshot pixels verify Python, XML, JSON, JS, Kotlin, SQL, CSS,
YAML and HTML colors. Native checks also cover plain/manual/automatic selection,
disabling/re-enabling highlighting, a light editor palette, live triple-string
recoloring/undo, UTF-8 save with raw original-byte backup verification, file-tab
language isolation, Java highlighting and DEX class syntax. Host APK v1/v2 signature
and 16 KB alignment checks pass.

Evidence: `syntax-results.json`, `syntax-final-attempt-results.json`,
`syntax-first-results.json`, `syntax-color-counts.json`, `syntax-logcat.txt` and
`syntax-*.png`. Earlier failures remain recorded: the driver initially scrolled
only down when earlier syntax choices were above the selected row, and compared
normalized editor text with a raw CRLF backup. The driver now scrolls both ways
and checks the actual original bytes. The previously unlabeled tabs button was
fixed and checked natively.

This is lexical coloring rather than a complete grammar/semantic language service.
Embedded languages, regex literals and some raw-string/heredoc forms are not fully
parsed; see `SYNTAX.md`. There are no new application libraries. The historical
aggregate combines validation runs; it must not be read as every feature having
been retested on this APK. Existing release-certification gaps still apply.

## Remaining certification gaps

This is a tested debug build, not an assertion that every existing tool is
production-ready. RAR variants, split APK installation, APK clone/optimization/
protection, advanced DEX rewriting, binary-resource edits, large/mutated DEX comparisons,
shared-content flows, overlays, accessibility services, camera/Bluetooth/NFC,
DNS configuration and destructive storage tools were not comprehensively exercised.
They need suitable fixtures, permissions and, where relevant, hardware or services.
FTPS server certificate/security behavior was not certified.

Lint remains a release gate. Its errors were not hidden with broad suppressions.
No claim is made that the declared minimum SDK 19 is fully supported.
MuMu occasionally crashes its `uiautomator` process after writing valid XML;
the driver accepts only a newly written valid dump and records the abnormal exit.
Those tool crashes are distinguished from app crashes. Actual app failures found
during this work were retained and followed by fixes/retests.

## Artifacts and reproducibility

- APK: `qa/dist/Hexora-validated-debug.apk`; SHA-256 beside it.
- APK SHA-256: `f8b1f7f5ad3e4b898ae90e9736397046986db0d784b84aa7e0c11a5cc20abb6e`.
- Native evidence: `qa/artifacts`, including per-scenario XML/screenshots, original
  failures, retests, signed/rebuilt APKs and `many-files-gfxinfo.txt`.
- Build/driver logs: workspace parent, `hexora-qa-*.log` and `hexora-dex-*.log`.
- Scripts and instructions: `qa/README.md`; fixtures are isolated under
  `/sdcard/Download/Hexora-QA-<timestamp>` and are not user folders.
- Pre-change backup: `E:\Hexora-backups\Hexora-before-20261009-2225`.
- Existing local `gradle.properties` changes were preserved. No commit, push,
  publication, proprietary MT assets or new application libraries were introduced.
