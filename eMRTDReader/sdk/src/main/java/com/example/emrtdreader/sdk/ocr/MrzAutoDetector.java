package com.example.emrtdreader.sdk.ocr;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;

/**
 * Fast, deterministic MRZ band detector (no ML, no OpenCV).
 * Finds the most "text-like" horizontal band based on vertical edge density.
 */
public final class MrzAutoDetector {
    private MrzAutoDetector() {}

    public static Rect detect(Bitmap src) {
        if (src == null) return null;

        // Downscale for speed
        int dw = Math.max(320, src.getWidth() / 2);
        int dh = Math.max(240, src.getHeight() / 2);
        Bitmap bmp = Bitmap.createScaledBitmap(src, dw, dh, true);

        int w = bmp.getWidth();
        int h = bmp.getHeight();

        int[] rowScore = new int[h];

        // Vertical edge density per row: abs( gray(x+1)-gray(x) )
        for (int y = 1; y < h - 1; y++) {
            int score = 0;
            for (int x = 1; x < w - 1; x++) {
                int p = bmp.getPixel(x, y);
                int q = bmp.getPixel(x + 1, y);

                int g1 = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3;
                int g2 = (Color.red(q) + Color.green(q) + Color.blue(q)) / 3;
                score += Math.abs(g1 - g2);
            }
            rowScore[y] = score;
        }

        // MRZ band is usually ~20% of height (tuneable)
        int bandHeight = Math.max(40, h / 5);

        int bestStart = 0;
        long bestSum = -1;

        long windowSum = 0;
        for (int y = 0; y < bandHeight && y < h; y++) windowSum += rowScore[y];
        bestSum = windowSum;

        for (int y = 1; y + bandHeight < h; y++) {
            windowSum = windowSum - rowScore[y - 1] + rowScore[y + bandHeight - 1];
            if (windowSum > bestSum) {
                bestSum = windowSum;
                bestStart = y;
            }
        }

        // back to source coordinates
        float sx = src.getWidth() / (float) w;
        float sy = src.getHeight() / (float) h;

        int top = Math.round(bestStart * sy);
        int bottom = Math.round((bestStart + bandHeight) * sy);

        top = clamp(top, 0, src.getHeight() - 2);
        bottom = clamp(bottom, top + 1, src.getHeight());

        return new Rect(0, top, src.getWidth(), bottom);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }
}
