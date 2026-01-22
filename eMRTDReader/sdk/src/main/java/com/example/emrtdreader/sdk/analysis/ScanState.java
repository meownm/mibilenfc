package com.example.emrtdreader.sdk.analysis;

public enum ScanState {
    WAITING,
    /** OCR is currently running; frames may be skipped due to backpressure. */
    OCR_IN_FLIGHT,

    /** MRZ region was not detected or not stable enough to run OCR. */
    MRZ_NOT_FOUND,

    /** OCR produced text, but MRZ could not be parsed/validated. */
    MRZ_OCR_REJECTED,

    /** OCR produced an MRZ candidate, but validation did not pass. */
    MRZ_INVALID,

    /** Retry is required (timeouts / repeated failures / unstable frames). */
    MRZ_RETRY_REQUIRED,

    /** OCR took too long; considered timed out. */
    MRZ_OCR_TIMEOUT,

    MRZ_FOUND,
    ML_TEXT_FOUND,
    TESS_TEXT_FOUND,
    ERROR
}
