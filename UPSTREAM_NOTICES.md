# Upstream attribution and license notices

Hexora is based on [MP-Manager](https://github.com/AbdurazaaqMohammed/MP-Manager)
by Abdurazaaq Mohammed and its upstream contributors. Their original
application and file-management foundation are retained under the
[GPL-3.0 license](LICENSE). Existing source copyright and license notices
remain in their respective files.

Third-party dependency authors, project links and licenses are listed in the
application's About screen and their bundled notices. The LSPatch runtime has
additional provenance and license files in
`app/src/main/assets/signature/lspatch`, with its pinned recursive source in
[third_party/lspatch](third_party/lspatch/README.md).

The signature dialog now uses an independent Parcel certificate runtime in place
of the LSPatch modes. Its AndroidHiddenApiBypass 6.1 dependency (LSPosed,
Apache-2.0), license, research reference and compatibility notes are recorded in
`app/src/main/assets/signature/parcel`. Those notices accompany patched copies.
The former LSPatch implementation and its attribution remain in the source tree.

Hexora's project contributor list is maintained separately in
[CONTRIBUTORS.md](CONTRIBUTORS.md).
