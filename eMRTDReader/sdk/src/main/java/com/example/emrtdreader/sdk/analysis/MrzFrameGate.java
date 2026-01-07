package com.example.emrtdreader.sdk.analysis;

import com.example.emrtdreader.sdk.models.GateMetrics;

/**
 * Computes frame quality metrics directly from the luma (Y) plane and applies
 * threshold gating for MRZ capture.
 */
public final class MrzFrameGate {

    public static final class Thresholds {
        public final double minBrightness;
        public final double maxBrightness;
        public final double minContrast;
        public final double minBlurVar;
        public final double maxMotion;

        public Thresholds(double minBrightness,
                          double maxBrightness,
                          double minContrast,
                          double minBlurVar,
                          double maxMotion) {
            if (minBrightness < 0 || maxBrightness < 0 || minContrast < 0 || minBlurVar < 0 || maxMotion < 0) {
                throw new IllegalArgumentException("Thresholds must be non-negative");
            }
            if (minBrightness > maxBrightness) {
                throw new IllegalArgumentException("minBrightness must be <= maxBrightness");
            }
            this.minBrightness = minBrightness;
            this.maxBrightness = maxBrightness;
            this.minContrast = minContrast;
            this.minBlurVar = minBlurVar;
            this.maxMotion = maxMotion;
        }
    }

    public static final class Result {
        public final boolean pass;
        public final GateMetrics metrics;

        Result(boolean pass, GateMetrics metrics) {
            this.pass = pass;
            this.metrics = metrics;
        }
    }

    private final Thresholds thresholds;

    public MrzFrameGate(Thresholds thresholds) {
        if (thresholds == null) {
            throw new IllegalArgumentException("thresholds cannot be null");
        }
        this.thresholds = thresholds;
    }

    public Result evaluate(byte[] yPlane, int width, int height, byte[] previousYPlane) {
        GateMetrics metrics = computeMetrics(yPlane, width, height, previousYPlane);
        boolean pass = metrics.brightnessMean >= thresholds.minBrightness
                && metrics.brightnessMean <= thresholds.maxBrightness
                && metrics.contrastStd >= thresholds.minContrast
                && metrics.blurVarLap >= thresholds.minBlurVar
                && metrics.motionMad <= thresholds.maxMotion;
        return new Result(pass, metrics);
    }

    public static GateMetrics computeMetrics(byte[] yPlane, int width, int height, byte[] previousYPlane) {
        if (yPlane == null) {
            throw new IllegalArgumentException("yPlane cannot be null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width/height must be positive");
        }
        int pixelCount = width * height;
        if (yPlane.length < pixelCount) {
            throw new IllegalArgumentException("yPlane is smaller than width*height");
        }

        double sum = 0;
        double sum2 = 0;
        for (int i = 0; i < pixelCount; i++) {
            int y = yPlane[i] & 0xFF;
            sum += y;
            sum2 += y * y;
        }
        double mean = sum / pixelCount;
        double variance = (sum2 / pixelCount) - mean * mean;
        double stddev = Math.sqrt(Math.max(0, variance));

        double lapSum = 0;
        double lapSum2 = 0;
        int lapCount = 0;
        if (width > 2 && height > 2) {
            for (int y = 1; y < height - 1; y++) {
                int row = y * width;
                for (int x = 1; x < width - 1; x++) {
                    int idx = row + x;
                    double c = yPlane[idx] & 0xFF;
                    double lap = (-4 * c
                            + (yPlane[idx - 1] & 0xFF)
                            + (yPlane[idx + 1] & 0xFF)
                            + (yPlane[idx - width] & 0xFF)
                            + (yPlane[idx + width] & 0xFF));
                    lapSum += lap;
                    lapSum2 += lap * lap;
                    lapCount++;
                }
            }
        }
        double lapMean = lapCount > 0 ? lapSum / lapCount : 0;
        double lapVar = lapCount > 0 ? (lapSum2 / lapCount - lapMean * lapMean) : 0;

        double motionMad = 0;
        if (previousYPlane != null && previousYPlane.length >= pixelCount) {
            double motionSum = 0;
            for (int i = 0; i < pixelCount; i++) {
                int curr = yPlane[i] & 0xFF;
                int prev = previousYPlane[i] & 0xFF;
                motionSum += Math.abs(curr - prev);
            }
            motionMad = motionSum / pixelCount;
        }

        return new GateMetrics((float) mean, (float) stddev, (float) Math.max(0, lapVar), (float) motionMad);
    }
}
