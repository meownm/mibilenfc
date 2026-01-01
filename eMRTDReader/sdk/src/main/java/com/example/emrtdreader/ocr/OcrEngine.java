package com.example.emrtdreader.ocr;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.emrtdreader.models.OcrResult;

public interface OcrEngine {
    String getName();
    boolean isAvailable(Context ctx);
    OcrResult recognize(Context ctx, Bitmap bitmap, int rotationDegrees);
    void close();
}
