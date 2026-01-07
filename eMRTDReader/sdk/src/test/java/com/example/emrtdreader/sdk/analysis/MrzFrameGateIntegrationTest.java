package com.example.emrtdreader.sdk.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

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

        GateMetrics metrics = MrzFrameGate.computeMetrics(current, 3, 3, previous, null);

        assertEquals(50f, metrics.brightnessMean, 0.001f);
        assertEquals(1f, metrics.motionMad, 0.001f);
        assertTrue(metrics.contrastStd > 0f);
    }

    @Test
    public void metricsHonorRoiHintForMotion() {
        byte[] current = new byte[] {
                10, 10, 10,
                10, 10, 10,
                10, 10, 10
        };
        byte[] previous = new byte[] {
                20, 20, 20,
                10, 10, 10,
                10, 10, 10
        };
        Rect roiHint = new Rect(0, 1, 3, 3);

        GateMetrics metrics = MrzFrameGate.computeMetrics(current, 3, 3, previous, roiHint);

        assertEquals(0f, metrics.motionMad, 0.001f);
    }
}
