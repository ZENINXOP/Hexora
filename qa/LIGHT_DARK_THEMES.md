# Light / Dark themes and sidebar toggle

AI-assisted contribution: OpenAI Codex.

- Only bundled Light and Dark palettes are selectable. Legacy Black resolves to
  Dark; System default resolves once from the device's setting and is persisted.
  Legacy style/boolean preferences stay synchronized for older code paths.
- Settings and the common chooser use the same selection API. Removed the
  independent text/DEX palette choices so editor surfaces follow the app.
- The compact sidebar header contains a 48dp theme action beside overflow.
  Dark shows a sun (switch to Light); Light shows a moon (switch to Dark).
  The 260ms sun/crescent animation uses ValueAnimator and honors system animation
  duration scaling. Repeated taps are ignored while animating, and teardown
  cancels the animator without changing preferences.
- Activity recreation is owned by AppCompat. Theme changes are prevented during
  active file tasks or while an editor is minimized to preserve ongoing work.
  The old main activity cannot overwrite a newly chosen theme during onPause.
- Defined matching foreground/background pairs for primary buttons, checked
  choices, container surfaces and secondary controls in both palettes.

Source validation on 2026-10-17: all 11 edited/new theme XML resources parse;
no duplicate layout IDs or style attributes; settings contains exactly two
choices; removed editor preference references are absent; theme menu placement
and animation/state guards checked. Twelve text/control contrast pairs pass
4.5:1 (lowest checked ratio: 4.59:1).

No builds, compilation or JVM tests were run for this change, as requested.
Animation rendering, recreation behavior and device UI remain unverified at runtime.
