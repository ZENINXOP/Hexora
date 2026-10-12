# MuMu regression checks

These tests drive the installed native Android app through ADB and UIAutomator.
They assert file contents, archive entries, and UI state; compilation alone is not a pass.
The core suites use Python's standard library. The syntax-color suite also uses
Pillow to inspect actual screenshot pixels (already installed in this workspace).
No test framework was added to the app.

## Run

1. Build with JDK 17: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :sdk:testDebugUnitTest`.
2. Connect MuMu and install `app/build/outputs/apk/debug/app-debug.apk` using `adb install -r`.
3. Grant file access using Android's All files access screen. FTP tests also need notifications.
4. Run `py -3 qa/run_mumu.py` from the Hexora repository.
5. Run `py -3 qa/surface_regression.py`, `py -3 qa/final_acceptance.py`,
   `py -3 qa/last_smoke.py`, and `py -3 qa/comparison_regression.py` sequentially
   for the additional save/restore, media, icon, comparison and final transfer checks.
6. Run `py -3 qa/dex_ui_regression.py` for DEX class themes, multiple classes,
   unsaved edits/undo, rapid open/back, rotation, Smali save, and Java/Python
   editor regression checks. This suite uses the existing disposable fixture.
7. Run `py -3 qa/syntax_regression.py` for automatic Python/XML/JSON/JS/Kotlin/
   SQL/CSS/YAML/HTML coloring, plain/manual/automatic selection, preferences,
   light-theme contrast, live multiline editing/undo, UTF-8 save/backup, per-tab
   overrides, and Java/DEX regression. It creates its own isolated fixture folders.

Set `HEXORA_ADB` and `HEXORA_SERIAL` to select a different ADB executable or
emulator. The driver defaults to `E:\AndroidStudioSDK\platform-tools\adb.exe`,
prefers MuMu's `127.0.0.1:16384` when connected, and otherwise detects one
connected emulator. Multiple candidates require an explicit serial; physical
phones are never selected automatically. Set `HEXORA_ARTIFACTS` to a separate
directory for each run so earlier evidence is preserved.
Run only one driver at a time on this emulator. UI navigation and settings changes
are real. The scripts restore the dark theme and granted file access after their checks.

`prepare_fixtures.py` creates disposable data under `qa/artifacts/fixtures`, including
a 16 MB binary file, 2,500 files, nested and empty folders, archives, text/code/XML,
and the repository's sample plugin APK. Each core run creates its own
`/sdcard/Download/Hexora-QA-<timestamp>` directory. Scripts do not clear user folders.
Additional suites reuse that run's directory; run the core suite first.

## Evidence

`qa/artifacts/final-results.json` contains the initial full-suite results.
`qa/artifacts/validated-results.json` combines the latest retests with their
evidence filenames while retaining every attempt. `collect_results.py` generates
that summary from the saved evidence of this validation session.
Individual scenario XML
and PNG files, logcat, copied output APKs/archives, and `many-files-gfxinfo.txt`
are kept alongside it. Failures stay visible in iteration logs/results; a retest
does not turn an earlier failure into a pass. Artifacts and `qa/dist` are ignored by Git.

MuMu Android 15 occasionally crashes UIAutomator after it writes its XML dump.
The driver accepts only a newly written, valid dump, records the tool abnormal exit,
and fails if no fresh dump is available. This is separate from app crash detection.

DEX follow-up evidence is in `dex-ui-results.json`, `dex-ui-logcat.txt`,
`dex-opening-gfxinfo.txt`, and the `dex-code-*` screenshots. The preliminary
run is retained separately in `dex-ui-first-pass-results.json`.

The detailed coverage and remaining limitations are in `REPORT.md`. A successful
MuMu run does not establish compatibility across all Android versions or validate
root, Shizuku, every optional tool pack, and every advanced APK transformation.

Syntax follow-up evidence: `syntax-results.json`, `syntax-first-results.json`,
`syntax-color-counts.json`, `syntax-logcat.txt`, and `syntax-*.png`. Color checks
inspect the CodeEditor area and distinguish property, string, number and tag/type
colors; they do not infer highlighting from file extensions or toolbar icons.

## Signature and editor follow-ups

After an initial debug build has created the local Android debug signing key,
build the owned fixture with
`gradlew.bat -PsignatureFixtures=true :samples:signature-fixture:assembleDebug`.
It is an opt-in test application and is never bundled into Hexora. Its original
certificate must differ from the key selected in Hexora's signing preferences.
`py -3 -B qa/signature_regression.py` drives the actual APK More dialog, patching,
installation and launch, then checks supported certificate-query outcomes and
failure paths. It replaces only the disposable `app.hexora.signaturefixture`
test application, retaining original APK bytes and checking embedded originals.

After that plain suite finishes, build the optional arm64 JNI fixture with
`gradlew.bat -PsignatureFixtures=true -PnativeSignatureFixture=true :samples:signature-fixture:assembleDebug`
and run `py -3 -B qa/signature_native_regression.py`. The native fixture replaces
the same owned test package, reads the actual outer APK path supplied by the host,
and compares its native `open`/`read` CRC against the original signed APK. Both
advanced and experimental modes should redirect that common native read; this
does not certify direct syscalls or every native protection. Building
this variant replaces the plain fixture output; rebuild without the native flag
before rerunning the plain suite. Neither fixture is included in Hexora.

For MuMu's native x86_64 ABI, build with the additional
`-PsignatureFixtureAbi=x86_64` and run
`py -3 -B qa/signature_native_regression.py original-native-read advanced-common-native-read-install-launch`.
The arm64 experimental case needs a genuine arm64 process to certify native
coverage. MuMu ARM translation failed the native read checks on October 11 even
though the certificate queries passed; those failures are retained in the report.
Set `HEXORA_SIGNATURE_FIXTURE` to an archived fixture APK to retest a specific ABI.

`py -3 -B qa/editor_performance_regression.py` creates disposable small, 3 MiB,
100 MiB, single-line and binary-library fixtures. It checks clean/dirty/undo/Save
states, editing and backups, protected previews, original file hashes and
minimize-versus-Back tab behavior. Run these drivers sequentially after the core
suite, using separate artifact directories. Runtime outcomes for October 11 are
recorded in `RUNTIME_20261011.md`.

`py -3 -B qa/ui_polish_regression.py` checks light/dark file and archive icon
screens, uppercase PDF names, chooser search/no-match, Type and details, reopening,
and the Text editor action. It creates its own disposable folder. Chooser tiles
expose their labels as accessibility descriptions; the driver uses those labels.

`py -3 -B qa/dex_session_regression.py` uses a new copy of the sample DEX and
checks light/dark/black class screens, dirty Back/Cancel/undo, closed-class tabs,
Save/restore, rapid opening/Back and rotation. This suite tests the current
close-on-Back behavior; older DEX suites record earlier tab semantics.

`py -3 -B qa/dex_java_theme_regression.py` converts an owned sample class in
light/dark/black modes, compares the dominant Smali/Java background and gutter
pixels, checks read-only preview/original DEX hashes, and verifies switching back
to Smali. Black mode also checks rotation. Run this UI driver sequentially with
other drivers and use a separate `HEXORA_ARTIFACTS` directory.

`py -3 -B qa/refresh_gesture_regression.py` creates an owned file after each
pane's list is loaded. It checks that 40/150 dp pulls leave the cached list
unchanged, a 280 dp pull reveals the new file, and the other pane stays unchanged.
It also checks ordinary scrolling/row opening and refreshing an empty folder.
It temporarily forces portrait for consistent gesture distances and restores
the previous rotation settings. Run this UI driver sequentially.

`py -3 -B qa/file_routing_regression.py` checks a single main location header,
switching it on pane touch Down and when browsing ZIP entries, direct opening
of source/extensionless text, compiled and native files, uppercase DEX/ARSC,
fonts, and nested archive entries. Text archive edits retain the save-back
confirmation. Manual Open with remains available through the file context menu;
the chooser and binary text-preview suites use that explicit action.
The external PDF/DOCX cases use the owned fixture as a MIME/URI-read observer, then
removes the owned test package so it cannot capture later PDF taps. It checks
handoff, not document rendering. The fixture is never bundled into the manager APK.

The editor driver also checks binary `.lib`/`.so` decoded text previews at 262,149 and
33,554,437 bytes, mandatory library chooser opening, UTF-16LE/BE BOM preservation,
Unicode, Undo, Save and backups. The routing driver checks text libraries inside
ZIP entries and their save-back confirmation. Source and binary fixtures are
owned disposable files; no third-party library is modified by these checks.
The current library scenarios require a chooser for both readable and binary
libraries and assert that Text editor does not display hexadecimal rows. Older
direct-opening/byte-preview evidence describes the superseded implementation.

`py -3 -B qa/mt_text_view_regression.py` compares the library text presentation
using the same owned real x86_64 `.so` viewed in the installed MT Canary app.
It checks light/dark views, visible ELF/JNI strings, compact preview status,
disabled preview shortcuts/Save, unchanged native bytes after scrolling, and
editable symbol insertion/caret/Undo and a source-file light-theme screenshot.
It records screenshots for visual comparison.
Run it with its own `HEXORA_ARTIFACTS` directory, sequentially with other UI drivers.


`py -3 -u -B qa/class_aar_regression.py` creates and compiles owned Java 8 fixtures,
then checks JVM bytecode and Java conversion in light/dark/black themes, local
and AAR Smali conversion, nested JAR/AAR/ZIP parents via Back/Up/`..`, unchanged
source/archive bytes, malformed/oversized class handling, and minimized Java tabs.
Use a separate `HEXORA_ARTIFACTS` directory and run it sequentially with other
UI drivers. The bytecode view and generated Java are read-only previews.

`py -3 -u -B qa/editor_scroll_regression.py` checks that both panes keep the
same visible file and pixel offset after editor Back, minimize, local Save,
archive Back/Save, and viewing a binary library as text. Its owned 160-row
directories also exercise a new file inserted while the editor is open and
normal navigation to a different directory. Archive Save verifies the actual
entry bytes. Run this driver sequentially with other UI checks.

`py -3 -u -B qa/bug_sweep_regression.py` checks filter retention and pane isolation,
archive folder sort isolation, dirty Reload confirmation, every saved archive
tab, rotation, Save and Exit, resuming through local files and process restart,
missing source protection, uppercase APK autosigning and V4 sidecar verification.
It uses owned disposable files and the sample plugin APK; no third-party app
is modified. Keep MuMu visible and run native UI drivers sequentially.
The current fixes, APK identity and scoped evidence are in
[`BUG_SWEEP_20261011.md`](BUG_SWEEP_20261011.md).

`py -3 -u -B qa/first_batch_regression.py` checks the approved DEX actions,
password-protected ZIP opening/extraction/editing, streaming script controls,
root input, signing cancellation cleanup, and dark-theme controls with EOF.
Use a separate `HEXORA_ARTIFACTS` directory and keep MuMu on Hexora; the driver
checks the foreground package before sending input or capturing screenshots.
DEX add/import/rename/diff/save scenarios run in order in the same session.
It uses owned fixtures and temporarily restores signing/theme preferences.
See [`FIRST_BATCH_20261013.md`](FIRST_BATCH_20261013.md) for scope, evidence and APK.

`py -3 -u -B qa/second_batch_regression.py` checks local file clipboard capture,
copy/cut/paste, collisions, multi-selection, self-destination and missing-source
handling; archive filename/content/encrypted/regex search and editor save-back;
DEX package rename/diff/save; and dark archive-search/clipboard dialogs.
Keep MuMu on Hexora and run this driver sequentially with other native checks.
See [`SECOND_BATCH_20261013.md`](SECOND_BATCH_20261013.md).
