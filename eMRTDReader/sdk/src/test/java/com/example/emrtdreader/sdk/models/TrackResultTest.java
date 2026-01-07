package com.example.emrtdreader.sdk.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class TrackResultTest {
    @Test
    public void constructorSetsFields() {
        MrzBox box = new MrzBox(0f, 1f, 2f, 3f);
        TrackResult result = new TrackResult(true, 4, 1.25f, box);

        assertEquals(true, result.stable);
        assertEquals(4, result.stableCount);
        assertEquals(1.25f, result.jitter, 0.0001f);
        assertSame(box, result.box);
    }

    @Test
    public void constructorRejectsInvalidInputs() {
        MrzBox box = new MrzBox(0f, 0f, 1f, 1f);

        assertThrows(IllegalArgumentException.class, () -> new TrackResult(true, -1, 0.5f, box));
        assertThrows(IllegalArgumentException.class, () -> new TrackResult(true, 1, -0.1f, box));
        assertThrows(IllegalArgumentException.class, () -> new TrackResult(true, 1, 0.1f, null));
    }
}
