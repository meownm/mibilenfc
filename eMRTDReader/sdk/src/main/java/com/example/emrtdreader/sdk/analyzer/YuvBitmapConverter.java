package com.example.emrtdreader.sdk.analyzer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.media.Image;

import androidx.camera.core.ImageProxy;
import androidx.camera.core.internal.YuvToRgbConverter;

final class YuvBitmapConverter {
    static final int MIN_AVG_LUMA = 70;
    static final int MAX_AVG_LUMA = 200;

    interface Converter {
        void yuvToRgb(Image image, Bitmap bitmap);
    }

    private final Converter converter;

    YuvBitmapConverter(Converter converter) {
        this.converter = converter;
    }

    YuvBitmapConverter(Context context) {
        YuvToRgbConverter yuvConverter = new YuvToRgbConverter(context);
        this.converter = yuvConverter::yuvToRgb;
    }

    Bitmap toBitmap(ImageProxy imageProxy) {
        try {
            Image image = imageProxy.getImage();
            if (image == null) {
                throw new IllegalStateException("Image conversion failed");
            }
            Bitmap bitmap = Bitmap.createBitmap(
                    imageProxy.getWidth(),
                    imageProxy.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            converter.yuvToRgb(image, bitmap);
            normalizeBrightness(bitmap);
            return bitmap;
        } catch (Throwable t) {
            if (t instanceof IllegalStateException && "Image conversion failed".equals(t.getMessage())) {
                throw (IllegalStateException) t;
            }
            throw new IllegalStateException("Image conversion failed", t);
        }
    }

    private static void normalizeBrightness(Bitmap bitmap) {
        float avgLuma = averageLuma(bitmap);
        if (avgLuma <= 0f) {
            return;
        }
        float scale;
        if (avgLuma < MIN_AVG_LUMA) {
            scale = MIN_AVG_LUMA / avgLuma;
        } else if (avgLuma > MAX_AVG_LUMA) {
            scale = MAX_AVG_LUMA / avgLuma;
        } else {
            return;
        }
        scale = Math.max(0.5f, Math.min(2.0f, scale));
        applyLumaScale(bitmap, scale);
    }

    private static float averageLuma(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int step = Math.max(1, Math.min(width, height) / 50);
        long sum = 0L;
        long count = 0L;
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int color = bitmap.getPixel(x, y);
                int luma = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3;
                sum += luma;
                count++;
            }
        }
        return count == 0L ? 0f : (float) sum / (float) count;
    }

    private static void applyLumaScale(Bitmap bitmap, float scale) {
        Bitmap adjusted = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
        Canvas canvas = new Canvas(adjusted);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ColorMatrix matrix = new ColorMatrix();
        matrix.setScale(scale, scale, scale, 1f);
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        bitmap.eraseColor(Color.TRANSPARENT);
        Canvas out = new Canvas(bitmap);
        out.drawBitmap(adjusted, 0, 0, null);
    }
}
