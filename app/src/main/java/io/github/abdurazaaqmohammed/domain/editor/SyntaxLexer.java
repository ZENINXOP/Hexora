package io.github.abdurazaaqmohammed.domain.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Linear line lexer with explicit state across lines; never modifies document text. */
public final class SyntaxLexer {
    public enum Kind { KEYWORD, STRING, NUMBER, COMMENT, PROPERTY, TYPE, FUNCTION, TAG, ANNOTATION, OPERATOR }

    public static final class Token {
        public final int start, end;
        public final Kind kind;
        Token(int start, int end, Kind kind) { this.start = start; this.end = end; this.kind = kind; }
    }

    public static final class State {
        public static final State INITIAL = new State(null, null, false, false, 0, 0, -1);
        final String delimiter;
        final Kind continued;
        final boolean inTag, tagName;
        final int commentDepth, fence, yamlIndent;
        State(String delimiter, Kind continued, boolean inTag, boolean tagName, int commentDepth, int fence, int yamlIndent) {
            this.delimiter = delimiter; this.continued = continued; this.inTag = inTag; this.tagName = tagName;
            this.commentDepth = commentDepth; this.fence = fence; this.yamlIndent = yamlIndent;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof State s)) return false;
            return Objects.equals(delimiter, s.delimiter) && continued == s.continued && inTag == s.inTag
                    && tagName == s.tagName && commentDepth == s.commentDepth && fence == s.fence && yamlIndent == s.yamlIndent;
        }
        @Override public int hashCode() { return Objects.hash(delimiter, continued, inTag, tagName, commentDepth, fence, yamlIndent); }
    }

    public static final class Result {
        public final State state;
        public final List<Token> tokens;
        Result(State state, List<Token> tokens) { this.state = state; this.tokens = Collections.unmodifiableList(tokens); }
    }

    private static final Pattern NUMBER = Pattern.compile("(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|(?:[0-9][0-9_]*(?:\\.[0-9_]+)?|\\.[0-9]+)(?:[eE][+-]?[0-9_]+)?)[uUlLfFdD]?");
    private final SyntaxFormat format;
    public SyntaxLexer(SyntaxFormat format) { this.format = format; }

    public Result tokenize(CharSequence line, State previous) {
        if (format == SyntaxFormat.TEXT) return new Result(State.INITIAL, Collections.emptyList());
        return new Scan(line.toString(), previous == null ? State.INITIAL : previous).run();
    }

    private final class Scan {
        final String text;
        final int length;
        final List<Token> tokens = new ArrayList<>();
        String delimiter;
        Kind continued;
        boolean inTag, tagName;
        int commentDepth, fence, yamlIndent, pos;
        boolean nextDeclaration;
        Scan(String text, State state) {
            this.text = text; length = text.length(); delimiter = state.delimiter; continued = state.continued;
            inTag = state.inTag; tagName = state.tagName; commentDepth = state.commentDepth;
            fence = state.fence; yamlIndent = state.yamlIndent;
        }
        void add(int start, int end, Kind kind) { if (end > start) tokens.add(new Token(start, end, kind)); }
        boolean at(String value) { return text.startsWith(value, pos); }
        int nonSpace(int from) { while (from < length && Character.isWhitespace(text.charAt(from))) from++; return from; }
        boolean markup() { return format == SyntaxFormat.XML || format == SyntaxFormat.HTML; }
        boolean slashComments() {
            return switch (format) {
                case JAVASCRIPT, TYPESCRIPT, KOTLIN, GROOVY, JAVA, JSON, CSS, C, CPP, RUST, GO, PHP -> true;
                default -> false;
            };
        }
        boolean hashComments() {
            return switch (format) {
                case PYTHON, YAML, CONFIG, SHELL, PHP, RUBY, SMALI -> true;
                default -> false;
            };
        }
        Result finish() { return new Result(new State(delimiter, continued, inTag, tagName, commentDepth, fence, yamlIndent), tokens); }

        Result run() {
            int first = nonSpace(0);
            if (format == SyntaxFormat.MARKDOWN) return markdown(first);
            if (format == SyntaxFormat.YAML && yamlIndent >= 0) {
                if (first == length || first > yamlIndent) { add(0, length, Kind.STRING); return finish(); }
                yamlIndent = -1;
            }
            if (format == SyntaxFormat.RUBY && "=end".equals(delimiter)) {
                add(0, length, Kind.COMMENT);
                if (text.substring(first).matches("=end(?:\\s.*)?")) { delimiter = null; continued = null; }
                return finish();
            }
            if (format == SyntaxFormat.RUBY && text.substring(first).matches("=begin(?:\\s.*)?")) {
                delimiter = "=end"; continued = Kind.COMMENT; add(0, length, Kind.COMMENT); return finish();
            }
            while (pos < length) {
                if (delimiter != null) { consumeContinuation(pos); continue; }
                char ch = text.charAt(pos);
                if (Character.isWhitespace(ch)) { pos++; continue; }
                if (markup()) { markupToken(); continue; }
                if ((slashComments() && at("//") && format != SyntaxFormat.CSS)
                        || (format == SyntaxFormat.SQL && at("--")) || (hashComments() && ch == '#')
                        || (format == SyntaxFormat.CONFIG && ch == ';') || (pos == first && at("#!"))) {
                    add(pos, length, Kind.COMMENT); pos = length; continue;
                }
                if ((slashComments() || format == SyntaxFormat.SQL) && at("/*")) {
                    startBlock("*/", Kind.COMMENT, 2); continue;
                }
                if ((format == SyntaxFormat.C || format == SyntaxFormat.CPP) && pos == first && ch == '#') {
                    add(pos, length, Kind.ANNOTATION); pos = length; continue;
                }
                if ((format == SyntaxFormat.PYTHON || format == SyntaxFormat.KOTLIN || format == SyntaxFormat.CONFIG || format == SyntaxFormat.GROOVY) && (at("\"\"\"") || at("'''"))) {
                    startBlock(text.substring(pos, pos + 3), Kind.STRING, 3); continue;
                }
                // Python prefixes belong to the string, rather than a separate identifier.
                if (format == SyntaxFormat.PYTHON && "rRuUbBfF".indexOf(ch) >= 0) {
                    int quote = pos + 1;
                    if (quote < length && "rRuUbBfF".indexOf(text.charAt(quote)) >= 0) quote++;
                    if (quote < length && (text.charAt(quote) == '\'' || text.charAt(quote) == '"')) {
                        quoted(quote, pos); continue;
                    }
                }
                if (format == SyntaxFormat.RUST && ch == '\'' && pos + 1 < length && Character.isJavaIdentifierStart(text.charAt(pos + 1))) {
                    int end = pos + 2;
                    while (end < length && Character.isJavaIdentifierPart(text.charAt(end))) end++;
                    if (end == length || text.charAt(end) != '\'') { add(pos, end, Kind.TYPE); pos = end; continue; }
                }
                if (ch == '\'' || ch == '"' || ch == '`' && (format == SyntaxFormat.JAVASCRIPT || format == SyntaxFormat.TYPESCRIPT || format == SyntaxFormat.GO || format == SyntaxFormat.SHELL || format == SyntaxFormat.SQL)) {
                    quoted(pos, pos); continue;
                }
                if (format == SyntaxFormat.CONFIG && ch == '[') {
                    int end = text.indexOf(']', pos + 1);
                    if (end >= 0) { add(pos, end + 1, Kind.TYPE); pos = end + 1; continue; }
                }
                if ((format == SyntaxFormat.SHELL || format == SyntaxFormat.PHP) && ch == '$') {
                    int start = pos++;
                    if (pos < length && text.charAt(pos) == '{') {
                        int end = text.indexOf('}', ++pos); pos = end < 0 ? length : end + 1;
                    } else while (pos < length && (Character.isJavaIdentifierPart(text.charAt(pos)) || "?@*#!".indexOf(text.charAt(pos)) >= 0)) pos++;
                    add(start, pos, Kind.PROPERTY); continue;
                }
                if (ch == '@' && (format == SyntaxFormat.PYTHON || format == SyntaxFormat.KOTLIN || format == SyntaxFormat.CSS || format == SyntaxFormat.TYPESCRIPT)) {
                    int start = pos++;
                    while (pos < length && (Character.isJavaIdentifierPart(text.charAt(pos)) || text.charAt(pos) == '.' || text.charAt(pos) == '-')) pos++;
                    add(start, pos, Kind.ANNOTATION); continue;
                }
                if (Character.isDigit(ch) || ch == '.' && pos + 1 < length && Character.isDigit(text.charAt(pos + 1))) {
                    Matcher number = NUMBER.matcher(text).region(pos, length);
                    if (number.lookingAt()) { add(pos, number.end(), Kind.NUMBER); pos = number.end(); continue; }
                }
                if (format == SyntaxFormat.YAML && (ch == '|' || ch == '>') && !tokens.isEmpty()) {
                    String suffix = text.substring(pos + 1).trim();
                    if (suffix.isEmpty() || suffix.matches("[+-]?[1-9]?\\s*(?:#.*)?")) {
                        yamlIndent = first; add(pos, length, Kind.STRING); pos = length; continue;
                    }
                }
                if (Character.isJavaIdentifierStart(ch) || (format == SyntaxFormat.SMALI && ch == '.')
                        || format == SyntaxFormat.CSS && (ch == '-' || ch == '#')) {
                    word(); continue;
                }
                if ("{}[]():;,.=+-*/<>!&|%^~?".indexOf(ch) >= 0) add(pos, pos + 1, Kind.OPERATOR);
                pos++;
            }
            return finish();
        }

        void word() {
            int start = pos++;
            while (pos < length && (Character.isJavaIdentifierPart(text.charAt(pos))
                    || "-".indexOf(text.charAt(pos)) >= 0 && (format == SyntaxFormat.CSS || format == SyntaxFormat.SMALI || format == SyntaxFormat.CONFIG || format == SyntaxFormat.YAML)
                    || text.charAt(pos) == '.' && (format == SyntaxFormat.CONFIG || format == SyntaxFormat.YAML))) pos++;
            String word = text.substring(start, pos);
            int next = nonSpace(pos);
            Kind kind = null;
            if (format.isKeyword(word) || format == SyntaxFormat.SMALI && word.startsWith(".")) kind = Kind.KEYWORD;
            else if (nextDeclaration) kind = Kind.FUNCTION;
            else if ((format == SyntaxFormat.JSON || format == SyntaxFormat.YAML || format == SyntaxFormat.CSS) && next < length && text.charAt(next) == ':') kind = Kind.PROPERTY;
            else if (format == SyntaxFormat.CONFIG && next < length && (text.charAt(next) == '=' || text.charAt(next) == ':')) kind = Kind.PROPERTY;
            else if (format == SyntaxFormat.SMALI && word.matches("[vp][0-9]+")) kind = Kind.NUMBER;
            else if (next < length && text.charAt(next) == '(') kind = Kind.FUNCTION;
            else if (format == SyntaxFormat.CSS && start == nonSpace(0)) kind = Kind.TAG;
            nextDeclaration = kind == Kind.KEYWORD && (word.equals("def") || word.equals("class") || word.equals("fun") || word.equals("func") || word.equals("fn") || word.equals("function") || word.equals("struct") || word.equals("interface"));
            if (kind != null) add(start, pos, kind);
        }

        void startBlock(String end, Kind kind, int openingLength) {
            int start = pos; delimiter = end; continued = kind;
            commentDepth = kind == Kind.COMMENT && "*/".equals(end) ? 1 : 0;
            pos += openingLength; consumeContinuation(start);
        }

        void consumeContinuation(int start) {
            String end = delimiter;
            Kind kind = continued;
            boolean nested = commentDepth > 0 && (format == SyntaxFormat.RUST || format == SyntaxFormat.KOTLIN);
            while (pos < length) {
                if (nested && at("/*")) { commentDepth++; pos += 2; continue; }
                if (at(end)) {
                    if (kind == Kind.STRING && end.length() == 1 && (format == SyntaxFormat.SQL || format == SyntaxFormat.YAML) && text.startsWith(end + end, pos)) {
                        pos += 2; continue;
                    }
                    pos += end.length();
                    if (nested && --commentDepth > 0) continue;
                    delimiter = null; continued = null; commentDepth = 0;
                    add(start, pos, kind); return;
                }
                if (kind == Kind.STRING && text.charAt(pos) == '\\' && !markup() && format != SyntaxFormat.GO
                        && !(format == SyntaxFormat.SHELL && "'".equals(end))
                        && !(format == SyntaxFormat.YAML && "'".equals(end))
                        && !(format == SyntaxFormat.KOTLIN && end.length() == 3)) pos = Math.min(length, pos + 2);
                else pos++;
            }
            add(start, length, kind);
        }

        void quoted(int quotePosition, int start) {
            char quote = text.charAt(quotePosition);
            String end = Character.toString(quote);
            boolean multiline = quote == '`' || format == SyntaxFormat.SHELL || format == SyntaxFormat.RUBY || format == SyntaxFormat.PHP || format == SyntaxFormat.SQL || format == SyntaxFormat.YAML || format == SyntaxFormat.GROOVY;
            if (format == SyntaxFormat.PYTHON && text.startsWith(end + end + end, quotePosition)) {
                pos = quotePosition; startBlock(end + end + end, Kind.STRING, 3);
                if (start < quotePosition) { Token last = tokens.remove(tokens.size() - 1); add(start, last.end, last.kind); }
                return;
            }
            pos = quotePosition + 1;
            boolean closed = false;
            while (pos < length) {
                if (text.charAt(pos) == quote) {
                    if ((format == SyntaxFormat.SQL || format == SyntaxFormat.YAML) && pos + 1 < length && text.charAt(pos + 1) == quote) { pos += 2; continue; }
                    pos++; closed = true; break;
                }
                if (text.charAt(pos) == '\\' && !(format == SyntaxFormat.GO && quote == '`') && !((format == SyntaxFormat.YAML || format == SyntaxFormat.SHELL) && quote == '\'')) pos = Math.min(length, pos + 2);
                else pos++;
            }
            Kind kind = Kind.STRING;
            int next = nonSpace(pos);
            if ((format == SyntaxFormat.JSON || format == SyntaxFormat.YAML) && closed && next < length && text.charAt(next) == ':') kind = Kind.PROPERTY;
            add(start, pos, kind);
            if (!closed && multiline) { delimiter = end; continued = kind; }
        }

        void markupToken() {
            int start = pos;
            if (at("<!--")) { startBlock("-->", Kind.COMMENT, 4); return; }
            if (at("<![CDATA[")) { startBlock("]]>", Kind.STRING, 9); return; }
            char ch = text.charAt(pos);
            if (ch == '<') {
                pos++;
                if (pos < length && "/!?".indexOf(text.charAt(pos)) >= 0) pos++;
                add(start, pos, Kind.OPERATOR); inTag = true; tagName = true; return;
            }
            if (inTag && (ch == '>' || at("/>") || at("?>"))) {
                pos += ch == '>' ? 1 : 2; add(start, pos, Kind.OPERATOR); inTag = false; tagName = false; return;
            }
            if (inTag && (ch == '"' || ch == '\'')) {
                // Attribute values may wrap onto another line.
                startBlock(Character.toString(ch), Kind.STRING, 1); return;
            }
            if (ch == '&') {
                int end = text.indexOf(';', pos + 1);
                if (end >= 0 && end - pos < 32) { pos = end + 1; add(start, pos, Kind.ANNOTATION); return; }
            }
            if (inTag && (Character.isLetter(ch) || ch == '_' || ch == ':')) {
                pos++;
                while (pos < length && (Character.isLetterOrDigit(text.charAt(pos)) || "_:-.".indexOf(text.charAt(pos)) >= 0)) pos++;
                add(start, pos, tagName ? Kind.TAG : Kind.PROPERTY); tagName = false; return;
            }
            if (inTag && ch == '=') add(pos, pos + 1, Kind.OPERATOR);
            pos++;
        }

        Result markdown(int first) {
            String trimmed = text.substring(first);
            boolean backticks = trimmed.startsWith("```");
            boolean tildes = trimmed.startsWith("~~~");
            if (fence != 0) {
                add(0, length, Kind.STRING);
                char marker = fence > 0 ? '`' : '~';
                int count = 0; while (count < trimmed.length() && trimmed.charAt(count) == marker) count++;
                if (count >= Math.abs(fence) && trimmed.substring(count).trim().isEmpty()) fence = 0;
                return finish();
            }
            if (backticks || tildes) {
                int count = 0; char marker = trimmed.charAt(0);
                while (count < trimmed.length() && trimmed.charAt(count) == marker) count++;
                fence = marker == '`' ? count : -count; add(first, length, Kind.ANNOTATION); return finish();
            }
            if (trimmed.matches("#{1,6}(?:\\s.*)?")) { add(first, length, Kind.KEYWORD); return finish(); }
            if (trimmed.startsWith(">")) add(first, first + 1, Kind.COMMENT);
            while (pos < length) {
                int start = pos;
                if (at("**") || at("__")) {
                    String marker = text.substring(pos, pos + 2); int end = text.indexOf(marker, pos + 2);
                    if (end >= 0) { pos = end + 2; add(start, pos, Kind.FUNCTION); continue; }
                }
                if (text.charAt(pos) == '`') {
                    int end = text.indexOf('`', pos + 1);
                    if (end >= 0) { pos = end + 1; add(start, pos, Kind.STRING); continue; }
                }
                if (at("](")) {
                    int end = text.indexOf(')', pos + 2);
                    if (end >= 0) { pos = end + 1; add(start, pos, Kind.PROPERTY); continue; }
                }
                pos++;
            }
            return finish();
        }
    }
}
