package com.example.emrtdreader.sdk.ocr;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.emrtdreader.sdk.models.OcrResult;

public interface OcrEngine {
    String getName();
    boolean isAvailable(Context ctx);
    OcrResult recognize(Context ctx, Bitmap bitmap, int rotationDegrees);
    void close();
}
