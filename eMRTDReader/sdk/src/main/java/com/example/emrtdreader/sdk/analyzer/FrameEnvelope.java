package com.example.emrtdreader.sdk.analyzer;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import java.util.Objects;

/**
 * Immutable metadata wrapper for a CameraX {@link ImageProxy} frame.
 * <p>
 * Holds lightweight frame metadata only; no bitmap conversion or heavy processing is performed.
 */
public final class FrameEnvelope {
    private final ImageProxy image;
    private final long timestampMs;
    private final int rotationDegrees;
    private final int frameWidth;
    private final int frameHeight;

    public FrameEnvelope(@NonNull ImageProxy image,
                         long timestampMs,
                         int rotationDegrees,
                         int frameWidth,
                         int frameHeight) {
        this.image = Objects.requireNonNull(image, "image");
        this.timestampMs = timestampMs;
        this.rotationDegrees = rotationDegrees;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
    }

    @NonNull
    public ImageProxy getImage() {
        return image;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public int getRotationDegrees() {
        return rotationDegrees;
    }

    public int getFrameWidth() {
        return frameWidth;
    }

    public int getFrameHeight() {
        return frameHeight;
    }
}
