package io.github.abdurazaaqmohammed.domain.editor;

import org.junit.Test;
import java.util.Locale;
import static io.github.abdurazaaqmohammed.domain.editor.SyntaxLexer.Kind.*;
import static org.junit.Assert.*;

public class SyntaxLexerTest {
    private SyntaxLexer.Result scan(SyntaxFormat format, String line) {
        return new SyntaxLexer(format).tokenize(line, SyntaxLexer.State.INITIAL);
    }
    private void token(String line, SyntaxLexer.Result result, String value, SyntaxLexer.Kind kind) {
        assertTrue("Missing " + kind + " token: " + value, result.tokens.stream().anyMatch(t -> t.kind == kind && line.substring(t.start, t.end).equals(value)));
    }

    @Test public void detectsSourceExtensionsWithoutUsingDirectoryNames() {
        assertEquals(SyntaxFormat.PYTHON, SyntaxFormat.forFilename("C:\\folder.java\\SCRIPT.PY"));
        assertEquals(SyntaxFormat.XML, SyntaxFormat.forFilename("archive.zip/res/layout/main.XML"));
        assertEquals(SyntaxFormat.TEXT, SyntaxFormat.forFilename("/folder.py/unknown"));
        assertEquals(SyntaxFormat.GROOVY, SyntaxFormat.forFilename("build.gradle"));
        assertEquals(SyntaxFormat.KOTLIN, SyntaxFormat.forFilename("build.gradle.kts"));
        assertEquals(SyntaxFormat.SHELL, SyntaxFormat.forFilename(".bashrc"));
        assertEquals(SyntaxFormat.CONFIG, SyntaxFormat.forFilename(".env.production"));
    }
    @Test public void unknownAndBinaryNamesRemainPlainText() {
        for (String name : new String[]{null, "", "file.", "thing.unknown", "classes.dex", "Main.class", "data.sqlite", "module.pyc"})
            assertEquals(SyntaxFormat.TEXT, SyntaxFormat.forFilename(name));
    }
    @Test public void extensionDetectionIsLocaleIndependent() {
        Locale previous = Locale.getDefault();
        try { Locale.setDefault(new Locale("tr", "TR")); assertEquals(SyntaxFormat.CONFIG, SyntaxFormat.forFilename("SETTINGS.INI")); }
        finally { Locale.setDefault(previous); }
    }
    @Test public void pythonKeywordsStringsNumbersAndCommentsAreSeparate() {
        String line = "if count == 42: print(\"# not a comment\") # actual comment";
        SyntaxLexer.Result r = scan(SyntaxFormat.PYTHON, line);
        token(line, r, "if", KEYWORD); token(line, r, "42", NUMBER);
        token(line, r, "print", FUNCTION); token(line, r, "\"# not a comment\"", STRING);
        token(line, r, "# actual comment", COMMENT);
    }
    @Test public void pythonTripleStringsContinueAndReturnToCode() {
        SyntaxLexer lexer = new SyntaxLexer(SyntaxFormat.PYTHON);
        SyntaxLexer.Result open = lexer.tokenize("doc = r\"\"\"begin", SyntaxLexer.State.INITIAL);
        String line = "# string, not comment\"\"\"; return 5";
        SyntaxLexer.Result closed = lexer.tokenize(line, open.state);
        token(line, closed, "# string, not comment\"\"\"", STRING);
        token(line, closed, "return", KEYWORD); token(line, closed, "5", NUMBER);
        assertEquals(SyntaxLexer.State.INITIAL, closed.state);
    }
    @Test public void unterminatedOrdinaryPythonQuoteDoesNotColorTheNextLine() {
        SyntaxLexer lexer = new SyntaxLexer(SyntaxFormat.PYTHON);
        SyntaxLexer.Result first = lexer.tokenize("value = 'incomplete", SyntaxLexer.State.INITIAL);
        assertEquals(SyntaxLexer.State.INITIAL, first.state);
        token("return 2", lexer.tokenize("return 2", first.state), "return", KEYWORD);
    }
    @Test public void removingAMultilineOpenerChangesFollowingLineState() {
        SyntaxLexer lexer = new SyntaxLexer(SyntaxFormat.JAVASCRIPT);
        SyntaxLexer.State comment = lexer.tokenize("/* comment", SyntaxLexer.State.INITIAL).state;
        SyntaxLexer.State edited = lexer.tokenize("// comment", SyntaxLexer.State.INITIAL).state;
        assertNotEquals(comment, edited);
        token("return 1", lexer.tokenize("return 1", comment), "return 1", COMMENT);
        token("return 1", lexer.tokenize("return 1", edited), "return", KEYWORD);
    }
    @Test public void nestedRustAndKotlinCommentsKeepTheOuterCommentOpen() {
        for (SyntaxFormat format : new SyntaxFormat[]{SyntaxFormat.RUST, SyntaxFormat.KOTLIN}) {
            SyntaxLexer lexer = new SyntaxLexer(format);
            SyntaxLexer.State open = lexer.tokenize("/* outer /* nested */", SyntaxLexer.State.INITIAL).state;
            String line = "still comment */ return 1";
            SyntaxLexer.Result result = lexer.tokenize(line, open);
            token(line, result, "still comment */", COMMENT); token(line, result, "return", KEYWORD);
            assertEquals(SyntaxLexer.State.INITIAL, result.state);
        }
    }
    @Test public void jsonKeysDifferFromStringsAndNumbers() {
        String line = "{\"name\": \"Hexora\", \"count\": 42, \"enabled\": true}";
        SyntaxLexer.Result r = scan(SyntaxFormat.JSON, line);
        token(line, r, "\"name\"", PROPERTY); token(line, r, "\"Hexora\"", STRING);
        token(line, r, "42", NUMBER); token(line, r, "true", KEYWORD);
    }
    @Test public void quotedUrlsDoNotStartJavascriptComments() {
        String line = "const url = \"https://example.test/a\"; // comment";
        SyntaxLexer.Result r = scan(SyntaxFormat.JAVASCRIPT, line);
        token(line, r, "\"https://example.test/a\"", STRING); token(line, r, "// comment", COMMENT);
    }
    @Test public void javascriptTemplateAndGoRawStringsContinueAcrossLines() {
        for (SyntaxFormat format : new SyntaxFormat[]{SyntaxFormat.JAVASCRIPT, SyntaxFormat.GO}) {
            SyntaxLexer lexer = new SyntaxLexer(format);
            SyntaxLexer.State open = lexer.tokenize("value = `begin", SyntaxLexer.State.INITIAL).state;
            String line = "// string` + 3";
            SyntaxLexer.Result r = lexer.tokenize(line, open);
            token(line, r, "// string`", STRING); token(line, r, "3", NUMBER);
            assertEquals(SyntaxLexer.State.INITIAL, r.state);
        }
    }
    @Test public void xmlTagsAttributesAndMultilineCommentsHaveSeparateTokens() {
        SyntaxLexer lexer = new SyntaxLexer(SyntaxFormat.XML);
        String line = "<TextView android:text=\"Hello\" />";
        SyntaxLexer.Result r = lexer.tokenize(line, SyntaxLexer.State.INITIAL);
        token(line, r, "TextView", TAG); token(line, r, "android:text", PROPERTY); token(line, r, "\"Hello\"", STRING);
        SyntaxLexer.State comment = lexer.tokenize("<!-- begin", r.state).state;
        token("not a <tag>", lexer.tokenize("not a <tag>", comment), "not a <tag>", COMMENT);
        String end = "--> <item/>";
        token(end, lexer.tokenize(end, comment), "item", TAG);
    }
    @Test public void xmlWrappedAttributesAndCdataResumeMarkup() {
        SyntaxLexer lexer = new SyntaxLexer(SyntaxFormat.XML);
        SyntaxLexer.State open = lexer.tokenize("<item", SyntaxLexer.State.INITIAL).state;
        String line = "  name=\"C:\\folder\\\">";
        SyntaxLexer.Result r = lexer.tokenize(line, open);
        token(line, r, "name", PROPERTY); assertEquals(SyntaxLexer.State.INITIAL, r.state);
        SyntaxLexer.State cdata = lexer.tokenize("<![CDATA[begin", r.state).state;
        String end = "<text>]]><item/>";
        SyntaxLexer.Result closed = lexer.tokenize(end, cdata);
        token(end, closed, "<text>]]>", STRING); token(end, closed, "item", TAG);
    }
    @Test public void sqlKeywordsAreCaseInsensitiveAndDoubledQuotesStayInsideStrings() {
        String line = "select 'it''s -- text', 12 from records -- actual comment";
        SyntaxLexer.Result r = scan(SyntaxFormat.SQL, line);
        token(line, r, "select", KEYWORD); token(line, r, "from", KEYWORD);
        token(line, r, "'it''s -- text'", STRING); token(line, r, "-- actual comment", COMMENT);
    }
    @Test public void yamlBlockScalarsEndOnDedent() {
        SyntaxLexer lexer = new SyntaxLexer(SyntaxFormat.YAML);
        SyntaxLexer.State open = lexer.tokenize("message: |", SyntaxLexer.State.INITIAL).state;
        token("  # literal text", lexer.tokenize("  # literal text", open), "  # literal text", STRING);
        String line = "enabled: true";
        SyntaxLexer.Result closed = lexer.tokenize(line, open);
        token(line, closed, "enabled", PROPERTY); token(line, closed, "true", KEYWORD);
        assertEquals(SyntaxLexer.State.INITIAL, closed.state);
    }
    @Test public void configurationKeysAndShellVariablesAreRecognized() {
        String config = "api.url = \"https://example.test\" # note";
        token(config, scan(SyntaxFormat.CONFIG, config), "api.url", PROPERTY);
        String shell = "echo ${HOME} # comment";
        SyntaxLexer.Result r = scan(SyntaxFormat.SHELL, shell);
        token(shell, r, "echo", KEYWORD); token(shell, r, "${HOME}", PROPERTY); token(shell, r, "# comment", COMMENT);
    }
    @Test public void commonSourceProfilesRecognizeTheirOwnKeywords() {
        SyntaxFormat[] formats = {SyntaxFormat.C, SyntaxFormat.CPP, SyntaxFormat.KOTLIN, SyntaxFormat.TYPESCRIPT,
                SyntaxFormat.RUST, SyntaxFormat.GO, SyntaxFormat.PHP, SyntaxFormat.RUBY, SyntaxFormat.GROOVY, SyntaxFormat.SMALI};
        String[] samples = {"int value = 1;", "constexpr int value = 1;", "val value = 1", "interface Sample {}",
                "let value = 1;", "func main() {}", "echo $value;", "def hello", "def value = 1", ".registers 3"};
        String[] keywords = {"int", "constexpr", "val", "interface", "let", "func", "echo", "def", "def", ".registers"};
        for (int i = 0; i < formats.length; i++) token(samples[i], scan(formats[i], samples[i]), keywords[i], KEYWORD);
    }
    @Test public void cssAndMarkdownHaveFormatSpecificTokens() {
        String css = "color: \"red\"; margin: 12px; /* note */";
        SyntaxLexer.Result r = scan(SyntaxFormat.CSS, css);
        token(css, r, "color", PROPERTY); token(css, r, "12", NUMBER); token(css, r, "/* note */", COMMENT);
        token("# Heading", scan(SyntaxFormat.MARKDOWN, "# Heading"), "# Heading", KEYWORD);
        SyntaxLexer md = new SyntaxLexer(SyntaxFormat.MARKDOWN);
        SyntaxLexer.State open = md.tokenize("````python", SyntaxLexer.State.INITIAL).state;
        assertNotEquals(SyntaxLexer.State.INITIAL, md.tokenize("```", open).state);
        assertEquals(SyntaxLexer.State.INITIAL, md.tokenize("````", open).state);
    }
    @Test public void plainTextHasNoSyntaxTokensAndLongLinesRemainOrdered() {
        String line = "if count == 42: print(\"value\") # note";
        assertTrue(scan(SyntaxFormat.TEXT, line).tokens.isEmpty());
        String large = line.repeat(5000);
        SyntaxLexer.Result r = scan(SyntaxFormat.PYTHON, large);
        int end = 0;
        for (SyntaxLexer.Token t : r.tokens) { assertTrue(t.start >= end); assertTrue(t.end <= large.length()); end = t.end; }
        assertEquals(large.length(), end);
    }
}
