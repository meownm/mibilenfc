package com.example.emrtdreader.sdk.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

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

        MrzFrameGate.Result result = gate.evaluate(frame, 3, 3, frame, null);

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

        MrzFrameGate.Result result = gate.evaluate(frame, 4, 4, null, null);

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

        MrzFrameGate.Result result = gate.evaluate(frame, 3, 3, null, null);

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

        MrzFrameGate.Result result = gate.evaluate(current, 2, 2, previous, null);

        assertFalse(result.pass);
        assertTrue(result.metrics.motionMad > 10f);
    }

    @Test
    public void evaluatePassesWhenRoiIsStableDespiteBackgroundMotion() {
        byte[] current = new byte[] {
                20, 20, 20, 20, 20,
                20, 60, 80, 60, 20,
                20, 80, 120, 80, 20,
                20, 60, 80, 60, 20,
                20, 20, 20, 20, 20
        };
        byte[] previous = new byte[] {
                (byte) 200, (byte) 200, (byte) 200, (byte) 200, (byte) 200,
                20, 60, 80, 60, 20,
                20, 80, 120, 80, 20,
                20, 60, 80, 60, 20,
                (byte) 200, (byte) 200, (byte) 200, (byte) 200, (byte) 200
        };
        Rect roiHint = new Rect(1, 1, 4, 4);
        MrzFrameGate.Thresholds thresholds = new MrzFrameGate.Thresholds(
                0,
                255,
                0,
                1,
                5
        );
        MrzFrameGate gate = new MrzFrameGate(thresholds);

        MrzFrameGate.Result result = gate.evaluate(current, 5, 5, previous, roiHint);

        assertTrue(result.pass);
        assertEquals(0f, result.metrics.motionMad, 0.001f);
        assertTrue(result.metrics.blurVarLap > 0f);
    }

    @Test
    public void evaluateFailsWhenRoiMotionExceedsThreshold() {
        byte[] current = new byte[] {
                10, 10, 10, 10,
                10, 50, 50, 10,
                10, 50, 50, 10,
                10, 10, 10, 10
        };
        byte[] previous = new byte[] {
                10, 10, 10, 10,
                10, (byte) 200, (byte) 200, 10,
                10, (byte) 200, (byte) 200, 10,
                10, 10, 10, 10
        };
        Rect roiHint = new Rect(1, 1, 3, 3);
        MrzFrameGate.Thresholds thresholds = new MrzFrameGate.Thresholds(
                0,
                255,
                0,
                0,
                5
        );
        MrzFrameGate gate = new MrzFrameGate(thresholds);

        MrzFrameGate.Result result = gate.evaluate(current, 4, 4, previous, roiHint);

        assertFalse(result.pass);
        assertTrue(result.metrics.motionMad > 5f);
    }

    @Test
    public void evaluateFailsWhenRoiBlurBelowThreshold() {
        byte[] current = new byte[] {
                30, 30, 30, 30,
                30, 30, 30, 30,
                30, 30, 30, 30,
                30, 30, 30, 30
        };
        Rect roiHint = new Rect(0, 0, 3, 3);
        MrzFrameGate.Thresholds thresholds = new MrzFrameGate.Thresholds(
                0,
                255,
                0,
                1,
                100
        );
        MrzFrameGate gate = new MrzFrameGate(thresholds);

        MrzFrameGate.Result result = gate.evaluate(current, 4, 4, null, roiHint);

        assertFalse(result.pass);
        assertEquals(0f, result.metrics.blurVarLap, 0.001f);
    }

    @Test
    public void computeMetricsUsesRoiHintForMotion() {
        byte[] current = new byte[] {
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0
        };
        byte[] previous = new byte[] {
                (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                (byte) 255, (byte) 255, (byte) 255, (byte) 255,
                0, 0, 0, 0,
                0, 0, 0, 0
        };
        Rect roiHint = new Rect(0, 2, 4, 4);

        GateMetrics metrics = MrzFrameGate.computeMetrics(current, 4, 4, previous, roiHint);

        assertEquals(0f, metrics.motionMad, 0.001f);
    }

    @Test
    public void computeMetricsNormalizesLaplacianVarianceByArea() {
        byte[] frame = new byte[] {
                (byte) 197, (byte) 215, 20, (byte) 132,
                (byte) 248, (byte) 207, (byte) 155, (byte) 244,
                (byte) 183, (byte) 111, 71, (byte) 144,
                71, 48, (byte) 128, 75
        };
        Rect full = new Rect(0, 0, 4, 4);
        Rect center = new Rect(1, 1, 3, 3);

        GateMetrics fullMetrics = MrzFrameGate.computeMetrics(frame, 4, 4, null, full);
        GateMetrics centerMetrics = MrzFrameGate.computeMetrics(frame, 4, 4, null, center);

        assertEquals(1243.5156f, fullMetrics.blurVarLap, 0.01f);
        assertEquals(4974.0625f, centerMetrics.blurVarLap, 0.01f);
    }

    @Test
    public void computeMetricsHandlesTinyRoiSafely() {
        byte[] current = new byte[] {
                10, 10, 10, 10,
                10, 50, 50, 10,
                10, 50, 50, 10,
                10, 10, 10, 10
        };
        byte[] previous = new byte[] {
                10, (byte) 200, (byte) 200, (byte) 200,
                10, 50, 50, 10,
                10, 50, 50, 10,
                (byte) 200, (byte) 200, (byte) 200, (byte) 200
        };
        Rect roiHint = new Rect(0, 0, 1, 1);

        GateMetrics metrics = MrzFrameGate.computeMetrics(current, 4, 4, previous, roiHint);

        assertEquals(0f, metrics.blurVarLap, 0.001f);
        assertEquals(0f, metrics.motionMad, 0.001f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void computeMetricsRejectsInvalidInput() {
        byte[] frame = new byte[3];
        GateMetrics metrics = MrzFrameGate.computeMetrics(frame, 2, 2, null, null);
        assertEquals(0f, metrics.brightnessMean, 0.001f);
    }
}
