package com.example.emrtdreader.sdk.ocr;

import android.graphics.Bitmap;
import android.graphics.Rect;

/**
 * Deterministic MRZ band detector (no ML).
 *
 * Idea:
 * - MRZ region produces high horizontal edge energy per row (many vertical strokes).
 * - We compute per-row gradient energy on a downscaled frame.
 * - Find the strongest contiguous band of rows near the bottom of the image.
 * - Refine left/right bounds by column energy inside the band.
 *
 * Output Rect is in ORIGINAL bitmap coordinates.
 */
public final class MrzAutoDetector {

    // Performance: process downscaled image
    private static final int TARGET_W = 640;

    // Search constraints (ratios in ORIGINAL frame)
    private static final float SEARCH_BOTTOM_START_RATIO = 0.40f;  // start searching from 40% height
    private static final float SEARCH_BOTTOM_END_RATIO   = 0.98f;  // up to bottom
    private static final float MIN_BAND_HEIGHT_RATIO = 0.10f;       // 10% of frame height
    private static final float MAX_BAND_HEIGHT_RATIO = 0.45f;       // 45% of frame height
    private static final float MIN_BAND_WIDTH_RATIO  = 0.60f;       // MRZ should be wide
    private static final float MIN_ASPECT_RATIO      = 3.0f;        // width/height

    // Smoothing window for row energies
    private static final int ROW_SMOOTH_WIN = 9; // odd
    private static final int ROW_EXPAND_PX  = 6; // expand a bit after detection (in downscaled px)

    // Thresholding: choose band based on percentile of energies
    private static final float ENERGY_THRESH_RATIO = 0.55f; // relative to max in search zone

    private MrzAutoDetector() {}

    public static Rect detect(Bitmap src) {
        if (src == null) return null;
        final int ow = src.getWidth();
        final int oh = src.getHeight();
        if (ow < 200 || oh < 200) return null;

        // Downscale for speed (keep aspect)
        final float scale = ow > TARGET_W ? (TARGET_W / (float) ow) : 1.0f;
        final int w = Math.max(1, Math.round(ow * scale));
        final int h = Math.max(1, Math.round(oh * scale));

        Bitmap bm = (scale < 1.0f)
                ? Bitmap.createScaledBitmap(src, w, h, false)
                : src;

        final int[] px = new int[w * h];
        bm.getPixels(px, 0, w, 0, 0, w, h);

        // Search zone in downscaled coords
        final int y0 = clamp(Math.round(h * SEARCH_BOTTOM_START_RATIO), 0, h - 1);
        final int y1 = clamp(Math.round(h * SEARCH_BOTTOM_END_RATIO), 0, h);
        if (y1 - y0 < 20) return null;

        // Compute row edge energy: sum |lum(x)-lum(x-1)| across row
        final float[] rowEnergy = new float[h];
        for (int y = y0; y < y1; y++) {
            int idx = y * w;
            int prevLum = lum(px[idx]);
            float sum = 0f;
            for (int x = 1; x < w; x++) {
                int curLum = lum(px[idx + x]);
                sum += Math.abs(curLum - prevLum);
                prevLum = curLum;
            }
            rowEnergy[y] = sum;
        }

        // Smooth row energies
        smooth1d(rowEnergy, y0, y1, ROW_SMOOTH_WIN);

        // Find max energy in search zone
        float maxE = 0f;
        for (int y = y0; y < y1; y++) {
            if (rowEnergy[y] > maxE) maxE = rowEnergy[y];
        }
        if (maxE <= 0f) return null;

        final float thr = maxE * ENERGY_THRESH_RATIO;

        // Find best contiguous band above threshold: choose band with max integrated energy
        int bestTop = -1, bestBot = -1;
        float bestScore = 0f;

        int curTop = -1;
        float curScore = 0f;

        for (int y = y0; y < y1; y++) {
            if (rowEnergy[y] >= thr) {
                if (curTop < 0) curTop = y;
                curScore += rowEnergy[y];
            } else {
                if (curTop >= 0) {
                    int curBot = y; // exclusive
                    float score = curScore;
                    if (score > bestScore) {
                        bestScore = score;
                        bestTop = curTop;
                        bestBot = curBot;
                    }
                    curTop = -1;
                    curScore = 0f;
                }
            }
        }
        // close tail
        if (curTop >= 0) {
            int curBot = y1;
            float score = curScore;
            if (score > bestScore) {
                bestScore = score;
                bestTop = curTop;
                bestBot = curBot;
            }
        }

        if (bestTop < 0 || bestBot <= bestTop) return null;

        // Expand band a little
        bestTop = clamp(bestTop - ROW_EXPAND_PX, 0, h - 1);
        bestBot = clamp(bestBot + ROW_EXPAND_PX, bestTop + 1, h);

        int bandH = bestBot - bestTop;
        float bandHR = bandH / (float) h;
        if (bandHR < MIN_BAND_HEIGHT_RATIO || bandHR > MAX_BAND_HEIGHT_RATIO) return null;

        // Refine left/right bounds by column energies within the band
        float[] colEnergy = new float[w];
        for (int x = 1; x < w; x++) {
            float sum = 0f;
            for (int y = bestTop; y < bestBot; y++) {
                int idx = y * w + x;
                int a = lum(px[idx]);
                int b = lum(px[idx - 1]);
                sum += Math.abs(a - b);
            }
            colEnergy[x] = sum;
        }

        // Find horizontal bounds where colEnergy is significant
        float maxC = 0f;
        for (int x = 0; x < w; x++) if (colEnergy[x] > maxC) maxC = colEnergy[x];
        if (maxC <= 0f) return null;

        float cThr = maxC * 0.20f;

        int left = 0;
        while (left < w && colEnergy[left] < cThr) left++;
        int right = w - 1;
        while (right >= 0 && colEnergy[right] < cThr) right--;

        // Expand margins a bit
        left = clamp(left - 8, 0, w - 1);
        right = clamp(right + 8, left + 1, w);

        int bandW = right - left;
        float bandWR = bandW / (float) w;
        if (bandWR < MIN_BAND_WIDTH_RATIO) return null;

        float aspect = bandW / (float) bandH;
        if (aspect < MIN_ASPECT_RATIO) return null;

        // Convert to original coords
        Rect r = new Rect(
                Math.round(left / scale),
                Math.round(bestTop / scale),
                Math.round(right / scale),
                Math.round(bestBot / scale)
        );

        // Clamp to original frame
        r.left = clamp(r.left, 0, ow - 1);
        r.top = clamp(r.top, 0, oh - 1);
        r.right = clamp(r.right, r.left + 1, ow);
        r.bottom = clamp(r.bottom, r.top + 1, oh);

        // Additional sanity: MRZ usually in lower half
        if (r.bottom < oh * 0.55f) return null;

        return r;
    }

    private static int lum(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = (argb) & 0xFF;
        // integer luma approximation
        return (r * 77 + g * 150 + b * 29) >> 8;
    }

    private static void smooth1d(float[] a, int from, int to, int win) {
        if (win < 3 || (win % 2) == 0) return;
        int half = win / 2;

        float[] tmp = new float[to - from];
        for (int i = from; i < to; i++) {
            float sum = 0f;
            int cnt = 0;
            int s = Math.max(from, i - half);
            int e = Math.min(to - 1, i + half);
            for (int j = s; j <= e; j++) {
                sum += a[j];
                cnt++;
            }
            tmp[i - from] = (cnt == 0) ? a[i] : (sum / cnt);
        }
        for (int i = from; i < to; i++) {
            a[i] = tmp[i - from];
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
