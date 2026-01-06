package com.example.emrtdreader.sdk.ocr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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

    public static Bitmap preprocessForMl(Bitmap src) {
        return preprocess(src);
    }

    public static Bitmap preprocessForMlMinimal(Bitmap src) {
        return src;
    }

    public static Bitmap preprocessForTesseract(Bitmap src) {
        return preprocessForTesseract(src, PreprocessParamSet.getDefault());
    }

    public static Bitmap preprocessForTesseract(Bitmap src, PreprocessParams params) {
        if (params == null) {
            return preprocessForTesseract(src);
        }
        Bitmap pre = preprocess(src);
        Bitmap blurred = blur(pre, params.blurRadius);
        Bitmap scaled = scaleForTesseract(blurred, params.scale);
        return AdaptiveThreshold.binarize(scaled, params.blockSize, params.c);
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

    private static Bitmap scaleForTesseract(Bitmap src, float scaleFactor) {
        if (src == null) return null;
        int width = Math.max(1, Math.round(src.getWidth() * scaleFactor));
        int height = Math.max(1, Math.round(src.getHeight() * scaleFactor));
        if (width == src.getWidth() && height == src.getHeight()) {
            return src;
        }
        return Bitmap.createScaledBitmap(src, width, height, true);
    }

    private static Bitmap blur(Bitmap src, int radius) {
        if (src == null || radius <= 0) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sum = 0;
                int count = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    for (int dx = -radius; dx <= radius; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= w) continue;
                        sum += Color.red(src.getPixel(xx, yy));
                        count++;
                    }
                }
                int mean = sum / Math.max(1, count);
                out.setPixel(x, y, Color.rgb(mean, mean, mean));
            }
        }
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
