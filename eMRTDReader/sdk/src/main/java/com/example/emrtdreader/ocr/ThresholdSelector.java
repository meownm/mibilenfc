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

        double sharpGray = OcrQuality.compute(gray).sharpness;
        double sharpBin = OcrQuality.compute(binary).sharpness;

        // If binarized is noticeably worse, keep grayscale.
        return (sharpBin < sharpGray * 0.9) ? gray : binary;
    }
}
