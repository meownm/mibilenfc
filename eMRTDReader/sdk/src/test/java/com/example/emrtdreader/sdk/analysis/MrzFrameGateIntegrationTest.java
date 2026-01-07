package com.example.emrtdreader.sdk.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.emrtdreader.sdk.models.GateMetrics;

import org.junit.Test;

public class MrzFrameGateIntegrationTest {

    @Test
    public void metricsReflectMotionBetweenFrames() {
        byte[] current = new byte[] {
                10, 20, 30,
                40, 50, 60,
                70, 80, 90
        };
        byte[] previous = new byte[] {
                11, 21, 31,
                41, 51, 61,
                71, 81, 91
        };

        GateMetrics metrics = MrzFrameGate.computeMetrics(current, 3, 3, previous);

        assertEquals(50f, metrics.brightnessMean, 0.001f);
        assertEquals(1f, metrics.motionMad, 0.001f);
        assertTrue(metrics.contrastStd > 0f);
    }
}
