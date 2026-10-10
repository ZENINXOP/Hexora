# Text syntax colors

Source files select their syntax from the filename, including uppercase extensions
and archive-entry names. Plain text and unknown formats stay plain. File Options →
Syntax offers Automatic, Plain Text and the supported languages. Manual choices
belong to the current document during the editor session, so another tab cannot
inherit the wrong language. Preferences → Syntax Highlighting controls highlighting;
Preferences → Theme changes the editor palette without changing its content.

Formats: Java (bundled Java language), Python, XML, JSON/JSONC/JSON5, JavaScript,
TypeScript, Kotlin, Groovy/Gradle, HTML, CSS/SCSS, YAML, INI/properties/TOML/env,
Shell, SQL, Markdown, C, C++, Rust, Go, PHP, Ruby and standalone Smali. The DEX editor
keeps its dedicated TextMate Smali language and theme.

The new languages use a lightweight incremental lexical highlighter. Multiline
comments/strings, wrapped XML tags/attributes, Python triple strings, YAML block
scalars, nested Rust/Kotlin comments and Markdown fences retain state between lines.
Each editor owns its background analyzer. Recoloring does not rewrite document text.
The 19 pure-Java regression tests cover token boundaries and state transitions;
native checks inspect real rendered pixels and exercise editing/preferences.

This provides syntax colors, not compilation, validation, automatic code formatting
or semantic completion. It is not a complete grammar for every dialect. Embedded
script/style languages, JavaScript/Ruby regular-expression literals, Rust raw-string
variants and shell/PHP heredocs are not fully parsed. Language-server features and
custom grammar import are outside this change.
