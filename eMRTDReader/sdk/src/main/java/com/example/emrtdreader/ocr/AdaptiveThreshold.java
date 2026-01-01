package com.example.emrtdreader.ocr;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Adaptive (local mean) thresholding for MRZ.
 * Input must be grayscale bitmap (ARGB_8888 with R=G=B).
 */
public final class AdaptiveThreshold {
    private AdaptiveThreshold() {}

    public static Bitmap binarize(Bitmap gray) {
        if (gray == null) return null;

        final int w = gray.getWidth();
        final int h = gray.getHeight();
        final Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

        final int radius = 7;   // 7..11 is a good range for MRZ
        final int offset = 5;   // small offset makes chars darker vs background

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
                        int v = Color.red(gray.getPixel(xx, yy));
                        sum += v;
                        count++;
                    }
                }

                int mean = sum / Math.max(1, count);
                int cur = Color.red(gray.getPixel(x, y));
                int bw = (cur < mean - offset) ? 0 : 255;
                out.setPixel(x, y, Color.rgb(bw, bw, bw));
            }
        }
        return out;
    }
}
