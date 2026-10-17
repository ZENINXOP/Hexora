# Rename, destination conflicts and certificate runtime

AI-assisted source changes: OpenAI Codex. No build, compilation, APK packaging,
installation, JUnit execution or device validation was performed.

## Changes

- Rename tries the app's filesystem access, then a persisted folder grant, then
  the selected elevated backend. Ordinary shared-storage renames no longer go
  through Shizuku just because its mode is selected. Failed service renames now
  return a useful access error instead of an empty or stale service error.
- Single local-file rename, local copy/move, archive extraction to local storage,
  and external bulk-rename collisions offer Replace, Keep both, or Cancel.
  Copying to the same location offers Replace, Keep both and Cancel. Replace
  leaves the identical source/destination unchanged, before replacement staging
  or move-source deletion. Keep both adds
  a numeric suffix before file extensions; directory names retain their dots.
- Replacement holds the previous destination at a unique hidden sibling until
  success. Failure/cancellation restores it. If recovery cannot finish, its
  retained path is reported. Cross-volume moves compare the copy's bytes before
  source deletion, and source deletion starts after replacement commits.
- Bulk renames resolve external conflicts before staging. Recovery tracks only
  completed source/destination renames, avoiding the old rollback attempts on
  unprocessed files. Existing duplicate names generated within a bulk pattern
  still receive unique preview names.
- The signature dialog replaces both LSPatch selections with Certificate
  runtime (Parcel). The lightweight Java rewriter remains available. The new
  implementation hooks PackageInfo.CREATOR for the target package and preserves
  the original Application as its superclass. A new DEX holds the runtime;
  original DEX entries are retained. Patched copies include dependency
  notices, require API 28+, and are signed, aligned and verified before publishing.
- Research reference: https://github.com/L-JINBIN/ApkSignatureKillerEx . Its native
  libraries, source, binaries and keys were not imported. This is an independent
  Java implementation, not a copy of MT Manager or a claim of equivalent coverage.
- HiddenApiBypass is pinned to 6.1. Read-only Maven AAR metadata inspection found
  6.2 requires compileSdk 37; 6.1 accepts the project's compileSdk 36. Dependency
  credit and Apache-2.0 notices are included. Empty List.of() calls are adapted
  so injected classes do not need Hexora's desugared JDK runtime.
- Packed/native startup, final/inaccessible Application constructors, native APK
  reads, original Application static-initializer checks, multiple/rotated signers,
  system checks and server checks remain unsupported. Previously patched inputs,
  missing runtime classes and unsupported desugared dependencies stop without
  publishing. Legacy LSPatch source/assets and their attribution are retained,
  but the signature dialog no longer invokes that implementation.

## Source checks

Changed Java files parsed with tree-sitter's Java grammar; changed XML files and
string-resource names validated. `git diff --check` passed. Dependency manifest,
AAR metadata and source imports were inspected without executing its code.

Added unrun JUnit coverage for replacement success, rollback, cancellation,
an arriving destination and failed recovery, plus runtime DEX portability and
manifest Application-name resolution. Updated the owned-fixture signature UI
driver for the Parcel option; it has not been run against these changes.

## Pending device checks after building is authorized

1. Rename shared-storage files with Shizuku on, off and disconnected; repeat with
   root mode, a system folder grant and added local storage.
2. For file and folder collisions, check Replace, Keep both and Cancel in rename,
   bulk rename, copy, move and extraction. Verify original/new bytes and names.
   Repeat with extensionless/dot files, dotted folders and existing numbered names.
   For copying a file/folder onto itself, confirm Replace keeps the original
   bytes/tree and creates no backup or duplicate; Keep both creates a numbered
   copy and Cancel changes nothing.
3. Interrupt a large replacement, revoke provider access during copying and
   simulate a failed second bulk-rename stage; verify retained data and error paths.
4. Check same-volume moves remain immediate and cross-volume moves preserve the
   source on verification failure. Confirm progress cancellation remains usable.
5. Run the owned signature fixture on API 28 and a current Android version with
   the Parcel engine; check legacy/modern queries, reflection, other packages,
   original Application lifecycle and manifest minimum SDK. Recheck unsupported
   inputs, signing failure, duplicate ZIP entries and output alignment.
