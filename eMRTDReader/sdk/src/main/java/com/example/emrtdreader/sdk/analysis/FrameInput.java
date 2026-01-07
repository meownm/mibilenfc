package com.example.emrtdreader.sdk.analysis;

import android.graphics.Rect;

public final class FrameInput {
    public final byte[] yPlane;
    public final int width;
    public final int height;
    public final byte[] previousYPlane;
    public final long timestampMs;
    public final Rect roiHint;

    public FrameInput(byte[] yPlane, int width, int height, byte[] previousYPlane, long timestampMs) {
        this(yPlane, width, height, previousYPlane, timestampMs, null);
    }

    public FrameInput(byte[] yPlane,
                      int width,
                      int height,
                      byte[] previousYPlane,
                      long timestampMs,
                      Rect roiHint) {
        if (yPlane == null) {
            throw new IllegalArgumentException("yPlane cannot be null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width/height must be positive");
        }
        if (yPlane.length < width * height) {
            throw new IllegalArgumentException("yPlane length must cover width*height");
        }
        this.yPlane = yPlane;
        this.width = width;
        this.height = height;
        this.previousYPlane = previousYPlane;
        this.timestampMs = timestampMs;
        this.roiHint = roiHint;
    }
}
