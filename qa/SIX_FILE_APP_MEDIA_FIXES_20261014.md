# Six app, backup, media and comparison fixes

Source changes assisted by OpenAI Codex. No build, compilation, installation,
JUnit execution or device test was performed. Previous workspace edits were retained.

## Implemented behavior

1. **Quick Edit launcher icon:** picking PNG/WebP/JPEG decodes a bounded image and
   previews it on the Change icon button. Done adds a dedicated drawable resource,
   updates application icon/roundIcon and explicit activity icons, then applies the
   existing signing preference. Cancel leaves the APK unchanged. Quick Edit works
   on a sibling temporary APK and publishes after editing/signing; original backup
   and V4 sidecar handling are retained. Density, vector, alias and adaptive launcher
   resources no longer cause a guessed PNG filename to hide the replacement.
2. **Split bundle icons:** APKS/XAPK/APKM/ASPK rows receive the APK fallback tile
   immediately, then load the base APK icon asynchronously. Base-master and density
   resource splits are handled. Bitmap pixels are detached before deleting temporary
   APKs. Reads are bounded (256 MiB base, 32 MiB per density split, 128 MiB density
   total, at most eight density splits); unavailable previews keep the fallback.
3. **APK Extractor package/data:** each app's menu has Package and data details.
   The dialog shows actual package/data/device-protected paths, app/data/cache sizes,
   and directory navigation. Android StorageStats supplies sizes when Usage access
   is available; readable files or elevated access provide a fallback. Inaccessible
   sizes display Unavailable. Usage access and elevated access remain subject to
   Android's permissions; this change does not bypass private-directory restrictions.
4. **Xiaomi backups:** `.bak`/`.ab` files are checked for MIUI/Android backup headers.
   Unencrypted, compressed/uncompressed Android envelopes and MIUI raw TAR payloads
   become a cache ZIP for browsing/extracting. Ordinary `.bak` copies retain Restore.
   The original backup is never rewritten. Encrypted backups require prior decryption;
   malformed/unsupported headers fail explicitly. Temporary output is removed on
   failure; unsafe paths, duplicate entries and excessive expansion are rejected.
   Symbolic/hard links are skipped in the generated view. Repacking an edited ZIP
   as a Xiaomi-restorable backup is outside this change.
5. **Folder video navigation:** playback builds the complete same-media-type queue
   once. Browser launches carry the pane's displayed order; other launches use stable
   filename order. Next no longer adds one unsorted sibling then stops. Rotation
   does not restart the originally opened video. Manual Next works in repeat-one;
   shuffle starts from the selected item and queue mutation rebuilds its indices.
6. **Same-pane comparisons:** selecting exactly two entries in one pane now offers
   applicable text/hash/ZIP/ARSC/APK/DEX comparisons. The pair uses the pane's row order.
   Selecting one entry per pane and multi-DEX groups retain their existing behavior.

## Source validation

- Parse changed Java sources with tree-sitter's Java grammar.
- Parse changed XML, check duplicate string names and added string references.
- Inspect new Android SDK calls and the bundled REAndroid resource APIs read-only.
- Run `git diff --check`.
- Add unrun AndroidBackup JUnit cases for ordinary `.bak`, compression, MIUI prefixes,
  raw TAR, malformed input and explicit encrypted-input rejection.

Backup format references: [Android backup extractor's envelope reader](https://github.com/nelenkov/android-backup-extractor/blob/master/src/org/nick/abe/AndroidBackup.java)
and [MIUI header support](https://github.com/nelenkov/android-backup-extractor/pull/79/files).
Implementation is original project code; no upstream code was copied.

## Device checks pending build authorization

- Change launcher icons in raster, vector and adaptive APK fixtures. Cancel first,
  then save with signing disabled/enabled and V4 enabled. Verify launcher and round
  icons at multiple densities and original/backup hashes after a failed save.
- Browse uppercase/lowercase APKS, base.apk/base-master layouts, density splits,
  missing icons, malformed bundles and quick row recycling in light/dark themes.
- Open Extractor details for split and ordinary apps. Verify package/data paths,
  Usage access grant/denial, directory navigation in an existing browser, and root/
  Shizuku modes. Confirm denied sizes never masquerade as zero-byte directories.
- Open owned compressed/uncompressed MIUI and raw-TAR fixtures, extract an entry,
  compare hashes, and confirm the source `.bak` remains unchanged. Exercise ordinary
  restore, encrypted/truncated headers, cancellation and insufficient cache storage.
- Play three or more videos from the first/middle row, tap Next repeatedly, use
  Previous, rotate, and test repeat/shuffle. Include uppercase extensions and audio
  files mixed with videos; compare playback order with the pane's displayed order.
- Select two text, binary, ZIP, ARSC, APK and DEX files in the same pane. Verify each
  applicable comparison uses both selected sources and cross-pane comparison still works.
