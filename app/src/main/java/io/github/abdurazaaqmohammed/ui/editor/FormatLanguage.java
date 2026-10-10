package io.github.abdurazaaqmohammed.ui.editor;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.abdurazaaqmohammed.domain.editor.SyntaxFormat;
import io.github.abdurazaaqmohammed.domain.editor.SyntaxLexer;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager;
import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager.LineTokenizeResult;
import io.github.rosemoe.sora.lang.styling.CodeBlock;
import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.TextStyle;
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
        @Override public List<CodeBlock> computeBlocks(Content text, CodeBlockAnalyzeDelegate delegate) { return Collections.emptyList(); }
    }
}
