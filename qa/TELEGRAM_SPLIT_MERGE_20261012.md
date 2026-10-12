# Shared attachments and single-APK conversion — October 12, 2026

AI-assisted implementation and validation: OpenAI Codex. Changes remain local;
no commit or push was performed. The existing SDK 28 choice is preserved.

## Delivered APK

`qa/dist/Hexora.0.1.2-telegram-split-fix.apk` is installed in the owner's MuMu
instance (`emulator-5554`, Android 15 / API 35). This is the same-debug-certificate
update to the existing Hexora installation; app data was retained. The vivo was
not connected for this task, and the owner explicitly selected MuMu testing.

Hexora 0.1.2, version code 13, compile SDK 36, target SDK 28. Distributed APK and
installed base APK have identical SHA-256:

`9dba52377077e4b3ce74bd1da9b5b476dee8a022c1d99b28c799303ed5863ad4`

APK signature verification passes V1/V2. The temporary share fixture is an
opt-in sample, excluded from the manager APK and removed after validation.

## Telegram/shared-file fix

The screenshot's `not a local file: content://...` came from `LocateActivity`
requiring every attachment to resolve to an existing filesystem path. Telegram
can grant access to one file through its provider without granting access to
the Android/data directory containing it. Hexora now reads content URIs with
`ContentResolver.openInputStream` while that temporary grant is active, following
the [Android shared-file contract](https://developer.android.com/training/secure-file-sharing/request-file).

- Content attachments are copied into Hexora's private **Shared files** folder
  and displayed in the manager. They retain their display name and extension;
  duplicate names produce separate copies. Edits apply to this copy, preserving
  the sender's original. Local `file://` paths retain the locate workflow.
- VIEW, SEND, SEND_MULTIPLE, and ClipData are supported, with duplicate URIs
  removed. Provider names are reduced to safe filenames. Copying is streamed,
  checks disk budget/interruption, and removes incomplete copies on failure.
- Missing files and missing URI permission show a readable retry message.
- Root mode no longer uses cached privileged listings for Hexora's own private
  directories. That cache had hidden newly received copies from the browser.
- The receiving activity appears as **Hexora** in Android's chooser.

Validation uses an owned private provider with no `_data` column, temporary
read grants, and a Telegram-shaped path containing `Android/data/.../Telegram
Files/277449322868.pdf`. The actual Telegram app/account and the user's PDF
were not accessed; live Telegram behavior on the vivo remains untested.

## Merge to single APK

- Tap XAPK/APKS/APKM/ASPK → **Merge to single APK**. The action is also available
  from the bundle's context menu and Open with.
- For loose splits, select the base APK and matching split APKs, then long-press
  → **Merge to single APK**. An APK-containing folder can also be merged through
  its context menu.
- The dialog keeps split selection, device filtering, compression, signing,
  and native-library options and adds an output-folder chooser. Manually changed
  checkboxes remain authoritative after device filtering.
- Nested bundle members are flattened into a private working directory; only
  APK entries are extracted, and duplicate basenames do not replace each other.
  Exactly one base APK and matching package/version are required.
- New output is published after merging and the selected signing step, with
  a fresh filename. Source bundles/APKs remain unchanged. Optional V4 sidecars
  follow the final output name.
- **OBB expansion data stays separate.** The dialog explains this; a generic
  merged APK cannot replace every game's external expansion-data requirement.

## Validation

Build and tests: `gradlew.bat -PshareFixtures=true :app:assembleDebug
:app:testDebugUnitTest :samples:share-fixture:assembleDebug --offline`.
All **169 JVM tests** pass, with zero failures/errors/skips. New tests cover
filename containment, preserving earlier same-name bytes, incomplete streams,
disk-budget failure, nested/uppercase APK members, selection, absent APKs,
and duplicate basenames. Touched tracked files pass `git diff --check`.

**16 native MuMu checks** pass across the retained logs:

| Scenario | Result |
| --- | --- |
| VIEW text grant → exact imported bytes and editor contents | PASS |
| SEND text grant → exact imported bytes and editor contents | PASS |
| ClipData-only grant → exact imported bytes and editor contents | PASS |
| SEND_MULTIPLE → two separate received files | PASS |
| Telegram-shaped PDF URI → original filename and exact PDF bytes | PASS |
| Missing URI permission → friendly error, no added file | PASS |
| Missing provider file → friendly error, no added file | PASS |
| Nested uppercase XAPK → signed standalone APK; unchanged source | PASS |
| APKS context-menu merge → signed standalone APK; unchanged source | PASS |
| APKM merge → signed standalone APK; unchanged source | PASS |
| Loose split folder → merged APK in Download/Hexora | PASS |
| Selected loose APK files → one new merged APK | PASS |
| Missing base APK → explicit rejection, no final APK | PASS |
| Unrelated apps → explicit package/version rejection | PASS |
| Chosen output folder → actual APK saved in that folder | PASS |
| Merged APK installation/launch → split English resource displayed | PASS |

The installation check uses an owned base APK plus a real binary config split
containing an English resource. `aapt2 dump resources` confirms both base and
English configurations in the output; the installed app displays
`RESOURCE: SPLIT_RESOURCE`. This checks resource merging, not just a renamed ZIP.
AndroidRuntime's captured error log is empty.

Evidence: `qa/artifacts/share-merge-20261012/`, including `runtime-final.log`,
`resource-final.log`, `additional-final.log`, `output-folder-final.log`, `multi-final.log`,
`acceptance.json`, snapshots/XML, source fixtures, and copied output APKs.
Earlier failed attempts remain visible. They identified the root-list cache
bug and a Windows rename incompatibility in the initial copy helper; both were
fixed. Driver-only issues were fixture task reuse and unnecessary quoting for
ADB exec-out. The first resource probe lacked its English resource because
AAPT2 removed configuration-only entries; the fixture was corrected, and actual
merged-resource installation was then verified successfully. One multi-file
run failed because MuMu produced no fresh UIAutomator XML; the full multi-file
case was rerun and both displayed names and exact file bytes passed.
