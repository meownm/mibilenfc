package com.example.emrtdreader.sdk.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MrzTrackerIntegrationTest {
    @Test
    public void trackingSequenceBecomesStableAfterThreshold() {
        MrzTracker tracker = new MrzTracker(1f);
        MrzBox box = new MrzBox(0f, 0f, 2f, 2f);

        TrackResult first = tracker.track(box);
        TrackResult second = tracker.track(box);
        TrackResult third = tracker.track(box);
        TrackResult fourth = tracker.track(box);

        assertEquals(0, first.stableCount);
        assertFalse(first.stable);
        assertEquals(1, second.stableCount);
        assertFalse(second.stable);
        assertEquals(2, third.stableCount);
        assertFalse(third.stable);
        assertEquals(3, fourth.stableCount);
        assertTrue(fourth.stable);
    }
}
