package com.example.emrtdreader.sdk.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.emrtdreader.sdk.models.GateMetrics;

import org.junit.Test;

public class MrzFrameGateTest {

    @Test
    public void evaluatePassesBalancedFrame() {
        byte[] frame = new byte[] {
                30, 60, 90,
                60, 120, 180,
                90, (byte) 180, (byte) 220
        };
        MrzFrameGate.Thresholds thresholds = new MrzFrameGate.Thresholds(
                20,
                230,
                5,
                0,
                50
        );
        MrzFrameGate gate = new MrzFrameGate(thresholds);

        MrzFrameGate.Result result = gate.evaluate(frame, 3, 3, frame);

        assertTrue(result.pass);
        assertTrue(result.metrics.brightnessMean > 20f);
        assertTrue(result.metrics.contrastStd > 0f);
    }

    @Test
    public void evaluateFailsDarkFrame() {
        byte[] frame = new byte[16];
        MrzFrameGate.Thresholds thresholds = new MrzFrameGate.Thresholds(
                40,
                200,
                0,
                0,
                50
        );
        MrzFrameGate gate = new MrzFrameGate(thresholds);

        MrzFrameGate.Result result = gate.evaluate(frame, 4, 4, null);

        assertFalse(result.pass);
    }

    @Test
    public void evaluateFailsBlurThreshold() {
        byte[] frame = new byte[9];
        MrzFrameGate.Thresholds thresholds = new MrzFrameGate.Thresholds(
                0,
                255,
                0,
                1,
                50
        );
        MrzFrameGate gate = new MrzFrameGate(thresholds);

        MrzFrameGate.Result result = gate.evaluate(frame, 3, 3, null);

        assertFalse(result.pass);
        assertEquals(0f, result.metrics.blurVarLap, 0.001f);
    }

    @Test
    public void evaluateFailsOnMotion() {
        byte[] current = new byte[] {0, 0, 0, 0};
        byte[] previous = new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255};
        MrzFrameGate.Thresholds thresholds = new MrzFrameGate.Thresholds(
                0,
                255,
                0,
                0,
                10
        );
        MrzFrameGate gate = new MrzFrameGate(thresholds);

        MrzFrameGate.Result result = gate.evaluate(current, 2, 2, previous);

        assertFalse(result.pass);
        assertTrue(result.metrics.motionMad > 10f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void computeMetricsRejectsInvalidInput() {
        byte[] frame = new byte[3];
        GateMetrics metrics = MrzFrameGate.computeMetrics(frame, 2, 2, null);
        assertEquals(0f, metrics.brightnessMean, 0.001f);
    }
}
