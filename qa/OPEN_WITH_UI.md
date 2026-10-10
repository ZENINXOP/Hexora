# Open with and file icon update — 2026-10-10

Status: source changes only. No Gradle builds, Java compilation, packaging, installation, or Android runtime tests were run for this update, per the user's instruction.

The supplied MT Manager screenshots establish the reference: white symbols in colored rounded tiles, captions beneath, three columns, Search/Info in the header, and a Type popup above the footer. The new chooser follows that visual structure while keeping Hexora's light/dark theme colors and supported handlers. It does not add unavailable MT-specific tools.

## Changes

- Shared vector tile rendering for built-in tools and browser fallback icons. Text/XML blue, archive/Java brown, image cyan, music red, video orange, DEX teal, ARSC amber, APK green, fonts purple, and hex/signatures blue-gray. Source formats retain readable identifiers on document marks; Java uses a cup. Real APK icons and media thumbnails remain supported.
- Scrollable RecyclerView grid with no item-change animation. Two columns on narrow screens or large font settings. Dialog height follows the number of built-in rows, with a screen-height cap. Tool captions may wrap.
- Search filters the current list. Type offers Built-in, Text, Image, Video, Audio, All; explicit MIME categories discover compatible installed applications in a worker. All includes built-ins. Package visibility declares VIEW `*/*` for Android 11+.
- MIME details and the existing actual-MIME preference sit behind Info. More keeps the existing detected-MIME app list and default-app controls. Existing archive/root/editor routes and script confirmation are retained.
- Background app requests are canceled/invalidated when switching types or dismissing/detaching the chooser. Tile drawables own independent bounds and callbacks per file row. AppCompat vector loading and RectF drawing preserve the API 19 rendering path.

## Verification

- Java syntax parsing passed for six affected Java files; eleven XML files parsed successfully.
- App resource references resolve; default string names are unique; `git diff --check` passed.
- Read-only inspection of Material 1.12.0 confirms the dialog inset APIs used by the chooser.
- Existing APK SHA-256 remains `649146dfa9cc5ba894e37d8ef43b74bdc8fdbad8007bbe0bc1bc04efa71f6ad2`.
- [Source-check record](artifacts/open-with/source-checks.json).
- [Design preview](artifacts/open-with/design-preview.html) / [PNG](artifacts/open-with/design-preview.png), rendered from the actual vector paths for visual inspection. This is a design preview, **not a native Android screenshot or runtime verification**. Compared with the supplied reference: colored tiles, symbol silhouettes, caption placement and Type popup follow the reference; Hexora uses its own palette/surface and fewer supported tools per file.

## Native acceptance checks pending an authorized build

1. Compare the chooser and file list against the references in light/dark/black themes; check phone, landscape, large font, long labels and archive entries.
2. Open Text, Hex, Archive, Image, Music, Video, XML, DEX, ARSC, APK info, Font, Signature and Script options for matching files. Confirm scripts retain their confirmation dialog.
3. Toggle Search/Info; clear the query; test no matches and keyboard resize. Check footer access with both panels expanded.
4. Switch categories rapidly, dismiss while loading, rotate, reopen, and check logs for exceptions/window leaks. Old results must not appear after switching categories.
5. Verify MIME category discovery on Android 11+, app launches/read URI grants, no-apps state, and More's default-app actions. Repeat for a staged archive entry and protected/root file.
6. Scroll/scale both browser panes, confirming distinct file type marks, independent icon bounds, correct asynchronous thumbnails and accessible labels.

AI-assisted implementation and source review: OpenAI Codex. Upstream notices retained.

## PDF icon follow-up

PDF files now use the shared folded-document tile with a red background and a
readable PDF label. The browser, archive entries, FTP list and file picker use
the same mark, including uppercase `.PDF` filenames. This replaces the small
boxed glyph and the picker's monochrome PDF icon. Both browser and picker row
bindings invalidate previous thumbnail requests so a delayed image or APK icon
cannot overwrite a PDF icon after scrolling.

Java syntax parsing and whitespace checks passed for this source-only change.
No build, compilation, packaging or device UI verification was performed.
