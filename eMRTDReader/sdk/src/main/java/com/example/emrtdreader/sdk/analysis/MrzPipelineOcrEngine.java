package com.example.emrtdreader.sdk.analysis;

import com.example.emrtdreader.sdk.models.OcrOutput;
import com.example.emrtdreader.sdk.models.TrackResult;

public interface MrzPipelineOcrEngine {
    OcrOutput recognize(FrameInput frame, TrackResult trackResult);
}
