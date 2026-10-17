package io.github.abdurazaaqmohammed.domain.files;

import org.junit.Test;
import static org.junit.Assert.*;

public class SearchPatternTest {
    @Test public void preservesOrdinaryRegexAndCaseMatching() {
        assertTrue(SearchPattern.find(SearchPattern.compile("needle[0-9]+", java.util.regex.Pattern.CASE_INSENSITIVE), "a NEEDLE42 b"));
        assertFalse(SearchPattern.find(SearchPattern.compile("^needle$", 0), "a needle b"));
    }

    @Test(timeout = 3000) public void pathologicalRegexCannotRunIndefinitely() {
        char[] chars = new char[40000];
        java.util.Arrays.fill(chars, 'a');
        try {
            SearchPattern.find(SearchPattern.compile("(a+)+$", 0), new String(chars) + "!");
            fail("Expected work limit");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test public void cancellationIsCheckedBeforeMatchingEvenShortInputs() {
        Thread.currentThread().interrupt();
        try {
            SearchPattern.find(SearchPattern.compile("a", 0), "a");
            fail("Expected cancellation");
        } catch (java.util.concurrent.CancellationException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
