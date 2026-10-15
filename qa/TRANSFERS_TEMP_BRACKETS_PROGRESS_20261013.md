# Transfer, temporary import, bracket and progress fixes

Source changes assisted by OpenAI Codex. No build, compilation, APK installation,
JUnit execution or device validation was performed, as requested.

## Changes

- Clipboard batches refresh the browser once, rather than refreshing both folder
  lists for every pasted file. Copy totals are cached for the operation.
- Regular moves try direct and elevated rename before copying. Folder sizes are
  counted only when a regular move actually needs a copy fallback.
- Local copies use bounded FileChannel transfers with a stream fallback when the
  channel returns zero. Provider streams still work. Temporary publication,
  cancellation, source-change checks, sync and cross-volume byte verification remain.
- Shared content and image imports use `cache/shared-incoming`. Imported copies
  are deleted after the last activity closes and active progress work finishes.
  Startup also removes leftover imports, including the old private `Shared files`
  folder. Recoverable text editor sources/archive parents and background playback
  files are preserved. Sender files and copies exported outside the import folders
  are unaffected. Android can reclaim temporary cache under storage pressure.
- Normal text/code analyzers publish indexed bracket pairs, including plain text.
  Syntax-aware pairing excludes strings/comments and quoted JSON keys. Brackets
  have explicit light/dark colors. Version checks reject pairs from older edits.
  Existing lightweight-document protections remain in place.
- Each progress operation has its own notification and restore intent. Tapping it
  reuses the owning activity and hidden dialog. Notification updates are limited
  to two per second; missing notification permission keeps the dialog accessible.

## Validation completed without building

- Changed Java sources parsed using tree-sitter's Java grammar.
- New editor calls checked against the bundled Sora AAR using read-only `javap`.
- `git diff --check`.
- Added JUnit coverage for multi-chunk channel byte/progress accuracy, provider
  stream fallback and cancellation after the final data chunk. Tests are unrun.

## Device checks when builds are authorized

1. Copy 2,500 small files and a large binary through clipboard and between panes.
   Compare bytes and elapsed time; verify pane refresh happens once for a batch.
2. Cut a deeply nested folder within one volume. It should rename promptly without
   a recursive counting phase. Cross-volume moves must verify bytes before deletion.
3. Hide an ongoing copy, tap its notification, hide it again, and repeat. Check the
   original progress dialog returns without a second browser/editor instance.
   Concurrent operations must retain separate notifications. Also deny notifications.
4. Open APK/archive/image content from Telegram. Close viewers and the browser,
   wait at least five seconds, then inspect the managed import folder. Repeat with
   canceled imports, app process death/relaunch and leftover legacy imports.
5. Minimize an edited shared text document, rotate, and relaunch after process death.
   Verify recovery still works. Explicitly copy/save a file to Downloads and ensure
   cleanup preserves that exported copy. Background media playback must continue.
6. In JSON, Python, JavaScript and plain text, put the caret before/after either
   bracket in nested `()`, `[]`, `{}`. Check both ends in light and dark themes,
   LF/CRLF, strings/comments, quoted keys, malformed nesting and edit/undo/tab switch.
