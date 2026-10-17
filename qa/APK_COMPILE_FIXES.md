# APK compilation and DEX toolbar fixes — 2026-10-17

AI-assisted contribution: OpenAI Codex. Existing upstream notices are retained.

The report described errors/crashes during APK builds without a failing APK,
stack trace, or reproduction steps. The following concrete source defects were
identified and corrected; reproducing that particular user's crash remains unverified.

- The selectable JF Smali folder compiler called a source-text parser with the
  directory path. It now invokes the directory assembler and checks its result.
- DEX format numbers were passed as Android API levels to the source parser.
  Lexer, parser and walker now use the opcode API for the selected DEX format.
- Folder rebuilds preserve the cached DEX format and only publish a validated
  successful cache file. Failed compilation retains the previous file and stamp.
- APK build dispatch now recognizes binary, XML and JSON manifests, including
  resource-only APKs, instead of guessing the format from a res directory.
- APK folder builds use staged output, alignment checks, cancellable workers,
  cleanup on failure, and handled memory/linkage errors.
- DEX saves write final output and deferred method code to disk, avoiding
  multiple full-file heap buffers. Each selected DEX keeps its own source path
  and format. Failed saves retain pending Smali and the original file.
- DEX compilation no longer leaves an endless worker Looper behind. Cancellation
  interrupts workers, concurrent compile taps are ignored, and failures reset
  the UI state. Compiler pools are bounded and shut down on failure.
- File replacement uses direct rename where supported and recovery/rollback
  where storage refuses replacing an existing file.
- Removed the Classes / Navigation / Code theme button row and its height from
  the shared DEX layout. Code theme remains accessible in the editor popup.

Validation: `:app:testReleaseUnitTest --offline --console=plain` passed all
233 tests (16 new), with zero failures/errors/skips. Coverage includes both Smali
libraries, cached format preservation, invalid input, a 40,000-instruction method,
disk-write failure, cancellation, rollback and mixed-format/mixed-directory saves.
The layout XML and `git diff --check` passed. JVM tests run on Windows; Android
runtime UI, a full APK resource round-trip and the originally reported APK were
not exercised in this change. No new release APK was packaged or published.
