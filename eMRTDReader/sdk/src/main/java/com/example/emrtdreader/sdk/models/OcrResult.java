package com.example.emrtdreader.sdk.models;

import java.io.Serializable;

public class OcrResult implements Serializable {
    public enum Engine {
        ML_KIT,
        TESSERACT,
        UNKNOWN
    }

    public final String rawText;
    public final long elapsedMs;
    public final OcrMetrics metrics;
    public final Engine engine;

    public OcrResult(String rawText, long elapsedMs, OcrMetrics metrics) {
        this(rawText, elapsedMs, metrics, Engine.UNKNOWN);
    }

    public OcrResult(String rawText, long elapsedMs, OcrMetrics metrics, Engine engine) {
        this.rawText = rawText;
        this.elapsedMs = elapsedMs;
        this.metrics = metrics;
        this.engine = engine;
    }
}
