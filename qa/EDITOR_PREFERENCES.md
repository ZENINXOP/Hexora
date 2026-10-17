# Editor preferences: behavior and UI

Source-only change; no build or compilation requested or performed.

## Fixed behavior

- Font size, line numbers and wrapping use the same preference keys as the
  editors. Existing prefixed settings migrate once, so an older value no longer
  silently overrides a later choice in Preferences.
- Font and tab values are bounded. Tab width is now a list from 1 through 16,
  rather than an unchecked text field that can crash the editor on return.
- Indent-with-spaces and automatic indentation are connected to the editor and
  its language implementation. The text editor's tab shortcut follows this
  choice as well. Preference updates preserve the current document and undo
  history; the language wrapper is installed when a language is created.
- Syntax highlighting applies to text, Smali and Java views. Imported grammars
  follow the app's Light/Dark palette. Large/read-only document safeguards remain.
- Syntax import opens the system document picker, validates a TextMate JSON
  grammar with scopeName/patterns/fileTypes, limits it to 1 MiB and atomically
  stores it in app-private files. Invalid imports retain the last working file.
  An installed grammar can be replaced or removed; matching text files use it
  automatically. The reserved built-in Smali grammar is not replaced.
- The shortcut manager edits its existing list instead of stacking replacement
  manager dialogs. Tap and long-press-only buttons are supported, custom clipboard
  shortcuts are honored, blank labels fall back to the action name, regex patterns
  are checked before saving, and editing shortcuts respect read-only documents.
- Selection-menu defaults are shared with its editor. Invalid configurations are
  repaired, duplicate IDs are removed, recycled checkbox listeners are detached
  before binding, and the live menu reloads saved changes when next opened.

## UI

Editor preferences have a visible back toolbar, three named sections, consistent
rows, Material switches, themed choice dialogs, readable summaries, outlined
shortcut fields and themed edit/delete icons. The selection-menu editor has a
readable toolbar, persistent instructions and flexible-height rows so labels do
not overlap their checkboxes at larger font sizes.

The Selection menu screen explicitly tints its final toolbar back icon, action
icons, checkbox outlines and checkmarks from the active Light/Dark palette.
Hidden actions retain readable muted labels; dragging a row uses the theme's
selection background and clears it on release/rebind. Editor preference choice
dialogs use an explicit surface/text/control overlay as well.
During release validation, a screenshot exposed white row backgrounds after a
Light-to-Dark switch despite correct label colors. An obsolete layout-v21 row
was overriding the shared themed layout; that duplicate is removed. Backgrounds
also resolve from the activity theme on binding and drag-state changes; see
`RELEASE_0.1.4.md` for release build/device evidence.

## Source validation

- Java tree-sitter syntax parsing; XML parsing and reference resolution.
- Canonical preference keys, numeric entry/default consistency, toolbar widget
  IDs and switch widget/controller compatibility checked in source.
- Inspected the bundled Sora language/indentation APIs and cached Material APIs
  without compiling code.
- `git diff --check`.
- Selection-menu Java/XML syntax and cached Material checkbox/style resources
  checked; normal/dragged labels and checked-box marks pass 4.5:1 contrast in
  both palettes. Visual and touch behavior still require device verification.

Android/device verification remains pending. Exercise font/line-number/wrap
changes from text and DEX tabs, tab widths 1/4/16, spaces vs tabs, automatic
indentation on/off, highlighting on/off in text/Smali/Java, both themes, screen
rotation, and read-only/large documents. Add/edit/delete tap and hold shortcuts,
try invalid regex patterns, scroll and reorder selection-menu rows, then reopen
the live selection menu. Import `fixtures/editor-sample.tmLanguage.json`, open a
`.hxsample` text file containing `hello "world"`, then replace/remove the grammar
and verify that a malformed or oversized import preserves the working file.
