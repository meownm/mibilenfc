package com.example.emrtdreader.models;

import java.io.Serializable;

public class OcrResult implements Serializable {
    public final String rawText;
    public final long elapsedMs;
    public final OcrMetrics metrics;

    public OcrResult(String rawText, long elapsedMs, OcrMetrics metrics) {
        this.rawText = rawText;
        this.elapsedMs = elapsedMs;
        this.metrics = metrics;
    }
}
