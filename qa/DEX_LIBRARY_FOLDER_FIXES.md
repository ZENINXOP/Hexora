# DEX selection, library text, and Android package folder icons

Source changes by OpenAI Codex. No builds, compilation, automated tests, or
emulator actions were run for these changes, at the owner's request. The
previous QA APK does not include them.

- Tapping a root `classes*.dex` entry in an APK now opens the existing MultiDex
  selector. The tapped entry is selected initially; individual entries and
  Select All remain available. Listing includes nonconsecutive DEX numbers.
  Selected DEX results retain the source APK and publish only selected entries.
- Library text is decoded through EOF into a temporary UTF-8 cache on a worker.
  The viewer loads bounded pages instead of stopping at the old preview limits.
  Previous/Next and tapping the page counter allow access to the entire decoded
  text. Page position survives tab switches and session recovery. Binary control
  bytes remain hidden and undecodable bytes use replacement characters; this is
  a read-only text representation, not an executable decompiler or hex view.
- Package folders directly under Android/data, Android/obb, and Android/media
  keep the folder artwork and add the installed application's icon as a small
  lower-corner badge, loaded asynchronously with caching and guards
  against updating a recycled row. Uninstalled or inaccessible packages retain
  the standard folder icon. Both themes use the same installed app artwork.

## Additional source changes

- The DEX selector uses wide checkbox tiles side by side, with a selected count,
  Select All / Invert Selection, and a disabled confirmation when none are selected.
- Folder listing tries direct access, then saved system folder grants, then
  existing elevated access. A system folder-picker option is offered before the
  Shizuku setup flow. Grants are checked against the requested path and persisted.
  They support in-app folder navigation, staged file viewing, text/hex write-back
  to existing writable documents, and provider-supported deletion. Staged APK
  navigation retains its original folder rather than exposing the cache parent.
- Android/data and Android/obb permission restrictions still apply. This uses
  system-issued grants where a device permits them; it cannot grant access on a
  device whose file picker rejects those locations. Root/Shizuku remain available.
  See [Android's storage documentation](https://developer.android.com/training/data-storage/shared/documents-files).
- Progress dialogs and hidden-operation notifications show completed and remaining
  percentages to two decimal places, using long counters for large files.
  Local copying reports transferred bytes; moves also count verification and
  source deletion. ZIP work follows the library's progress monitor. Archive
  extraction counts decoded bytes and entries before extracting, including ZIP,
  7z, RAR, TAR, and gzip/bzip2/xz. TAR and standalone compression streams need a
  counting pass; the dialog identifies that preparation step.
- Local recursive deletion counts actual completed files/directories. Elevated
  shell deletion reports completed selected targets because the shell command
  does not supply nested-entry callbacks. An unreadable total stays indeterminate;
  a percentage is not fabricated. These are percentages of measurable work,
  not estimates of time remaining. Publication/signing/cleanup can hold the
  operation below 100% until success. Live FTP transfer progress is unchanged.

No build, test, emulator interaction, Git commit, or push was performed for this
follow-up. Prior runtime test results do not validate these new changes.

Changes remain local and require a new build before manual runtime validation.
