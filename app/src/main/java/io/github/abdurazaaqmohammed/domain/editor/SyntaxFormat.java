package io.github.abdurazaaqmohammed.domain.editor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Text syntax detection is separate from file icons and binary file handling. */
public enum SyntaxFormat {
    TEXT("Plain Text", "", "txt", "text", "log", "csv", "tsv"),
    JAVA("Java", "", "java"),
    PYTHON("Python", "and as assert async await break class continue def del elif else except False finally for from global if import in is lambda None nonlocal not or pass raise return True try while with yield match case", "py", "pyw", "pyi"),
    XML("XML", "", "xml", "xsd", "xsl", "xslt", "svg"),
    JSON("JSON", "true false null", "json", "jsonc", "json5", "ipynb"),
    JAVASCRIPT("JavaScript", "as async await break case catch class const continue debugger default delete do else export extends false finally for from function get if import in instanceof let new null of return set static super switch this throw true try typeof undefined var void while with yield", "js", "mjs", "cjs", "jsx"),
    TYPESCRIPT("TypeScript", "abstract any as asserts async await bigint boolean break case catch class const constructor continue declare default delete do else enum export extends false finally for from function get if implements import in infer instanceof interface is keyof let module namespace never new null number object of private protected public readonly require return set static string super switch symbol this throw true try type typeof undefined unique unknown var void while with yield", "ts", "tsx"),
    KOTLIN("Kotlin", "abstract actual annotation as break by catch class companion const constructor continue crossinline data delegate do dynamic else enum expect external false field file final finally for fun get if import in infix init inline inner interface internal is lateinit noinline null object open operator out override package param private property protected public receiver reified return sealed set setparam super suspend tailrec this throw true try typealias typeof val var vararg when where while", "kt", "kts"),
    GROOVY("Groovy", "abstract as assert boolean break byte case catch char class const continue def default do double else enum extends false final finally float for goto if implements import in instanceof int interface long native new null package private protected public return short static strictfp super switch synchronized this throw throws trait transient true try void volatile while", "groovy", "gradle"),
    HTML("HTML", "", "html", "htm", "xhtml", "vue", "svelte"),
    CSS("CSS", "important inherit initial unset auto none", "css", "scss", "sass", "less"),
    YAML("YAML", "true false null yes no on off", "yml", "yaml"),
    CONFIG("Configuration", "true false null", "ini", "conf", "cfg", "properties", "prop", "toml", "env", "pro"),
    SHELL("Shell", "if then else elif fi case esac for while do done in function select until export local readonly unset source echo printf exit return break continue set trap", "sh", "bash", "zsh", "bashrc", "zshrc"),
    SQL("SQL", "SELECT FROM WHERE INSERT INTO VALUES UPDATE SET DELETE CREATE ALTER DROP TABLE INDEX VIEW DATABASE JOIN INNER LEFT RIGHT FULL OUTER ON AS AND OR NOT NULL IS IN EXISTS BETWEEN LIKE ORDER BY GROUP HAVING LIMIT OFFSET DISTINCT UNION ALL PRIMARY KEY FOREIGN REFERENCES DEFAULT CONSTRAINT CASE WHEN THEN ELSE END BEGIN COMMIT ROLLBACK TRUE FALSE COUNT SUM AVG MIN MAX", "sql"),
    MARKDOWN("Markdown", "", "md", "markdown", "rst"),
    C("C", "auto break case char const continue default do double else enum extern float for goto if inline int long register restrict return short signed sizeof static struct switch typedef union unsigned void volatile while _Bool _Complex _Imaginary", "c", "h"),
    CPP("C++", "alignas alignof auto bool break case catch char class concept const constexpr consteval constinit continue co_await co_return co_yield decltype default delete do double dynamic_cast else enum explicit export extern false float for friend goto if inline int long mutable namespace new noexcept nullptr operator private protected public register reinterpret_cast requires return short signed sizeof static static_assert static_cast struct switch template this thread_local throw true try typedef typeid typename union unsigned using virtual void volatile wchar_t while", "cpp", "cc", "cxx", "hpp", "hxx"),
    RUST("Rust", "as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod move mut pub ref return self Self static struct super trait true type unsafe use where while", "rs"),
    GO("Go", "break case chan const continue default defer else fallthrough for func go goto if import interface map package range return select struct switch type var true false nil", "go"),
    PHP("PHP", "abstract and array as break callable case catch class clone const continue declare default do echo else elseif empty endfor endforeach endif endswitch endwhile enum eval exit extends final finally fn for foreach function global if implements include include_once instanceof interface isset list match namespace new null or print private protected public readonly require require_once return static switch throw trait true false try unset use var while xor yield", "php"),
    RUBY("Ruby", "alias and begin break case class def defined do else elsif end ensure false for if in module next nil not or redo rescue retry return self super then true undef unless until when while yield", "rb"),
    SMALI("Smali", "class super source implements field method end registers locals param prologue line local restart return-void return return-object return-wide new-instance new-array move move-object move-result move-result-object move-exception const const-string const-class invoke-virtual invoke-direct invoke-static invoke-interface invoke-super goto if-eq if-ne if-eqz if-nez throw check-cast instance-of public private protected static final synthetic constructor", "smali");

    public final String label;
    final Set<String> keywords;
    private final String[] extensions;

    SyntaxFormat(String label, String keywords, String... extensions) {
        this.label = label;
        this.keywords = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(keywords.split(" "))));
        this.extensions = extensions;
    }

    public boolean isKeyword(String word) {
        return keywords.contains(this == SQL ? word.toUpperCase(Locale.ROOT) : word);
    }

    public static SyntaxFormat forFilename(String path) {
        if (path == null) return TEXT;
        String name = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1).toLowerCase(Locale.ROOT);
        if (name.equals(".bashrc") || name.equals(".zshrc") || name.equals(".profile")) return SHELL;
        if (name.equals("dockerfile") || name.equals("makefile") || name.equals(".gitignore") || name.equals(".editorconfig") || name.startsWith(".env.")) return CONFIG;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return TEXT;
        String extension = name.substring(dot + 1);
        for (SyntaxFormat format : values()) {
            for (String candidate : format.extensions) if (candidate.equals(extension)) return format;
        }
        return TEXT;
    }
}
