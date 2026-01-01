package com.example.emrtdreader.ocr;

import android.graphics.Bitmap;

/**
 * Chooses between grayscale and binarized images using a simple quality metric.
 * Prevents cases where thresholding makes OCR worse.
 */
public final class ThresholdSelector {
    private ThresholdSelector() {}

    public static Bitmap choose(Bitmap gray, Bitmap binary) {
        if (gray == null) return binary;
        if (binary == null) return gray;

        float sharpGray = OcrQuality.compute(gray).sharpness;
        float sharpBin = OcrQuality.compute(binary).sharpness;

        // If binarized is noticeably worse, keep grayscale.
        return (sharpBin < sharpGray * 0.9f) ? gray : binary;
    }
}
