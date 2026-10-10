# LSPatch corresponding source

`lspatch-v1.2-recursive-source.tar.gz` contains the unmodified source for the
JingMatrix/LSPatch v1.2 runtime bundled in Hexora, with all 13 recursive
submodules included at their recorded Git commits. Upstream build scripts,
Gradle wrappers, workflows and license notices are included. Git repository
metadata is excluded. No upstream code was built or executed to prepare this
source archive.

LSPatch commit: `0dc50f42503711b14f5e2bb217c2bdd6321ce5be`.
Vector/core commit: `e00c5c5038bbcc14e0b382ab893301d6993e6989`.
The archive SHA-256, size, repository URLs and every submodule commit are in
[SOURCE_MANIFEST.json](SOURCE_MANIFEST.json). The source archive is available
alongside the bundled runtime in this GitHub repository; it is not placed in
the Android app's assets.

Extract using `tar -xzf lspatch-v1.2-recursive-source.tar.gz`. Consult the
included upstream README and build workflows for toolchain and build steps.
Some upstream build scripts calculate version metadata from Git history;
to reproduce that metadata, use full clones at the manifest's pinned commits
with recursive submodules rather than treating this archive as a Git clone.
The manifest records source origins so those clones can be reconstructed.
Android SDK/NDK and Maven dependencies remain upstream toolchain dependencies.

The original libxposed API/service submodule URLs were unavailable when this
archive was prepared. JingMatrix's mirrors supplied the exact recorded commits
`39cac0845771547c9c67a3e3ce255af110a54a0e` and
`3318940876192e29cf6ab07637e899e22a87ebf0`; no substitutions of source versions
were made. Upstream `.gitmodules` files remain unmodified.

LSPatch and Vector are GPL-3.0. Each dependency's own license is retained in
its source directory. Runtime notices and payload provenance are also in
`app/src/main/assets/signature/lspatch`. Preserve these sources and notices
when redistributing the runtime. Preparing this archive does not establish
runtime compatibility or validate Hexora's latest changes.

Source archive preparation and integration: OpenAI Codex.
