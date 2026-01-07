package com.example.emrtdreader.sdk.analyzer;

import android.graphics.Bitmap;

/**
 * Lightweight container for passing frame data
 * from CameraX analyzer to background MRZ pipeline.
 */
public final class FrameEnvelope {

    public final Bitmap frameBitmap;
    public final long timestampMs;
    public final int rotationDegrees;
    public final int frameWidth;
    public final int frameHeight;

    public FrameEnvelope(
            Bitmap frameBitmap,
            long timestampMs,
            int rotationDegrees,
            int frameWidth,
            int frameHeight
    ) {
        this.frameBitmap = frameBitmap;
        this.timestampMs = timestampMs;
        this.rotationDegrees = rotationDegrees;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
    }
}
