package com.example.emrtdreader.sdk.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MrzTrackerTest {
    @Test
    public void constructorRejectsInvalidAlpha() {
        assertThrows(IllegalArgumentException.class, () -> new MrzTracker(0f));
        assertThrows(IllegalArgumentException.class, () -> new MrzTracker(1.1f));
    }

    @Test
    public void trackRejectsNullCurrent() {
        MrzTracker tracker = new MrzTracker();

        assertThrows(IllegalArgumentException.class, () -> tracker.track(null));
    }

    @Test
    public void trackAppliesEmaSmoothing() {
        MrzTracker tracker = new MrzTracker(0.5f);
        MrzBox first = new MrzBox(0f, 0f, 2f, 2f);
        MrzBox second = new MrzBox(2f, 2f, 4f, 4f);

        tracker.track(first);
        TrackResult result = tracker.track(second);

        assertEquals(1f, result.box.left, 0.0001f);
        assertEquals(1f, result.box.top, 0.0001f);
        assertEquals(3f, result.box.right, 0.0001f);
        assertEquals(3f, result.box.bottom, 0.0001f);
        assertEquals(0, result.stableCount);
        assertFalse(result.stable);
    }

    @Test
    public void stableCountResetsWhenIouBelowThreshold() {
        MrzTracker tracker = new MrzTracker(1f);
        MrzBox first = new MrzBox(0f, 0f, 2f, 2f);
        MrzBox same = new MrzBox(0f, 0f, 2f, 2f);
        MrzBox far = new MrzBox(5f, 5f, 7f, 7f);

        tracker.track(first);
        TrackResult stableResult = tracker.track(same);
        TrackResult resetResult = tracker.track(far);

        assertEquals(1, stableResult.stableCount);
        assertFalse(stableResult.stable);
        assertEquals(0, resetResult.stableCount);
        assertFalse(resetResult.stable);
    }
}
