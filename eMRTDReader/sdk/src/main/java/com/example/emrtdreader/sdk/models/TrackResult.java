package com.example.emrtdreader.sdk.models;

import java.io.Serializable;

public class TrackResult implements Serializable {
    public final boolean stable;
    public final int stableCount;
    public final float jitter;
    public final MrzBox box;

    public TrackResult(boolean stable, int stableCount, float jitter, MrzBox box) {
        if (stableCount < 0) {
            throw new IllegalArgumentException("stableCount must be >= 0");
        }
        if (jitter < 0) {
            throw new IllegalArgumentException("jitter must be >= 0");
        }
        if (box == null) {
            throw new IllegalArgumentException("box is required");
        }
        this.stable = stable;
        this.stableCount = stableCount;
        this.jitter = jitter;
        this.box = box;
    }
}
