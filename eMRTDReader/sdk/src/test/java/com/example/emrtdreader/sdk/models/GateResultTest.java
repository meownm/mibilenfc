package com.example.emrtdreader.sdk.models;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class GateResultTest {
    @Test
    public void constructorSetsFieldsForPassingGate() {
        GateMetrics metrics = new GateMetrics(110.0f, 12.0f, 3.5f, 0.2f);
        EnumSet<GateRejectReason> reasons = EnumSet.noneOf(GateRejectReason.class);

        GateResult result = new GateResult(true, metrics, reasons);

        assertEquals(true, result.pass);
        assertSame(metrics, result.metrics);
        assertSame(reasons, result.reasons);
    }

    @Test
    public void constructorSetsFieldsForRejectedGate() {
        GateMetrics metrics = new GateMetrics(45.0f, 2.0f, 18.0f, 1.1f);
        EnumSet<GateRejectReason> reasons = EnumSet.of(GateRejectReason.LOW_BRIGHTNESS, GateRejectReason.HIGH_BLUR);

        GateResult result = new GateResult(false, metrics, reasons);

        assertEquals(false, result.pass);
        assertSame(metrics, result.metrics);
        assertSame(reasons, result.reasons);
    }
}
