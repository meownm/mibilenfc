package com.example.emrtdreader.sdk.analyzer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.graphics.ImageFormat;

import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Converts {@link ImageProxy} YUV frames to mutable ARGB bitmaps using public YUV APIs,
 * then normalizes brightness to keep text readable for MRZ detection and OCR.
 */
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
        this.converter = new Yuv420888Converter();
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

    private static final class Yuv420888Converter implements Converter {
        @Override
        public void yuvToRgb(Image image, Bitmap bitmap) {
            if (image.getFormat() != ImageFormat.YUV_420_888) {
                throw new IllegalArgumentException("Unsupported format: " + image.getFormat());
            }
            int width = image.getWidth();
            int height = image.getHeight();
            byte[] nv21 = yuv420888ToNv21(image, width, height);
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean success = yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
            if (!success) {
                throw new IllegalStateException("YUV to JPEG conversion failed");
            }
            byte[] jpeg = out.toByteArray();
            Bitmap decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            if (decoded == null) {
                throw new IllegalStateException("Decoded bitmap was null");
            }
            Canvas canvas = new Canvas(bitmap);
            canvas.drawBitmap(decoded, 0, 0, null);
        }

        private static byte[] yuv420888ToNv21(Image image, int width, int height) {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer().duplicate();
            ByteBuffer uBuffer = planes[1].getBuffer().duplicate();
            ByteBuffer vBuffer = planes[2].getBuffer().duplicate();

            int yRowStride = planes[0].getRowStride();
            int yPixelStride = planes[0].getPixelStride();
            int uvRowStride = planes[1].getRowStride();
            int uvPixelStride = planes[1].getPixelStride();

            byte[] nv21 = new byte[width * height + (width * height) / 2];
            int pos = 0;
            for (int row = 0; row < height; row++) {
                int rowOffset = row * yRowStride;
                for (int col = 0; col < width; col++) {
                    nv21[pos++] = yBuffer.get(rowOffset + col * yPixelStride);
                }
            }

            int chromaHeight = height / 2;
            int chromaWidth = width / 2;
            for (int row = 0; row < chromaHeight; row++) {
                int rowOffset = row * uvRowStride;
                for (int col = 0; col < chromaWidth; col++) {
                    int offset = rowOffset + col * uvPixelStride;
                    nv21[pos++] = vBuffer.get(offset);
                    nv21[pos++] = uBuffer.get(offset);
                }
            }
            return nv21;
        }
    }
}
