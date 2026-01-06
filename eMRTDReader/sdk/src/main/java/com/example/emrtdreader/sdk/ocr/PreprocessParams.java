package com.example.emrtdreader.sdk.ocr;

import java.util.Objects;

public final class PreprocessParams {
    public final int blockSize;
    public final int c;
    public final float scale;
    public final int blurRadius;

    public PreprocessParams(int blockSize, int c, float scale, int blurRadius) {
        validate(blockSize, scale, blurRadius);
        this.blockSize = blockSize;
        this.c = c;
        this.scale = scale;
        this.blurRadius = blurRadius;
    }

    private static void validate(int blockSize, float scale, int blurRadius) {
        if (blockSize < 3 || blockSize % 2 == 0) {
            throw new IllegalArgumentException("blockSize must be odd and >= 3");
        }
        if (scale <= 0f) {
            throw new IllegalArgumentException("scale must be > 0");
        }
        if (blurRadius < 0) {
            throw new IllegalArgumentException("blurRadius must be >= 0");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PreprocessParams)) return false;
        PreprocessParams that = (PreprocessParams) o;
        return blockSize == that.blockSize
                && c == that.c
                && Float.compare(that.scale, scale) == 0
                && blurRadius == that.blurRadius;
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockSize, c, scale, blurRadius);
    }

    @Override
    public String toString() {
        return "PreprocessParams{"
                + "blockSize=" + blockSize
                + ", c=" + c
                + ", scale=" + scale
                + ", blurRadius=" + blurRadius
                + '}';
    }
}
