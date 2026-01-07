package com.example.emrtdreader.sdk.models;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.assertEquals;

public class GateMetricsIntegrationTest {
    @Test
    public void serializationRoundTripPreservesValues() throws Exception {
        GateMetrics original = new GateMetrics(100.1f, 5.5f, 12.25f, 0.35f);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(original);
        }

        GateMetrics decoded;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            decoded = (GateMetrics) ois.readObject();
        }

        assertEquals(original.brightnessMean, decoded.brightnessMean, 0.0001f);
        assertEquals(original.contrastStd, decoded.contrastStd, 0.0001f);
        assertEquals(original.blurVarLap, decoded.blurVarLap, 0.0001f);
        assertEquals(original.motionMad, decoded.motionMad, 0.0001f);
    }
}
