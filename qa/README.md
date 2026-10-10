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

Set `HEXORA_ADB` and `HEXORA_SERIAL` to override the local defaults
(`E:\AndroidStudioSDK\platform-tools\adb.exe`, `127.0.0.1:16384`).
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
