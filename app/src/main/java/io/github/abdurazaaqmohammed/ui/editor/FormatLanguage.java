package io.github.abdurazaaqmohammed.ui.editor;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ArrayDeque;

import io.github.abdurazaaqmohammed.domain.editor.SyntaxFormat;
import io.github.abdurazaaqmohammed.domain.editor.SyntaxLexer;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager;
import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager.LineTokenizeResult;
import io.github.rosemoe.sora.lang.styling.CodeBlock;
import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.TextStyle;
import io.github.rosemoe.sora.lang.brackets.SimpleBracketsCollector;
import io.github.rosemoe.sora.lang.brackets.PairedBracket;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/** One incremental background analyzer per editor, independent of DEX/TextMate themes. */
public final class FormatLanguage extends EmptyLanguage {
    private static final int STRING = 200, NUMBER = 201, PROPERTY = 202, TYPE = 203, FUNCTION = 204;
    private final Analyzer analyzer;

    public FormatLanguage(SyntaxFormat format) { analyzer = new Analyzer(format); }

    @NonNull @Override public AnalyzeManager getAnalyzeManager() { return analyzer; }

    public static void configureColors(EditorColorScheme scheme) {
        boolean dark = ColorUtils.calculateLuminance(scheme.getColor(EditorColorScheme.WHOLE_BACKGROUND)) < 0.5;
        scheme.setColor(STRING, dark ? 0xFFA8DAB5 : 0xFF287442);
        scheme.setColor(NUMBER, dark ? 0xFFF2C18D : 0xFF925316);
        scheme.setColor(PROPERTY, dark ? 0xFF8AB4F8 : 0xFF2457A6);
        scheme.setColor(TYPE, dark ? 0xFFC4ABEE : 0xFF7047A3);
        scheme.setColor(FUNCTION, dark ? 0xFFF2D49B : 0xFF805600);
        scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, dark ? 0xFF34506A : 0xFFDCEBFA);
        scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, dark ? 0xFFFFFFFF : 0xFF163B68);
        scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, dark ? 0xFF8AB4F8 : 0xFF2457A6);
    }

    private static int color(SyntaxLexer.Kind kind) {
        return switch (kind) {
            case KEYWORD -> EditorColorScheme.KEYWORD;
            case STRING -> STRING;
            case NUMBER -> NUMBER;
            case COMMENT -> EditorColorScheme.COMMENT;
            case PROPERTY -> PROPERTY;
            case TYPE, TAG -> TYPE;
            case FUNCTION -> FUNCTION;
            case ANNOTATION -> EditorColorScheme.ANNOTATION;
            case OPERATOR -> EditorColorScheme.OPERATOR;
        };
    }

    private static final class Analyzer extends AsyncIncrementalAnalyzeManager<SyntaxLexer.State, SyntaxLexer.Token> {
        private final SyntaxLexer lexer;
        Analyzer(SyntaxFormat format) { lexer = new SyntaxLexer(format); }
        @Override public SyntaxLexer.State getInitialState() { return SyntaxLexer.State.INITIAL; }
        @Override public boolean stateEquals(SyntaxLexer.State a, SyntaxLexer.State b) { return a.equals(b); }
        @Override public LineTokenizeResult<SyntaxLexer.State, SyntaxLexer.Token> tokenizeLine(CharSequence line, SyntaxLexer.State state, int lineIndex) {
            SyntaxLexer.Result result = lexer.tokenize(line, state);
            return new LineTokenizeResult<>(result.state, result.tokens);
        }
        @Override public List<Span> generateSpansForLine(LineTokenizeResult<SyntaxLexer.State, SyntaxLexer.Token> result) {
            List<Span> spans = new ArrayList<>();
            spans.add(Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)));
            for (SyntaxLexer.Token token : result.tokens) {
                Span last = spans.get(spans.size() - 1);
                if (last.getColumn() == token.start) last.setStyle(TextStyle.makeStyle(color(token.kind)));
                else spans.add(Span.obtain(token.start, TextStyle.makeStyle(color(token.kind))));
                spans.add(Span.obtain(token.end, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)));
            }
            return spans;
        }
        @Override public List<CodeBlock> computeBlocks(Content text, CodeBlockAnalyzeDelegate delegate) {
            // Build pairs off the UI thread. Selection then needs only an indexed lookup.
            if (delegate.isCancelled() || getContentRef() == null) return Collections.emptyList();
            long version = getContentRef().getDocumentVersion();
            int length = text.length();
            SimpleBracketsCollector pairs = new SimpleBracketsCollector();
            ArrayDeque<Integer> openings = new ArrayDeque<>();
            ArrayDeque<Character> kinds = new ArrayDeque<>();
            SyntaxLexer.State state = SyntaxLexer.State.INITIAL;
            int offset = 0;
            for (int lineIndex = 0; lineIndex < text.getLineCount(); lineIndex++) {
                if (delegate.isCancelled()) return Collections.emptyList();
                String line = text.getLineString(lineIndex);
                SyntaxLexer.Result result = lexer.tokenize(line, state);
                state = result.state;
                int tokenIndex = 0;
                for (int column = 0; column < line.length(); column++) {
                    while (tokenIndex < result.tokens.size() && result.tokens.get(tokenIndex).end <= column) tokenIndex++;
                    if (tokenIndex < result.tokens.size()) {
                        SyntaxLexer.Token token = result.tokens.get(tokenIndex);
                        boolean quotedProperty = token.kind == SyntaxLexer.Kind.PROPERTY
                                && (line.charAt(token.start) == '"' || line.charAt(token.start) == '\'');
                        if (column >= token.start && (token.kind == SyntaxLexer.Kind.STRING
                                || token.kind == SyntaxLexer.Kind.COMMENT || quotedProperty)) {
                            column = token.end - 1;
                            continue;
                        }
                    }
                    char ch = line.charAt(column);
                    if (ch == '(' || ch == '[' || ch == '{') {
                        openings.push(offset + column);
                        kinds.push(ch);
                    } else if (ch == ')' || ch == ']' || ch == '}') {
                        char expected = ch == ')' ? '(' : ch == ']' ? '[' : '{';
                        if (!kinds.isEmpty() && kinds.peek() == expected) {
                            pairs.add(openings.pop(), offset + column);
                            kinds.pop();
                        } else {
                            // Malformed nesting must not connect unrelated brackets.
                            openings.clear();
                            kinds.clear();
                        }
                    }
                }
                offset += line.length() + text.getLine(lineIndex).getLineSeparator().getContent().length();
            }
            if (!delegate.isCancelled()) withReceiver(receiver -> receiver.updateBracketProvider(this,
                    (document, index) -> {
                        if (document.getDocumentVersion() != version || document.length() != length) return null;
                        PairedBracket pair = pairs.getPairedBracketAt(document, index);
                        return pair == null ? null : new PairedBracket(Math.min(pair.leftIndex, pair.rightIndex),
                                Math.max(pair.leftIndex, pair.rightIndex));
                    }));
            return Collections.emptyList();
        }
    }
}
