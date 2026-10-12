# Editor loading and session changes — 2026-10-10

> Updated runtime evidence: [October 11 build and MuMu checks](RUNTIME_20261011.md).
> The source-only status below records October 10.

Status: implemented in source, **not built or installed**. The user requested no further builds.
Java/XML parsing, new resource references, bundled Sora API inspection, and diff whitespace checks passed.
Fourteen new JUnit regressions were added in `EditorDocumentReaderTest`; they have **not been executed**.
Earlier APK/native-test results do not validate these changes. The existing APK SHA-256 remains
`649146dfa9cc5ba894e37d8ef43b74bdc8fdbad8007bbe0bc1bc04efa71f6ad2`.

## Behavior

- One cancellable document-loading worker reads and prepares Sora Content before attaching it to the editor. Stale loads cannot replace a newer tab; loading/failed/partial documents cannot be saved.
- Source text above 256 Ki characters, 10,000 lines, or 4,096 characters on a line uses a lightweight editable mode. Syntax analysis, completion and wrapping pause; undo history is bounded. Opening a small file restores normal preferences.
- Full editing is limited to 4 Mi characters (lower on low-memory heaps), 80,000 lines, 32,768 characters per line and known file sizes up to 8 MiB. Beyond these limits, an explicit read-only prefix is shown: at most 64 Ki characters, 2,000 lines and 2,048 characters per line. This is a safety preview, not a complete large-file text editor.
- Binary contents (including typical `.lib`/`.so` files) get a bounded decoded text preview and a Hex editor action for direct files. Text classification uses actual bytes rather than the extension. Content-provider and archive previews also stay protected; archive edits continue through their existing text-save return path.
- CR/LF/CRLF and the absence of a final newline are preserved. UTF-8/UTF-16 BOMs and detected encoding survive saving.
- Clean inactive file tabs release their text buffers. Session serialization and recovery writes happen in a worker. Rotation retains current buffers in memory.
- Only explicit **Minimize** permits restoring the open-file list on a fresh launch. Back/Close ends the session and clears recents. Unsaved tabs offer Save and exit / Do not save / Cancel. DEX Back closes the active class through the existing unsaved-change prompt.
- Save starts disabled/dimmed, becomes active for modifications, dims after successful save, and dims when undo or equivalent editing returns to saved text. Equal-length dirty comparisons run after a short debounce in a worker. The DEX toolbar Save reflects the existing modification state too.

## Native acceptance checks pending a user-authorized build

1. Open a small Python/XML file; confirm normal syntax and saved wrapping preference. Save is dim. Type, save, then undo back to saved contents; check Save state and disk bytes.
2. Open a 1–3 MiB multiline text file, scroll/edit/undo/save, and watch responsiveness and AndroidRuntime/ANR logs. Repeat with wrapping previously enabled, then open a small source file to verify normal settings return.
3. Open a 100 MiB text file, a 100 KiB single-line file, and a file exceeding 80,000 lines through both local paths and a content URI. Expect a labelled bounded preview with disabled Save/read-only override. Hash originals before/after attempted edits.
4. Open a real ELF `.so` and binary `.lib` using Text editor. Expect decoded text rather than hex columns and full Hex editor access; a text `.lib` must still open as text.
5. Open large A, immediately switch to B, reload/close/back during loading, and rotate with unsaved changes. B must never show/save A or empty loading contents. Initial document load must not appear in undo history.
6. Open A/B, modify one, minimize and resume: tabs/buffers remain. Exit via Back and relaunch: old recents disappear. Cancel an unsaved exit: tabs remain. Save and exit must process all modified tabs, retaining the window if any save fails.
7. Rotate with large unsaved text; reopen after Android process death only for a minimized session. Settings/Hex navigation must not be mistaken for an explicit editor exit.
8. DEX: Back closes a clean class; modified Back prompts; Cancel keeps the class usable. Save brightens after edits and dims after save/undo. A failed Smali save must keep the class and changes open.

AI-assisted implementation: OpenAI Codex. Upstream notices remain unchanged.

## October 11 library follow-up

Libraries now show the Open with chooser each time, following the owner's
correction to the initial automatic-routing implementation. Text editor shows
decoded characters and line breaks, without hex offsets/byte columns. Binary
previews remain bounded and read-only; attempted typing cannot enable Save or
alter the original. Readable UTF-8/UTF-16 libraries remain editable. The worker
still performs full content/size checks before allowing editing. Native 32 MiB
preview and encoding/save evidence is in the updated
[runtime report](RUNTIME_20261011.md).

## MT text-view follow-up

The same owned x86_64 library was opened in the installed MT Canary Text Editor
for comparison. Binary padding/control bytes are now hidden, preserving newlines,
tabs, printable text and replacement characters from invalid decoding. The input
limit counts skipped controls too, so all-zero streams cannot bypass the bound.
Binary text is wrapped to the viewport; it remains read-only and uses the existing
64 Ki decoded-input, line and line-length guards.

The editor has a compact filename/caret/encoding row, a toolbar Minimize pin,
subtle light/dark gutter and default symbol shortcuts. Preview details move to
the status row for binary documents. Symbol shortcuts are disabled for previews;
editable text retains selection replacement, Undo and dirty Save behavior. Custom
editor palettes and bottom-bar tools remain available. Native outcomes and
screenshots are recorded in the current runtime report.
