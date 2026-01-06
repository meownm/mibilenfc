package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class FrameStatsTest {

    @Test
    public void computeReturnsZerosForNullBitmap() {
        FrameStats stats = FrameStats.compute(null);

        assertEquals(0.0, stats.brightness, 0.0);
        assertEquals(0.0, stats.contrast, 0.0);
        assertEquals(0.0, stats.sharpness, 0.0);
        assertEquals(0.0, stats.noise, 0.0);
    }

    @Test
    public void computeOnConstantBitmapHasZeroContrastAndSharpness() {
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.GRAY);

        FrameStats stats = FrameStats.compute(bitmap);

        assertEquals(128.0, stats.brightness, 1.0);
        assertEquals(0.0, stats.contrast, 0.01);
        assertEquals(0.0, stats.sharpness, 0.01);
        assertEquals(0.0, stats.noise, 0.01);
    }

    @Test
    public void computeOnCheckerboardHasNonZeroMetrics() {
        Bitmap bitmap = createCheckerboardBitmap(16, 16);

        FrameStats stats = FrameStats.compute(bitmap);

        assertTrue(stats.contrast > 0.0);
        assertTrue(stats.sharpness > 0.0);
        assertTrue(stats.noise > 0.0);
    }

    private static Bitmap createCheckerboardBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean even = ((x + y) % 2 == 0);
                bitmap.setPixel(x, y, even ? Color.WHITE : Color.BLACK);
            }
        }
        return bitmap;
    }
}
