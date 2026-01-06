package com.example.emrtdreader.ocr;

import static org.junit.Assert.assertSame;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ThresholdSelectorTest {

    @Test
    public void choose_prefersBinaryWhenSharper() {
        Bitmap gray = createUniformBitmap(20, 20, Color.GRAY);
        Bitmap binary = createCheckerboardBitmap(20, 20, Color.BLACK, Color.WHITE);

        Bitmap chosen = ThresholdSelector.choose(gray, binary);

        assertSame(binary, chosen);
    }

    @Test
    public void choose_prefersGrayWhenBinaryWorse() {
        Bitmap gray = createCheckerboardBitmap(20, 20, Color.BLACK, Color.WHITE);
        Bitmap binary = createUniformBitmap(20, 20, Color.GRAY);

        Bitmap chosen = ThresholdSelector.choose(gray, binary);

        assertSame(gray, chosen);
    }

    private static Bitmap createUniformBitmap(int width, int height, int color) {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(color);
        return bmp;
    }

    private static Bitmap createCheckerboardBitmap(int width, int height, int colorA, int colorB) {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = ((x + y) % 2 == 0) ? colorA : colorB;
                bmp.setPixel(x, y, color);
            }
        }
        return bmp;
    }
}
