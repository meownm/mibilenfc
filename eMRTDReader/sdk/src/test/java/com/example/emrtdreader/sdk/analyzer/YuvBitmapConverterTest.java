package com.example.emrtdreader.sdk.analyzer;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.Image;

import androidx.camera.core.ImageProxy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class YuvBitmapConverterTest {
    @Test
    public void toBitmapUsesConverterOutputWhenWithinRange() {
        Bitmap sample = createSampleFrame(160, 120, Color.rgb(140, 140, 140), Color.rgb(220, 220, 220));
        AtomicBoolean invoked = new AtomicBoolean(false);
        YuvBitmapConverter converter = new YuvBitmapConverter((image, bitmap) -> {
            assertTrue(bitmap.isMutable());
            invoked.set(true);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawBitmap(sample, 0, 0, null);
        });

        Bitmap converted = converter.toBitmap(createImageProxy(160, 120));

        assertTrue(invoked.get());
        assertTrue(converted.getPixel(4, 4) == sample.getPixel(4, 4));
    }

    @Test
    public void toBitmapNormalizesDarkFrameAndKeepsTextReadable() {
        Bitmap darkFrame = createSampleFrame(320, 240, Color.rgb(30, 30, 30), Color.WHITE);
        YuvBitmapConverter converter = new YuvBitmapConverter((image, bitmap) -> {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawBitmap(darkFrame, 0, 0, null);
        });

        Bitmap converted = converter.toBitmap(createImageProxy(320, 240));
        float avg = averageLuma(converted);
        float contrast = contrastBetweenRegions(converted);

        assertTrue(avg >= YuvBitmapConverter.MIN_AVG_LUMA);
        assertTrue(contrast >= 80f);
    }

    @Test
    public void toBitmapNormalizesBrightFrame() {
        Bitmap brightFrame = createSampleFrame(320, 240, Color.rgb(240, 240, 240), Color.BLACK);
        YuvBitmapConverter converter = new YuvBitmapConverter((image, bitmap) -> {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawBitmap(brightFrame, 0, 0, null);
        });

        Bitmap converted = converter.toBitmap(createImageProxy(320, 240));
        float avg = averageLuma(converted);

        assertTrue(avg <= YuvBitmapConverter.MAX_AVG_LUMA);
    }

    @Test
    public void toBitmapKeepsLowContrastTextUnreadable() {
        Bitmap lowContrast = createSampleFrame(320, 240, Color.rgb(120, 120, 120), Color.rgb(130, 130, 130));
        YuvBitmapConverter converter = new YuvBitmapConverter((image, bitmap) -> {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawBitmap(lowContrast, 0, 0, null);
        });

        Bitmap converted = converter.toBitmap(createImageProxy(320, 240));
        float contrast = contrastBetweenRegions(converted);

        assertTrue(contrast < 20f);
    }

    @Test(expected = IllegalStateException.class)
    public void toBitmapThrowsWhenImageMissing() {
        ImageProxy imageProxy = mock(ImageProxy.class);
        when(imageProxy.getWidth()).thenReturn(8);
        when(imageProxy.getHeight()).thenReturn(8);
        when(imageProxy.getImage()).thenReturn(null);
        YuvBitmapConverter converter = new YuvBitmapConverter((image, bitmap) -> {
        });

        converter.toBitmap(imageProxy);
    }

    private static ImageProxy createImageProxy(int width, int height) {
        ImageProxy imageProxy = mock(ImageProxy.class);
        when(imageProxy.getWidth()).thenReturn(width);
        when(imageProxy.getHeight()).thenReturn(height);
        when(imageProxy.getImage()).thenReturn(mock(Image.class));
        return imageProxy;
    }

    private static Bitmap createSampleFrame(int width, int height, int background, int textColor) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(background);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(textColor);
        int bandTop = (int) (height * 0.7f);
        int bandBottom = (int) (height * 0.9f);
        for (int x = 0; x < width; x += 8) {
            canvas.drawRect(x, bandTop, x + 4, bandBottom, paint);
        }
        return bitmap;
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

    private static float contrastBetweenRegions(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int bandTop = (int) (height * 0.7f);
        int bandBottom = (int) (height * 0.9f);
        float bandLuma = averageLuma(bitmap, bandTop, bandBottom);
        float backgroundLuma = averageLuma(bitmap, 0, bandTop / 2);
        return Math.abs(bandLuma - backgroundLuma);
    }

    private static float averageLuma(Bitmap bitmap, int top, int bottom) {
        int width = bitmap.getWidth();
        int step = Math.max(1, width / 50);
        long sum = 0L;
        long count = 0L;
        for (int y = top; y < bottom; y += step) {
            for (int x = 0; x < width; x += step) {
                int color = bitmap.getPixel(x, y);
                int luma = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3;
                sum += luma;
                count++;
            }
        }
        return count == 0L ? 0f : (float) sum / (float) count;
    }
}
