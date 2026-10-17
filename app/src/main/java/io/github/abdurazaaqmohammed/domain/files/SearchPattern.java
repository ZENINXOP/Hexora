package io.github.abdurazaaqmohammed.domain.files;

import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;

/** Cooperative work bounds for user-supplied regexes. OpenAI Codex assisted. */
public final class SearchPattern {
    private SearchPattern() {}

    public static Pattern compile(String expression, int flags) {
        if (expression.length() > 4096) throw new IllegalArgumentException("Search expression is too long.");
        try { return Pattern.compile(expression, flags); }
        catch (StackOverflowError error) { throw new IllegalArgumentException("Search expression is too complex.", error); }
    }

    public static boolean find(Pattern pattern, CharSequence text) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Search cancelled");
        try { return pattern.matcher(new CheckedText(text, 0, text.length(), new Budget(text.length()))).find(); }
        catch (StackOverflowError error) { throw new IllegalArgumentException("Search expression is too complex.", error); }
    }

    private static final class Budget {
        long remaining;
        final long deadline = System.nanoTime() + 250_000_000L;
        Budget(int length) { remaining = Math.min(64_000_000L, 4_000_000L + (long) length * 32); }
        void step() {
            if (--remaining <= 0) throw new IllegalArgumentException("Search expression exceeded its work limit.");
            if ((remaining & 1023) != 0) return;
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("Search cancelled");
            if (System.nanoTime() - deadline >= 0)
                throw new IllegalArgumentException("Search expression took too long. Simplify the expression.");
        }
    }

    private static final class CheckedText implements CharSequence {
        final CharSequence text;
        final int start, end;
        final Budget budget;
        CheckedText(CharSequence text, int start, int end, Budget budget) {
            this.text = text; this.start = start; this.end = end; this.budget = budget;
        }
        @Override public int length() { return end - start; }
        @Override public char charAt(int index) {
            if (index < 0 || index >= length()) throw new IndexOutOfBoundsException();
            budget.step();
            return text.charAt(start + index);
        }
        @Override public CharSequence subSequence(int from, int to) {
            if (from < 0 || to < from || to > length()) throw new IndexOutOfBoundsException();
            return new CheckedText(text, start + from, start + to, budget);
        }
        @Override public String toString() { return text.subSequence(start, end).toString(); }
    }
}
