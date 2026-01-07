package com.example.emrtdreader.sdk.analysis;

public enum MrzPipelineState {
    SEARCHING,
    TRACKING,
    OCR_RUNNING,
    OCR_COOLDOWN,
    CONFIRMED,
    TIMEOUT
}
