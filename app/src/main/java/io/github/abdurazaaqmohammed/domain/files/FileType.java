package io.github.abdurazaaqmohammed.domain.files;

import java.util.Locale;

/** Recognizable labels remain distinct even when users cannot distinguish colors. */
public enum FileType {
    PYTHON("PY", 0xFF356A99, "py", "pyw", "pyi", "pyc"),
    XML("XML", 0xFF315CBC, "xml", "xsd", "xsl", "xslt", "svg"),
    JAVA("JAVA", 0xFF8A563F, "java", "class"),
    TEXT("TXT", 0xFF315CBC, "txt", "text"),
    KOTLIN("KT", 0xFF8054AD, "kt", "kts"),
    JAVASCRIPT("JS", 0xFF997310, "js", "mjs", "cjs", "jsx"),
    TYPESCRIPT("TS", 0xFF316AB3, "ts", "tsx"),
    JSON("JSON", 0xFF3D8271, "json", "jsonc", "json5", "ipynb"),
    HTML("HTML", 0xFFB95434, "html", "htm", "xhtml", "vue", "svelte"),
    CSS("CSS", 0xFF6654B3, "css", "scss", "sass", "less"),
    SHELL("SH", 0xFF47735B, "sh", "bash", "zsh", "bat", "cmd", "ps1"),
    SQL("SQL", 0xFF3B798F, "sql", "db", "sqlite", "sqlite3"),
    MARKDOWN("MD", 0xFF536D83, "md", "markdown", "rst"),
    CONFIG("CFG", 0xFF7A6A52, "ini", "conf", "cfg", "properties", "prop", "toml", "env", "gradle", "pro"),
    YAML("YML", 0xFF997042, "yml", "yaml"),
    SMALI("SMALI", 0xFF397D67, "smali"),
    C("C", 0xFF4876A3, "c", "h"),
    CPP("C++", 0xFF795BA5, "cpp", "cc", "cxx", "hpp", "hxx"),
    RUST("RS", 0xFF936048, "rs"),
    GO("GO", 0xFF32869A, "go"),
    PHP("PHP", 0xFF706099, "php"),
    RUBY("RB", 0xFFAA4F61, "rb"),
    LOG("LOG", 0xFF687980, "log"),
    TABLE("CSV", 0xFF387C58, "csv", "tsv"),
    PDF("PDF", 0xFFBE3343, "pdf"),
    WORD("DOC", 0xFF3F70A6, "doc", "docx", "odt", "rtf"),
    SHEET("XLS", 0xFF398061, "xls", "xlsx", "ods"),
    SLIDES("PPT", 0xFFAC6040, "ppt", "pptx", "odp"),
    FONT("Aa", 0xFF6552C9, "ttf", "otf", "woff", "woff2"),
    CERTIFICATE("KEY", 0xFF947431, "pem", "crt", "cer", "key", "jks", "keystore", "p12"),
    BINARY("BIN", 0xFF65737F, "bin", "dat", "lib", "a", "so", "dll", "exe", "o");

    public final String label;
    public final int color;
    private final String[] extensions;

    FileType(String label, int color, String... extensions) {
        this.label = label;
        this.color = color;
        this.extensions = extensions;
    }

    public static FileType forPath(String path) {
        if (path == null) return null;
        String name = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1)
                .toLowerCase(Locale.ROOT);
        if (name.equals("dockerfile") || name.equals("makefile") || name.equals(".gitignore")
                || name.equals(".editorconfig")) return CONFIG;
        if (name.equals("readme") || name.equals("license") || name.equals("notice")) return TEXT;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        String extension = name.substring(dot + 1);
        for (FileType type : values()) {
            for (String candidate : type.extensions) if (candidate.equals(extension)) return type;
        }
        return null;
    }
}
