package com.example.emrtdreader.sdk.models;

import java.util.Collections;
import java.util.List;

public class OcrResult {

    /* === ИСХОДНЫЕ ПОЛЯ (НЕ ТРОГАЕМ) === */

    public final String rawText;
    public final long elapsedMs;
    public final OcrMetrics metrics;
    public final Engine engine;

    /* === НОВОЕ ПОЛЕ (РАСШИРЕНИЕ) === */

    public final List<OcrElement> elements;

    /* === ENUM (ОБЯЗАТЕЛЬНО) === */

    public enum Engine {
        ML_KIT,
        TESSERACT,
        UNKNOWN
    }

    /* === СТАРЫЕ КОНСТРУКТОРЫ (СОХРАНЯЕМ) === */

    public OcrResult(
            String rawText,
            long elapsedMs,
            OcrMetrics metrics,
            Engine engine
    ) {
        this(rawText, elapsedMs, metrics, engine, Collections.emptyList());
    }

    /* === НОВЫЙ КОНСТРУКТОР (ДЛЯ PIPELINE) === */

    public OcrResult(
            String rawText,
            long elapsedMs,
            OcrMetrics metrics,
            Engine engine,
            List<OcrElement> elements
    ) {
        this.rawText = rawText;
        this.elapsedMs = elapsedMs;
        this.metrics = metrics;
        this.engine = engine;
        this.elements = elements != null ? elements : Collections.emptyList();
    }
}
