package com.example.emrtdreader.sdk.analysis;

import android.graphics.Rect;

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

    public Result evaluate(byte[] yPlane,
                           int width,
                           int height,
                           byte[] previousYPlane,
                           Rect roiHint) {
        GateMetrics metrics = computeMetrics(yPlane, width, height, previousYPlane, roiHint);
        boolean pass = metrics.brightnessMean >= thresholds.minBrightness
                && metrics.brightnessMean <= thresholds.maxBrightness
                && metrics.contrastStd >= thresholds.minContrast
                && metrics.blurVarLap >= thresholds.minBlurVar
                && metrics.motionMad <= thresholds.maxMotion;
        return new Result(pass, metrics);
    }

    public static GateMetrics computeMetrics(byte[] yPlane,
                                             int width,
                                             int height,
                                             byte[] previousYPlane,
                                             Rect roiHint) {
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

        Rect roi = resolveRoi(width, height, roiHint);
        double lapVar = computeLaplacianVarianceROI(yPlane, width, height, roi);
        double motionMad = computeMadROI(yPlane, previousYPlane, width, height, roi);

        return new GateMetrics((float) mean, (float) stddev, (float) lapVar, (float) motionMad);
    }

    private static Rect resolveRoi(int width, int height, Rect roiHint) {
        if (roiHint == null) {
            int top = (int) (height * 0.6f);
            return new Rect(0, top, width, height);
        }

        int left = clamp(roiHint.left, 0, width);
        int top = clamp(roiHint.top, 0, height);
        int right = clamp(roiHint.right, 0, width);
        int bottom = clamp(roiHint.bottom, 0, height);

        if (right < left) {
            right = left;
        }
        if (bottom < top) {
            bottom = top;
        }

        return new Rect(left, top, right, bottom);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double computeMadROI(byte[] yPlane,
                                        byte[] previousYPlane,
                                        int width,
                                        int height,
                                        Rect roi) {
        if (previousYPlane == null || previousYPlane.length < width * height) {
            return 0;
        }

        int roiWidth = roi.width();
        int roiHeight = roi.height();
        int area = roiWidth * roiHeight;
        if (area <= 0) {
            return 0;
        }

        double motionSum = 0;
        for (int y = roi.top; y < roi.bottom; y++) {
            int row = y * width;
            for (int x = roi.left; x < roi.right; x++) {
                int idx = row + x;
                int curr = yPlane[idx] & 0xFF;
                int prev = previousYPlane[idx] & 0xFF;
                motionSum += Math.abs(curr - prev);
            }
        }

        return motionSum / area;
    }

    private static double computeLaplacianVarianceROI(byte[] yPlane,
                                                      int width,
                                                      int height,
                                                      Rect roi) {
        int roiWidth = roi.width();
        int roiHeight = roi.height();
        int area = roiWidth * roiHeight;
        if (area <= 0 || width <= 2 || height <= 2) {
            return 0;
        }

        int xStart = Math.max(roi.left, 1);
        int yStart = Math.max(roi.top, 1);
        int xEnd = Math.min(roi.right - 1, width - 2);
        int yEnd = Math.min(roi.bottom - 1, height - 2);

        if (xStart > xEnd || yStart > yEnd) {
            return 0;
        }

        double lapSum = 0;
        double lapSum2 = 0;
        int lapCount = 0;
        for (int y = yStart; y <= yEnd; y++) {
            int row = y * width;
            for (int x = xStart; x <= xEnd; x++) {
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

        if (lapCount == 0) {
            return 0;
        }

        double lapMean = lapSum / lapCount;
        double lapVar = (lapSum2 / lapCount) - lapMean * lapMean;
        double normalized = lapVar / area;
        return Math.max(0, normalized);
    }
}
