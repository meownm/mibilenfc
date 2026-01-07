package com.example.emrtdreader.sdk.ocr;

import android.graphics.Bitmap;

public final class PreprocessedMrz {
    public final Bitmap bitmap;
    public final int rotationDegrees;

    public PreprocessedMrz(Bitmap bitmap, int rotationDegrees) {
        this.bitmap = bitmap;
        this.rotationDegrees = rotationDegrees;
    }
}
