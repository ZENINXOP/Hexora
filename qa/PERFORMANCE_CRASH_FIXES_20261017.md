# Performance and crash-source audit — October 17, 2026

Status: source changes only. No Gradle tasks, Java compilation, APK packaging,
installation, or runtime tests were run. Existing APKs/logs do not validate these
changes. This report covers the fixes from this audit; the working tree also
contained other edits when the audit started.

AI-assisted contribution: OpenAI Codex. Upstream license notices are preserved.

## Implemented fixes

### File browsing and installed apps

- `FileSorting`: snapshot directory/type/size/date keys before sorting. Filesystem
  metadata calls now scale with entry count rather than comparator invocation
  count. Snapshots also prevent changing file metadata from violating the sort
  comparator contract. Directory-first and reverse ordering are preserved.
- `FileType`: index extensions once instead of cloning/scanning all enum values
  on every row bind.
- `MainActivity`: replace one folder-count thread per interaction with one
  latest-request worker; stop obsolete count loops and purge cancelled listing
  jobs. APK project-manifest probing runs with directory loading, off the UI thread.
- `SortFilterController`: debounce typing, filter on workers, cancel obsolete
  filters, and reject results for replaced adapters or destroyed activities.
- File-list select-all/invert/same-type changes use selection-only payloads,
  avoiding icon/metadata reloads. Navigation rows and invalid positions cannot
  enter selection. Batch image selection now indexes the adapter's parent-inclusive
  array, fixing the last-row out-of-bounds/wrong-file bug.
- Installed-app loading uses worker-local synchronized results and one UI
  publication, replacing concurrent writes into shared ArrayLists and per-app
  UI callbacks. Filters sort snapshots and preserve selected app identities.
  Click handlers resolve current adapter positions.
- Installed-app icons are loaded for bound rows through an 8 MiB bitmap cache,
  rather than retaining a full drawable for every installed package.

### Image viewing, editing and previews

- Viewer page decoding, sibling enumeration, resolution lookup and properties
  inspection run off the UI thread. Recycled rows cancel work; request tokens
  reject stale images and destroyed-screen callbacks.
- `BitmapPreview` bounds the longest decoded edge, including extreme aspect
  ratios and images beyond the previous sampling cap. Preview decoding handles
  malformed images and memory failures.
- Picker thumbnail queues and caches are bounded, use file-version keys, and
  clone drawables per row. File and APK icon work uses application contexts and
  weak target references; APK requests coalesce across panes and dialogs.
- APK and installed-package badge icons are rasterized to bounded pixels.
- Image-editor previews are sampled on a cancellable worker. Full-resolution
  transforms have a heap-aware budget and report insufficient memory rather
  than silently reducing saved resolution. Working copies survive configuration
  changes. Image paths are passed through intents instead of only a static field.
- Invalid image/layout dimensions no longer trigger an endless repost loop.
  PNG operations no longer require the JPEG native tool.
- Image compression and original-file publication stage output before replacement,
  so encoding/copy failure does not truncate the previous image.

### Hex editor

- `HexSearch` performs buffered linear-time forward/backward matching with unsaved
  byte overlays and cancellation checks. It opens its own file handle and reads
  64 KiB blocks, replacing per-candidate/per-byte seek/read loops.
- Byte addresses, edits and undo records use long offsets. Row addresses no longer
  wrap at 64 KiB or overflow signed 32-bit byte offsets. The RecyclerView-addressable
  range remains capped at its integer row-count limit (eight bytes per row).
- Row/status reads reuse an 8 KiB page instead of seeking for each displayed byte.
- Backups and grouped contiguous writes run off the UI thread. Dirty state is
  cleared only after local persistence and any elevated write-back succeed;
  duplicate saves and editing during save are blocked.
- Backup output is truncated correctly. Search/paste input sizes are bounded,
  and recycled empty cells discard stale touch positions.

### Text/DEX editing

