package com.example.emrtdreader.sdk.ocr;

import android.graphics.Bitmap;

/**
 * Computes frame-level image statistics directly from bitmap pixels.
 */
public final class FrameStats {
    public final double brightness;
    public final double contrast;
    public final double sharpness;
    public final double noise;

    public FrameStats(double brightness, double contrast, double sharpness, double noise) {
        this.brightness = brightness;
        this.contrast = contrast;
        this.sharpness = sharpness;
        this.noise = noise;
    }

    public static FrameStats compute(Bitmap bitmap) {
        if (bitmap == null) {
            return new FrameStats(0, 0, 0, 0);
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) {
            return new FrameStats(0, 0, 0, 0);
        }

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        double[] luma = new double[pixels.length];
        double sum = 0;
        double sum2 = 0;

        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            double y = 0.299 * r + 0.587 * g + 0.114 * b;
            luma[i] = y;
            sum += y;
            sum2 += y * y;
        }

        double mean = sum / luma.length;
        double variance = (sum2 / luma.length) - mean * mean;
        double stddev = Math.sqrt(Math.max(0, variance));

        double lapSum = 0;
        double lapSum2 = 0;
        int lapCount = 0;
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int idx = y * width + x;
                double c = luma[idx];
                double lap = (-4 * c
                        + luma[idx - 1]
                        + luma[idx + 1]
                        + luma[idx - width]
                        + luma[idx + width]);
                lapSum += lap;
                lapSum2 += lap * lap;
                lapCount++;
            }
        }
        double lapMean = lapCount > 0 ? lapSum / lapCount : 0;
        double lapVar = lapCount > 0 ? (lapSum2 / lapCount - lapMean * lapMean) : 0;

        double noiseSum2 = 0;
        int noiseCount = 0;
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int idx = y * width + x;
                double neighborSum =
                        luma[idx - width - 1] + luma[idx - width] + luma[idx - width + 1]
                                + luma[idx - 1] + luma[idx] + luma[idx + 1]
                                + luma[idx + width - 1] + luma[idx + width] + luma[idx + width + 1];
                double localMean = neighborSum / 9.0;
                double residual = luma[idx] - localMean;
                noiseSum2 += residual * residual;
                noiseCount++;
            }
        }
        double noiseStd = noiseCount > 0 ? Math.sqrt(noiseSum2 / noiseCount) : 0;

        return new FrameStats(mean, stddev, Math.max(0, lapVar), noiseStd);
    }
}
