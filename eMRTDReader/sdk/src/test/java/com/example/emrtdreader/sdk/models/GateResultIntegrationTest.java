package com.example.emrtdreader.sdk.models;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GateResultIntegrationTest {
    @Test
    public void serializationRoundTripPreservesValues() throws Exception {
        GateMetrics metrics = new GateMetrics(90.5f, 8.25f, 4.75f, 0.6f);
        EnumSet<GateRejectReason> reasons = EnumSet.of(GateRejectReason.LOW_CONTRAST, GateRejectReason.HIGH_MOTION);
        GateResult original = new GateResult(false, metrics, reasons);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(original);
        }

        GateResult decoded;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            decoded = (GateResult) ois.readObject();
        }

        assertEquals(original.pass, decoded.pass);
        assertEquals(original.metrics.brightnessMean, decoded.metrics.brightnessMean, 0.0001f);
        assertEquals(original.metrics.contrastStd, decoded.metrics.contrastStd, 0.0001f);
        assertEquals(original.metrics.blurVarLap, decoded.metrics.blurVarLap, 0.0001f);
        assertEquals(original.metrics.motionMad, decoded.metrics.motionMad, 0.0001f);
        assertEquals(original.reasons.size(), decoded.reasons.size());
        assertTrue(decoded.reasons.contains(GateRejectReason.LOW_CONTRAST));
        assertTrue(decoded.reasons.contains(GateRejectReason.HIGH_MOTION));
    }
}