- Cursor-position JSON loading/serialization/writes move to a serialized worker;
  duplicate positions do not schedule writes and retained positions are bounded.
  Restore callbacks wait for loading and avoid moving the caret after user input.
- Selection-length displays use cursor offsets instead of allocating the entire
  selected text. Smali method lookup reuses method-range results, invalidates
  equal-length edits, and holds text through a weak reference.
- DEX content snapshots are debounced rather than copying the full class at every
  keystroke. View disposal flushes complete buffers, not unfinished loading text.
  Smali snapshots do not overwrite a Java preview tab for the same class.
- Smali saves capture a stable UI-thread snapshot before compilation, block
  duplicate saves, restore editability on failure, and reject stale UI callbacks.
- Collapsing a class-tree package removes one contiguous range instead of
  repeatedly shifting the remaining ArrayList. Tree diffs skip move detection.

### Playback, memory and background callbacks

- File playback starts from lightweight metadata; title/duration metadata and
  sibling queue enumeration are prepared on a cancellable worker. Stop/track/queue
  changes invalidate pending enrichment.
- Service/player/mini-player album art uses shared, coalesced worker reads, sampled
  decoding and a 4 MiB cache. Artwork is never decoded in UI update callbacks.
- Playback session metadata is republished only when track/duration/artwork changes;
  periodic playback-state updates are throttled to one second.
- Playback callbacks tolerate registration changes during dispatch. A player is
  released even if stopping it while preparing would be invalid. Failed data-source
  setup releases its native player. Mini-player dismissal balances UI visibility
  and unregisters callbacks, including activity destruction.
- Android memory-pressure callbacks evict expendable thumbnail/APK/archive/artwork
  caches without recycling bitmaps that a visible view may still own.
- Error reporting prepares logs on a bounded worker and creates dialogs on the
  main thread with lifecycle checks.
- FTP callbacks capture their connection and pane request, preventing stale
  connections/listings from replacing a newer screen or dereferencing a cleared
  controller connection.

### Searches

- File traversal is iterative and retains canonical-path cycle detection.
  Name hits are capped at 500 and the UI reports this limit. Content reading has
  character, line-count and 64 Ki-character per-line bounds even for growing or
  virtual files with misleading lengths. Files exceeding content limits can be skipped.
- User regexes have expression-length, execution-work, time and interruption
  checks; regex stack exhaustion becomes an ordinary error. These are cooperative
  Java-regex safeguards, not a process-isolated regex engine.
- Cancel is connected to actual local/archive search workers. Stale or interrupted
  searches cannot publish results or overwrite the selected pane's status.

## Verification

Executed:

```text
python -B qa/performance_source_checks.py
git diff --check
```

The script parses changed/new Java with tree-sitter and XML with ElementTree.
It checks source syntax and an executor-type regression; it does not type-check
Android APIs or execute app behavior. The diff check checks whitespace.

Added JUnit cases (not compiled or executed):

- `HexSearchTest`: buffer boundaries, reverse scans, unsaved overlays,
  sparse-file offsets above 2 GiB, random binary cases and cancellation.
- `FileSortingTest`: one metadata read per entry and reverse/directory-first ordering.
- `FileSearchTest`: result limits, a 20,000-directory virtual tree, and oversized lines.
- `SearchPatternTest`: ordinary regex behavior, pathological regex bounds and cancellation.
- `BitmapPreviewTest`: panoramas, sampling boundaries and extreme dimensions.

## Runtime validation still required

No measured FPS, memory, ANR or crash-free claims are made for the modified source.
Runtime coverage must include rapid pane changes/filtering, large APK/image folders,
installed-app loading/filtering during refresh, image rotation/edit/save failures,
Hex searches across block/2 GiB boundaries, equal-length Smali edits/undo/save failures,
editor rotation while loading, rapid playback/stop/next/queue changes, missing or large
album art, notification restrictions, memory pressure and stale FTP callbacks.
Device-, codec-, storage-provider- and native-library-specific failures can remain
outside what source inspection can establish.
