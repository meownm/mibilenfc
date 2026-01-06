package com.example.emrtdreader.sdk.ocr;

import android.graphics.Rect;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Stabilizes ROI rectangle across frames:
 * - moving average over last N rects
 * - outlier rejection by IoU threshold
 */
public final class RectAverager {
    private final int windowSize;
    private final float minIouToAccept;
    private final Deque<Rect> window = new ArrayDeque<>();
    private Rect lastStable = null;

    public RectAverager(int windowSize, float minIouToAccept) {
        if (windowSize < 1) throw new IllegalArgumentException("windowSize must be >= 1");
        this.windowSize = windowSize;
        this.minIouToAccept = minIouToAccept;
    }

    public Rect update(Rect candidate, int frameW, int frameH) {
        if (candidate == null) throw new IllegalArgumentException("candidate is null");
        Rect clamped = clamp(candidate, frameW, frameH);

        if (lastStable == null) {
            push(clamped);
            lastStable = average(frameW, frameH);
            return new Rect(lastStable);
        }

        float iou = iou(lastStable, clamped);
        if (iou < minIouToAccept) {
            // If window is empty for some reason, accept to recover.
            if (window.isEmpty()) {
                push(clamped);
                lastStable = average(frameW, frameH);
            }
            return new Rect(lastStable);
        }

        push(clamped);
        lastStable = average(frameW, frameH);
        return new Rect(lastStable);
    }

    public void reset() {
        window.clear();
        lastStable = null;
    }

    public Rect getLastStable() {
        return lastStable == null ? null : new Rect(lastStable);
    }

    private void push(Rect r) {
        window.addLast(new Rect(r));
        while (window.size() > windowSize) window.removeFirst();
    }

    private Rect average(int frameW, int frameH) {
        long l = 0, t = 0, rr = 0, b = 0;
        int n = window.size();
        for (Rect r : window) {
            l += r.left; t += r.top; rr += r.right; b += r.bottom;
        }
        Rect avg = new Rect(
                (int) Math.round(l / (double) n),
                (int) Math.round(t / (double) n),
                (int) Math.round(rr / (double) n),
                (int) Math.round(b / (double) n)
        );
        return clamp(avg, frameW, frameH);
    }

    private static Rect clamp(Rect r, int w, int h) {
        int left = clampInt(r.left, 0, w - 1);
        int top = clampInt(r.top, 0, h - 1);
        int right = clampInt(r.right, left + 1, w);
        int bottom = clampInt(r.bottom, top + 1, h);
        return new Rect(left, top, right, bottom);
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    private static float iou(Rect a, Rect b) {
        int interLeft = Math.max(a.left, b.left);
        int interTop = Math.max(a.top, b.top);
        int interRight = Math.min(a.right, b.right);
        int interBottom = Math.min(a.bottom, b.bottom);

        int interW = Math.max(0, interRight - interLeft);
        int interH = Math.max(0, interBottom - interTop);
        long interArea = (long) interW * interH;

        long areaA = (long) (a.right - a.left) * (a.bottom - a.top);
        long areaB = (long) (b.right - b.left) * (b.bottom - b.top);

        long union = areaA + areaB - interArea;
        if (union <= 0) return 0f;
        return interArea / (float) union;
    }
}
