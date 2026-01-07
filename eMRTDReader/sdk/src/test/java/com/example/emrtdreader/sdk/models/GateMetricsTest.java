package com.example.emrtdreader.sdk.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GateMetricsTest {
    @Test
    public void constructorSetsFields() {
        GateMetrics metrics = new GateMetrics(120.5f, 15.25f, 2.75f, 0.4f);

        assertEquals(120.5f, metrics.brightnessMean, 0.0001f);
        assertEquals(15.25f, metrics.contrastStd, 0.0001f);
        assertEquals(2.75f, metrics.blurVarLap, 0.0001f);
        assertEquals(0.4f, metrics.motionMad, 0.0001f);
    }

    @Test
    public void constructorPreservesNegativeValues() {
        GateMetrics metrics = new GateMetrics(-1.5f, -0.25f, -3.75f, -0.1f);

        assertEquals(-1.5f, metrics.brightnessMean, 0.0001f);
        assertEquals(-0.25f, metrics.contrastStd, 0.0001f);
        assertEquals(-3.75f, metrics.blurVarLap, 0.0001f);
        assertEquals(-0.1f, metrics.motionMad, 0.0001f);
    }
}
