package com.example.emrtdreader.sdk.ocr;

import android.graphics.Bitmap;

import com.example.emrtdreader.sdk.models.OcrMetrics;

public final class OcrQuality {
    private OcrQuality() {}

    public static OcrMetrics compute(Bitmap bmp) {
        FrameStats stats = FrameStats.compute(bmp);
        return new OcrMetrics(stats.brightness, stats.contrast, stats.sharpness);
    }
}
