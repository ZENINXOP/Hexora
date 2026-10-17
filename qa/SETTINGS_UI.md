# Settings UI refresh

The main Settings screen now has search, a directly accessible Light/Dark selector,
and seven compact, expandable categories. Installed plugin preferences use the
same section layout. Search temporarily opens matching sections; clearing it
restores the user's expansion choices. Search and section state survive activity
recreation, including theme changes.

Controls use consistent outlined fields, spacing, readable theme colors and
48dp or larger interactive targets. Root and Shizuku availability use read-only
status badges. Signing controls stack vertically so they do not overlap. Folder
paths can wrap onto two lines beside compact Change buttons.

Validation performed without building:

- Preserved all 53 pre-existing view IDs while reorganizing the layout.
- Parsed the eight changed/new XML resources and resolved their referenced
  styles, strings, colors, layouts and drawables against the app and cached
  Material 1.12 resources.
- Parsed the three changed/new Java sources with the Java tree-sitter grammar.
- Checked category structure, unique IDs, status badge/controller types,
  two-theme selection, and removal of the overlapping signing FrameLayout.
- Checked text/accent contrast for the light and dark settings surfaces and
  status badge states.
- `git diff --check` passed.

No Gradle task, Java compilation, APK packaging or device test was run.

On-device verification remains necessary: search for a control, clear search,
expand sections, switch themes, rotate the screen, use a large system font,
change a preference and reopen Settings, exercise folder/font pickers, verify
root/Shizuku status, and check plugin settings when plugins are installed.
