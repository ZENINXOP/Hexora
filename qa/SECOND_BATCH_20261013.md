# Clipboard, archive search and DEX packages — 2026-10-13

AI-assisted implementation and validation: OpenAI Codex. All changes remain local.
This follows the approved first batch. Target SDK 28 and the earlier Telegram,
split-merge, shell and ZIP-password fixes are retained.

## Added behavior

- **File clipboard:** local file/folder menus offer Copy to clipboard and Cut for
  single or multiple selections. More → Paste uses the selected pane's folder;
  More → Clear file clipboard discards the selection. The existing two-pane
  Copy/Move commands remain available. Clipboard state survives activity
  recreation within the app process and does not modify Android's text clipboard.
  Duplicate/ancestor selections are collapsed. Cut validates source/destination
  paths before moving; successful items are consumed and unfinished items are
  retained after failure. Existing names are preserved through unused output names;
  copying in the same folder asks for a new name. Paste does not navigate the
  opposite pane. Archive/FTP clipboard destinations are not offered in this batch.
- **Archive search:** Search works by name within the current ZIP/APK directory,
  with recursion, case/regex, content and size filters. Implicit directories are
  included and results open their real archive entries. Find in files searches
  text within the current archive directory and descendants, lists paths/lines
  and snippets, and opens matches in the editor with search and archive-save
  metadata. It supports session passwords and BOM-aware text decoding. Searches
  preserve archive bytes and navigation. Limits: 500 results, entries smaller
  than 2 MiB, 64 MiB expanded content per search and 20,000 lines per entry;
  oversized/binary/preview-only entries are skipped and limits are reported.
  Back on the progress dialog cancels that search and suppresses late results.
- **DEX package rename:** directory context menus now offer Rename. The whole
  package subtree and typed references across loaded DEX files are rewritten
  together; destination collisions are rejected before session mutation. Original
  names remain available for comparisons, and DEX memberships are retained when
  saving. Manifest names and reflection strings still require manual updates.

## Validation

Offline debug build and 187 JVM tests pass. New tests cover canonical clipboard
selection deduplication, partial-cut completion/newer-selection isolation, scoped
archive filters, encrypted/BOM content, search bounds/interruption/regex validation,
and subtree package rename/collision/reference rewriting.

Native checks use owned fixtures on the authorized MuMu (`emulator-5554`, Android
15/API 35). Evidence is under `qa/artifacts/second-batch-20261013/`. The final
native acceptance, signature and installed APK identity are recorded there.

All 12 native scenarios passed across focused runs: six clipboard checks
(file/folder copy with collisions, multi-cut, same-folder copy, self-destination,
missing source and Clear), four archive-search checks (names/open, content/edit
publication, encrypted content and regex validation), DEX package rename/diff/save,
and dark search/clipboard controls. The comparison XML contains both original and
renamed class descriptors; the saved DEX was parsed separately and its backup
matches the original. ZIP save-back verifies actual edited entry bytes and the
unchanged original backup. Light and dark screenshots were visually inspected.

The native suite ran on the cancellation-fix build. After the sole subsequent
app change to request the editor result, save-back was rerun on the final APK;
regex validation also passed in a focused run. Machine-readable results are in
`qa/artifacts/second-batch-20261013/acceptance.json`. Captured
`androidruntime-final.txt` is empty. Theme preferences were restored.

APK: `qa/dist/Hexora.0.1.2-second-batch.apk` (debug signed, 0.1.2/code 13).
SHA-256: `70536e7ae8f30ebf857302f7e1918df6fa04d6692b07545e2ad406d7737dacb1`.
Signature verification passes v1/v2 and the installed MuMu APK has the same hash.

Earlier failed logs are retained. The initial clipboard unit test found aliases
replacing the first selected path, fixed by preserving the first canonical entry.
Initial native-driver issues included force-stopping the process between clipboard
capture/paste, inspecting menu text rather than disabled ancestors, and MuMu's ADB
not creating empty fixture directories. The driver was corrected to navigate
normally, inspect ancestors and explicitly create owned empty directories. The
More menu can scroll; the driver scrolls it to reach Preferences.
The archive-content save check found that search matches launched the editor
without requesting its result. This was corrected to use the established archive
result handler (request 757) before retesting actual ZIP publication and backup.
The regex driver initially used the wrong checkbox label. One save-back retest
also lacked a fresh MuMu UIAutomator dump before the editor opened; the guard now
retries only missing/malformed dumps while retaining foreground-package checks.

No Shizuku service or biometric hardware was enabled. No phone permissions were
changed. Clipboard state intentionally ends with the app process; the clipboard
contains paths rather than private copies of the selected data.
