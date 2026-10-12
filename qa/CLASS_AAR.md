# JVM class and AAR inspection

AI-assisted implementation and validation: OpenAI Codex.

`.class` files open directly in a read-only JVM bytecode viewer, showing class
version, method descriptors, fields, constants, instruction names, labels, stack
and local counts. They are no longer sent automatically to Hex editor or decoded
as ordinary UTF-8. Long instruction lines wrap. Hex editor remains available
through Open with and the local class preview's Hex editor button.

File Options → **View as Java** creates a separate read-only Java tab for a JVM
class or a complete Smali document, including entries inside AARs and JARs.
Conversion runs on the document worker, supports cancellation, preserves the
current text-editor palette, and leaves the source tab and binary/archive bytes
unchanged. The tab drawer returns to the original bytecode or Smali. Minimized
Java tabs persist with their read-only status. Existing DEX Smali tabs keep their
Smali to Java action and the separately verified DEX palette correction.

This is inspection and decompilation; it does not implement MT's editable JVM
assembler/compiler. Decompiled Java is reconstructed output and may contain
warnings or fail for unsupported/obfuscated bytecode. Such failures retain the
original file and report an error. Bytecode inputs are capped at 2 MiB, JVM Java
conversion at 512 KiB and Smali conversion at 512 Ki characters. Display output
uses the editor's existing bounded document preparation. Partial/generated
documents cannot overwrite a class file.

The owned `Greeting.class` was compared with installed MT Manager Canary before
implementation. MT showed JVM instructions and method descriptors; the same
owned `sample.aar` opened as an archive containing resources and `classes.jar`.
Reference screenshots are in `artifacts/20261011/class-aar-reference`. No MT
assets or user application files were copied or modified.

AAR, JAR, WAR and ZIP share the built-in archive browser and archive icon.
Opening a nested archive records its containing archive and entry directory.
Back, Up and the `..` row at the nested archive root return to that directory,
and the main path uses the logical archive chain. Cache files remain internal
storage details. Parent mappings are retained in activity saved state and remain
available during history navigation and pane switching.

The class viewer uses [ASM](https://asm.ow2.io/), pinned to `asm-util:9.7.1`,
with its [BSD license](https://asm.ow2.io/license.html) bundled in
`app/src/main/assets/licenses/ASM-LICENSE.txt`. Java conversion uses the existing
JADX 1.4.7 engine plus its matching
[JVM input plugin](https://github.com/skylot/jadx/blob/v1.4.7/jadx-plugins/jadx-java-input/src/main/java/jadx/plugins/input/java/JavaInputPlugin.java).
Upstream attribution and licenses are retained.

Seven added JVM tests verify instruction/descriptors, Java conversion, malformed
and oversized files, read-only output, nested parents, restoration and root-staged
archive parents. Native UI evidence and build hashes are recorded in
`RUNTIME_20261011.md`; failed attempts are retained alongside corrected runs.
