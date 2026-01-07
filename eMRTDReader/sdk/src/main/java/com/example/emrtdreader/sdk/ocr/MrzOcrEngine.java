package com.example.emrtdreader.sdk.ocr;

import com.example.emrtdreader.sdk.models.OcrOutput;

public interface MrzOcrEngine {
    OcrOutput recognize(PreprocessedMrz input);
}
