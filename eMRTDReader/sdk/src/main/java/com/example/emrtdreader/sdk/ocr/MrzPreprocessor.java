package com.example.emrtdreader.sdk.ocr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

/** Lightweight MRZ-oriented preprocessing: grayscale + contrast. */
public final class MrzPreprocessor {
    private MrzPreprocessor() {}

    public static Bitmap preprocess(Bitmap src) {
        if (src == null) return null;
        Bitmap gray = toGrayscale(src);
        return increaseContrast(gray, 1.8f);
    }

    private static Bitmap toGrayscale(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0f);
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);
        return out;
    }

    private static Bitmap increaseContrast(Bitmap src, float factor) {
        float translate = -128f * (factor - 1f);
        ColorMatrix cm = new ColorMatrix(new float[]{
                factor, 0, 0, 0, translate,
                0, factor, 0, 0, translate,
                0, 0, factor, 0, translate,
                0, 0, 0, 1, 0
        });

        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);
        return out;
    }
}
