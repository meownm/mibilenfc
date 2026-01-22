package com.example.emrtdreader.sdk.analysis;

/**
 * High-level state of the MRZ scanning pipeline.
 *
 * NOTE:
 * This enum is used both by the analyzer pipeline and by the UI layer.
 * Keep it stable and additive (avoid renames) to preserve backwards compatibility.
 */
public enum ScanState {
    /** Camera active, pipeline has not produced a meaningful decision yet. */
    WAITING,

    /** Analyzer skipped frames because OCR job is already running. */
    OCR_IN_FLIGHT,

    /** MRZ region could not be detected (or ROI quality is too low for reliable OCR). */
    MRZ_NOT_FOUND,

    /** MRZ-like text exists, but quality/confidence is low; user action is required. */
    MRZ_FOUND_LOW_CONFIDENCE,

    /** OCR ran, but did not produce a usable MRZ (empty / bad charset / bad structure). */
    MRZ_FOUND_OCR_REJECTED,

    /** MRZ was parsed, but failed validation checks (e.g., checksum). */
    MRZ_FOUND_INVALID_CHECKSUM,

    /** MRZ successfully detected and accepted by the pipeline. */
    MRZ_FOUND,

    /** Non-empty text from ML Kit (for feedback only). */
    ML_TEXT_FOUND,

    /** Non-empty text from Tesseract (for feedback only). */
    TESS_TEXT_FOUND,

    /** Analyzer/pipeline error. */
    ERROR
}
