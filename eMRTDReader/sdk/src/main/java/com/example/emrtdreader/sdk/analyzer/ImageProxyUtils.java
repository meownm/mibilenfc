package com.example.emrtdreader.sdk.analyzer;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Safe utilities for converting CameraX ImageProxy to Bitmap.
 *
 * Design goals:
 * - no usage of androidx.camera.core.internal.*
 * - minimal allocations per frame
 * - safe for STRATEGY_KEEP_ONLY_LATEST
 * - no crashes if format/layout slightly differs
 *
 * Supported format: YUV_420_888 (CameraX default)
 */
public final class ImageProxyUtils {

    private ImageProxyUtils() {}

    /**
     * Convert ImageProxy (YUV_420_888) to ARGB_8888 Bitmap.
     * Returns null if conversion is not possible.
     */
    public static Bitmap toBitmap(@NonNull ImageProxy image) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            return null;
        }

        try {
            byte[] nv21 = yuv420ToNv21(image);
            if (nv21 == null) return null;

            YuvImage yuvImage = new YuvImage(
                    nv21,
                    ImageFormat.NV21,
                    image.getWidth(),
                    image.getHeight(),
                    null
            );

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean ok = yuvImage.compressToJpeg(
                    new Rect(0, 0, image.getWidth(), image.getHeight()),
                    90,
                    out
            );
            if (!ok) return null;

            byte[] jpegBytes = out.toByteArray();
            return android.graphics.BitmapFactory.decodeByteArray(
                    jpegBytes,
                    0,
                    jpegBytes.length
            );
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Convert YUV_420_888 planes to NV21 byte[].
     *
     * Handles pixelStride != 1 and rowStride padding.
     */
    private static byte[] yuv420ToNv21(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        if (planes == null || planes.length < 3) return null;

        int width = image.getWidth();
        int height = image.getHeight();

        ByteBuffer yBuf = planes[0].getBuffer();
        ByteBuffer uBuf = planes[1].getBuffer();
        ByteBuffer vBuf = planes[2].getBuffer();

        int yRowStride = planes[0].getRowStride();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        byte[] nv21 = new byte[width * height * 3 / 2];

        int pos = 0;

        // ---- Y plane ----
        for (int row = 0; row < height; row++) {
            int yRowStart = row * yRowStride;
            yBuf.position(yRowStart);
            yBuf.get(nv21, pos, width);
            pos += width;
        }

        // ---- UV planes (NV21 = VU) ----
        int uvHeight = height / 2;
        int uvWidth = width / 2;

        for (int row = 0; row < uvHeight; row++) {
            int uvRowStart = row * uvRowStride;
            for (int col = 0; col < uvWidth; col++) {
                int uvIndex = uvRowStart + col * uvPixelStride;

                vBuf.position(uvIndex);
                uBuf.position(uvIndex);

                // NV21 expects V first, then U
                nv21[pos++] = vBuf.get();
                nv21[pos++] = uBuf.get();
            }
        }

        return nv21;
    }
}
