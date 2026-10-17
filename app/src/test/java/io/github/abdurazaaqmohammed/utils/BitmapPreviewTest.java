package io.github.abdurazaaqmohammed.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class BitmapPreviewTest {
    @Test public void panoramasAndTallImagesAreBoundedByTheirLongestEdge() {
        assertEquals(1024, BitmapPreview.sampleSize(100000, 128, 192));
        assertEquals(1024, BitmapPreview.sampleSize(128, 100000, 192));
    }
    @Test public void boundaryImagesCannotDecodeAtTwiceTheRequestedSize() {
        assertEquals(1, BitmapPreview.sampleSize(2048, 1024, 2048));
        assertEquals(2, BitmapPreview.sampleSize(2049, 1024, 2048));
    }
    @Test public void extremeDimensionsDoNotOverflowTheSamplingLoop() {
        int sample = BitmapPreview.sampleSize(Integer.MAX_VALUE, 1, 192);
        assertTrue(sample > 0);
        assertTrue((Integer.MAX_VALUE + (long) sample - 1) / sample <= 192);
    }
}
