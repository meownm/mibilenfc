package com.example.emrtdreader.sdk.models;

public class MrzTracker {
    private static final float DEFAULT_EMA_ALPHA = 0.5f;
    private static final float IOU_THRESHOLD = 0.7f;
    private static final int STABLE_THRESHOLD = 3;

    private final float emaAlpha;
    private MrzBox previousBox;
    private int stableCount;

    public MrzTracker() {
        this(DEFAULT_EMA_ALPHA);
    }

    public MrzTracker(float emaAlpha) {
        if (emaAlpha <= 0f || emaAlpha > 1f) {
            throw new IllegalArgumentException("emaAlpha must be in (0, 1]");
        }
        this.emaAlpha = emaAlpha;
    }

    public TrackResult track(MrzBox current) {
        if (current == null) {
            throw new IllegalArgumentException("current box is required");
        }
        if (previousBox == null) {
            previousBox = current;
            stableCount = 0;
            return new TrackResult(false, stableCount, 0f, current);
        }

        float iou = computeIoU(previousBox, current);
        if (iou >= IOU_THRESHOLD) {
            stableCount += 1;
        } else {
            stableCount = 0;
        }

        MrzBox smoothed = smooth(previousBox, current);
        previousBox = smoothed;

        boolean stable = stableCount >= STABLE_THRESHOLD;
        float jitter = 1f - iou;
        return new TrackResult(stable, stableCount, jitter, smoothed);
    }

    private MrzBox smooth(MrzBox previous, MrzBox current) {
        float left = emaAlpha * current.left + (1f - emaAlpha) * previous.left;
        float top = emaAlpha * current.top + (1f - emaAlpha) * previous.top;
        float right = emaAlpha * current.right + (1f - emaAlpha) * previous.right;
        float bottom = emaAlpha * current.bottom + (1f - emaAlpha) * previous.bottom;
        return new MrzBox(left, top, right, bottom);
    }

    private float computeIoU(MrzBox a, MrzBox b) {
        float intersectionLeft = Math.max(a.left, b.left);
        float intersectionTop = Math.max(a.top, b.top);
        float intersectionRight = Math.min(a.right, b.right);
        float intersectionBottom = Math.min(a.bottom, b.bottom);

        float intersectionWidth = Math.max(0f, intersectionRight - intersectionLeft);
        float intersectionHeight = Math.max(0f, intersectionBottom - intersectionTop);
        float intersectionArea = intersectionWidth * intersectionHeight;

        float areaA = (a.right - a.left) * (a.bottom - a.top);
        float areaB = (b.right - b.left) * (b.bottom - b.top);
        float unionArea = areaA + areaB - intersectionArea;
        if (unionArea <= 0f) {
            return 0f;
        }
        return intersectionArea / unionArea;
    }
}
