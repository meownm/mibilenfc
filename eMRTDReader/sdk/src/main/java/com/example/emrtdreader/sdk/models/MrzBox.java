package com.example.emrtdreader.sdk.models;

import java.io.Serializable;

public class MrzBox implements Serializable {
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;

    public MrzBox(float left, float top, float right, float bottom) {
        if (right < left) {
            throw new IllegalArgumentException("right must be >= left");
        }
        if (bottom < top) {
            throw new IllegalArgumentException("bottom must be >= top");
        }
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
}
