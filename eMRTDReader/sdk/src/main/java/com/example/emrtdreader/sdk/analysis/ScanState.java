package com.example.emrtdreader.sdk.analysis;

public enum ScanState {
    WAITING,
    MRZ_FOUND,
    ML_TEXT_FOUND,
    TESS_TEXT_FOUND,
    ERROR
}
