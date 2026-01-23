package com.example.emrtdreader.sdk.models;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

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

    /**
     * Optional diagnostics: OCR elements with bounding boxes (in OCR input bitmap coordinates).
     * May be empty if engine does not expose boxes or if disabled.
     */
    public final List<OcrElement> elements;

    public OcrResult(String rawText, long elapsedMs, OcrMetrics metrics) {
        this(rawText, elapsedMs, metrics, Engine.UNKNOWN, Collections.emptyList());
    }

    public OcrResult(String rawText, long elapsedMs, OcrMetrics metrics, Engine engine) {
        this(rawText, elapsedMs, metrics, engine, Collections.emptyList());
    }

    public OcrResult(String rawText, long elapsedMs, OcrMetrics metrics, Engine engine, List<OcrElement> elements) {
        this.rawText = rawText;
        this.elapsedMs = elapsedMs;
        this.metrics = metrics;
        this.engine = engine;
        this.elements = (elements == null) ? Collections.emptyList() : elements;
    }
}
