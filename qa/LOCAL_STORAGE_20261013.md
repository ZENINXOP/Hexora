# Added local storage through Android folder providers

Implementation assisted by OpenAI Codex. Builds, compilation, APK installation
and runtime tests remain disabled at the user's request.

The sidebar overflow menu now offers **Add local storage**, followed by Organize
sidebar and Settings. Android's system folder picker supplies the provider list
and **Use this folder** confirmation, as in the supplied MT Manager screenshots.
Selecting a folder creates a persistent entry under Storage and opens it in the
selected pane. Selecting the same tree twice reuses its entry.

The entry remembers its granted URI and provider label across restarts. Long-press
it to open or remove the entry. Removal keeps the provider's files and shared grants.
Locations use an internal virtual namespace, while the location header displays
the selected folder's name and relative path. Root navigation stays inside the
selected folder. Android 5.0 or newer is required for the folder picker.

Provider folders support browsing, file viewing, text/hex editing through staged
copies, backups, file/folder creation, rename, deletion, and copy/move with local
folders or another selected provider. Moves verify file bytes and directory names
before source deletion. Available operations depend on the provider's grants and
document capabilities. Provider-revoked or unavailable locations report an error.
Transfers between overlapping provider entries require an overlap check; providers
that cannot perform that check must use an ordinary local folder in between.
Existing tools that require direct filesystem paths may still require a local copy.
Duplicate provider filenames are rejected rather than risking the wrong document.
Viewer copies use the managed incoming cache and its existing recovery-aware cleanup.

Source validation: Java parsing with tree-sitter, XML parsing/resource checks,
Android SDK API signatures inspected with read-only `javap`, and `git diff --check`.
Added an unrun JUnit case that verifies a directory using provider-returned file
metadata and streams without assuming its paths exist on the physical filesystem.

## Device checks when building is authorized

1. Open sidebar overflow → Add local storage. Pick an ordinary folder, then a
   folder from Android Code Studio or Termux. Cancel the picker in a separate case.
2. Confirm the folder appears under Storage, opens in the selected pane, and keeps
   its entry after activity recreation and process restart. Re-add the same folder.
3. Browse nested/empty folders, use `..`, Back/Forward and pane synchronization.
   Verify the location header shows the folder name and remains within the grant.
4. Open text, image and archive files. Edit/save text and hex with backups enabled;
   confirm the provider receives the changed bytes and a `.bak` where writable.
5. Create a file and an empty directory. Rename and delete selected children.
   Verify unsupported/read-only operations fail without deleting existing files.
6. Copy and cut/paste nested folders and binary files in both directions between
   an ordinary local folder and a provider, and between two providers. Compare
   hashes and empty directories. Exercise collision, cancellation and overlap cases.
7. Revoke the system grant or uninstall the provider. Check a clear failure,
   then remove/re-add the entry. Removing an entry must preserve all provider files.
8. Compare the overflow and saved-storage rows in light/dark themes against the
   supplied references. No claim of exact visual parity has been made.
