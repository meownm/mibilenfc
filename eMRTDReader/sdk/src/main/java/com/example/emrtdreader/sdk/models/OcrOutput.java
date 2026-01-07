package com.example.emrtdreader.sdk.models;

import java.io.Serializable;

public class OcrOutput implements Serializable {
    public final String rawText;
    public final long elapsedMs;
    public final float whitelistRatio;
    public final int ltCount;

    public OcrOutput(String rawText, long elapsedMs, float whitelistRatio, int ltCount) {
        this.rawText = rawText;
        this.elapsedMs = elapsedMs;
        this.whitelistRatio = whitelistRatio;
        this.ltCount = ltCount;
    }
}
