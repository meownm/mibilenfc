package com.example.emrtdreader.sdk.models;

import android.graphics.Rect;

import java.io.Serializable;

/**
 * Single OCR element with bounding box in the coordinate space of the OCR input bitmap.
 *
 * NOTE: For diagnostics. Consumers may map these coordinates back to the original frame using
 * the same crop/scale parameters that were used to create the OCR input bitmap.
 */
public final class OcrElement implements Serializable {

    public enum Level { BLOCK, LINE, ELEMENT, WORD, SYMBOL }

    public final String text;
    public final Rect bbox;
    public final Level level;
    public final OcrResult.Engine engine;

    // Optional. Not all engines provide confidence.
    public final Float confidence;

    public OcrElement(String text, Rect bbox, Level level, OcrResult.Engine engine) {
        this(text, bbox, level, engine, null);
    }

    public OcrElement(String text, Rect bbox, Level level, OcrResult.Engine engine, Float confidence) {
        this.text = text;
        this.bbox = bbox;
        this.level = level;
        this.engine = engine;
        this.confidence = confidence;
    }
}
