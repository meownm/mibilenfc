package com.example.emrtdreader.sdk.models;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.assertEquals;

public class TrackResultIntegrationTest {
    @Test
    public void serializationRoundTripPreservesFields() throws Exception {
        TrackResult original = new TrackResult(false, 3, 0.75f, new MrzBox(1f, 2f, 3f, 4f));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(original);
        }

        TrackResult decoded;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            decoded = (TrackResult) ois.readObject();
        }

        assertEquals(original.stable, decoded.stable);
        assertEquals(original.stableCount, decoded.stableCount);
        assertEquals(original.jitter, decoded.jitter, 0.0001f);
        assertEquals(original.box.left, decoded.box.left, 0.0001f);
        assertEquals(original.box.top, decoded.box.top, 0.0001f);
        assertEquals(original.box.right, decoded.box.right, 0.0001f);
        assertEquals(original.box.bottom, decoded.box.bottom, 0.0001f);
    }
}
