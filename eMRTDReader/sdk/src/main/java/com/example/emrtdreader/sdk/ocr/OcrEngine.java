package com.example.emrtdreader.sdk.ocr;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.emrtdreader.sdk.models.OcrResult;

public interface OcrEngine {
    interface Callback {
        void onSuccess(OcrResult result);
        void onFailure(Throwable error);
    }

    String getName();
    boolean isAvailable(Context ctx);
    void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback);
    void close();
}
