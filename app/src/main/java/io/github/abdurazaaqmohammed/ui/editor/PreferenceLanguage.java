package io.github.abdurazaaqmohammed.ui.editor;

import android.os.Bundle;

import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.QuickQuoteHandler;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.format.Formatter;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.SymbolPairMatch;

/** Wrap at language creation, so Sora never destroys a delegate before it is reused. */
public final class PreferenceLanguage implements Language {
    private final Language delegate;
    private final EmptyLanguage unstyled = new EmptyLanguage();
    public final boolean highlighted;
    private boolean spaces = true;
    private boolean autoIndent = true;
    private int tabSize = 4;

    public PreferenceLanguage(Language delegate, boolean highlighted) {
        this.delegate = delegate;
        this.highlighted = highlighted;
    }

    public void configure(boolean spaces, boolean autoIndent, int tabSize) {
        this.spaces = spaces;
        this.autoIndent = autoIndent;
        this.tabSize = tabSize;
        if (delegate instanceof TextMateLanguage tm) {
            tm.setTabSize(tabSize);
            tm.useTab(!spaces);
        }
        if (delegate instanceof modder.hub.dexeditor.utils.CustomAutoComplete custom)
            custom.configureIndentation(!spaces, tabSize);
    }

    @Override public AnalyzeManager getAnalyzeManager() { return highlighted ? delegate.getAnalyzeManager() : unstyled.getAnalyzeManager(); }
    @Override public int getInterruptionLevel() { return delegate.getInterruptionLevel(); }
    @Override public void requireAutoComplete(ContentReference text, CharPosition position, CompletionPublisher publisher, Bundle extras) {
        delegate.requireAutoComplete(text, position, publisher, extras);
    }
    @Override public int getIndentAdvance(ContentReference text, int line, int column) {
        if (!autoIndent) return 0;
        int advance = delegate.getIndentAdvance(text, line, column);
        return delegate instanceof JavaLanguage ? advance * tabSize / 4 : advance;
    }
    @Override public int getIndentAdvance(ContentReference text, int startLine, int startColumn, int endLine, int endColumn) {
        if (!autoIndent) return 0;
        int advance = delegate.getIndentAdvance(text, startLine, startColumn, endLine, endColumn);
        return delegate instanceof JavaLanguage ? advance * tabSize / 4 : advance;
    }
    @Override public boolean useTab() { return !spaces; }
    @Override public Formatter getFormatter() { return delegate.getFormatter(); }
    @Override public SymbolPairMatch getSymbolPairs() { return delegate.getSymbolPairs(); }
    @Override public NewlineHandler[] getNewlineHandlers() {
        // Java's special enter handlers hard-code spaces; the normal enter path
        // respects our indentation choice and configured tab width.
        return !autoIndent || delegate instanceof JavaLanguage ? null : delegate.getNewlineHandlers();
    }
    @Override public QuickQuoteHandler getQuickQuoteHandler() { return delegate.getQuickQuoteHandler(); }
    @Override public void destroy() { delegate.destroy(); unstyled.destroy(); }
}
