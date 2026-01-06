package com.example.emrtdreader.sdk.ocr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

/** Lightweight MRZ-oriented preprocessing: grayscale + contrast. */
public final class MrzPreprocessor {
    private static final float TESSERACT_SCALE_FACTOR = 2.0f;

    private MrzPreprocessor() {}

    public static Bitmap preprocess(Bitmap src) {
        if (src == null) return null;
        Bitmap gray = toGrayscale(src);
        return increaseContrast(gray, 1.8f);
    }

    public static Bitmap preprocessForMl(Bitmap src) {
        return preprocess(src);
    }

    public static Bitmap preprocessForTesseract(Bitmap src) {
        Bitmap pre = preprocess(src);
        Bitmap scaled = scaleForTesseract(pre);
        return AdaptiveThreshold.binarize(scaled);
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

    private static Bitmap scaleForTesseract(Bitmap src) {
        if (src == null) return null;
        int width = Math.max(1, Math.round(src.getWidth() * TESSERACT_SCALE_FACTOR));
        int height = Math.max(1, Math.round(src.getHeight() * TESSERACT_SCALE_FACTOR));
        if (width == src.getWidth() && height == src.getHeight()) {
            return src;
        }
        return Bitmap.createScaledBitmap(src, width, height, true);
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
