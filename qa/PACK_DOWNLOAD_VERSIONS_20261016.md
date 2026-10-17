# Tool-pack download version mismatch — October 16, 2026

AI-assisted changes: OpenAI Codex. Source changes only; no build, compilation,
APK packaging, installation or device execution was performed.

## Findings and evidence

The supplied screenshot reports a pack returning version 1 while its catalog
expects 2. It shows Device and Network labeled 1.1, Math labeled 1.5. We cannot
read that device's saved catalog or establish its exact APK hashes from an image.

Source has a separate `ToolPack.version()` constant returning 1 in every pack,
including Math whose Gradle versionCode and bundled catalog version are 5.
PackManager previously treated this Java constant as the installed release
version and rejected a match between the actual APK and catalog.

Downloaded the public release URLs already pinned in `assets/packs.json` and
checked each SHA-256 and manifest with the installed Android SDK's read-only
`aapt dump badging`. All seven match the bundled catalog. Observed versions:

| Pack | APK versionCode | APK versionName |
| --- | --- | --- |
| Device | 1 | 1.0 |
| Network | 1 | 1.0 |
| Math | 5 | 1.5 |
| Media, Text, Notes, Random | 1 | 1.0 |

The APK manifest evidence is from ordinary SDK inspection, not REA evidence.
The reverse-engineer-anything skill was consulted; REA's Android operation was
unavailable because its optional JADX provider was not configured. No toolchain
was installed and no APK was executed. The confirmed Math source/manifest
disagreement explains the loader failure. For Device/Network, stale metadata is
consistent with the screenshot, but that specific device's catalog is unknown.

Reference release: https://github.com/AbdurazaaqMohammed/MP-Manager/releases/tag/tool-packs-v1

## Fixes

- Read the release version from the APK manifest. Check it against the catalog
  before changing any installed file. Load-success tracking also uses the APK
  manifest, so legacy packs with a stale Java constant work when their actual
  release matches. Same-signer and SHA-256 checks remain required.
- All seven pack entry classes now use their own generated
  `BuildConfig.VERSION_CODE`; generation is enabled in each module.
- A host update can correct a saved or refreshed catalog entry only when its
  ID, entry class and exact valid SHA-256 match a bundled entry. Different APK
  hashes keep their remote metadata and version checks. There is no blanket
  acceptance of older APKs. Catalog signatures are still checked on refresh.
- Downloads have unique filenames and installation resolves the completed
  DownloadManager ID, rather than guessing a reusable path. Completed/rejected
  download files are removed through DownloadManager to avoid retry leftovers.
- Installation failures appear in a readable dialog instead of a truncated
  toast. Genuine stale-download errors tell users to refresh and explain that
  the publisher may need to replace the file.
- Installed preferences are committed after validation. Missing-tool checks use
  tools loaded by this pack, rather than any matching tool in the registry.
  Backup creation failure preserves the existing APK; recovery failure retains
  the backup and reports its path instead of deleting it.

## Validation

Ran `qa/check_pack_releases.py --download` with SDK aapt 36.0.0: all seven passed
checksum, APK manifest, catalog, Gradle version and generated-version source
checks. Artifacts and full hashes are in ignored
`qa/artifacts/pack-version-review-20261016/release-audit.json`. The audit script
can be rerun against future releases without building or launching APKs.

Added unrun JUnit cases for stale identical-APK catalog correction, preserving
newer remote APKs, unpinned/invalid hashes, wrong pack/entry identity, checksum
case and unknown remote packs. Java syntax, XML/resource references, Python
syntax and diff whitespace checks are checked separately without compilation.

Still pending after builds are authorized: install each pack in the rebuilt
host, exercise refresh/retry and concurrent downloads, simulate failed updates,
and test all tools. A new host release is needed for these fixes to reach users;
no hosted release files or catalogs were changed.
