package com.example.emrtdreader.sdk.models;

public enum MrzFailReason {
    UNKNOWN_FORMAT,
    BAD_LENGTH,
    BAD_CHARSET,
    CHECKSUM_FAIL,
    LOW_STRUCTURE_SCORE,
    LOW_CONFIDENCE,
    INCONSISTENT_BETWEEN_FRAMES
}
