package com.example.emrtdreader.sdk.analysis;

import com.example.emrtdreader.sdk.models.MrzBox;
import com.example.emrtdreader.sdk.models.MrzParseResult;
import com.example.emrtdreader.sdk.models.OcrOutput;
import com.example.emrtdreader.sdk.models.TrackResult;

public final class MrzPipelineOutput {
    public final MrzFrameGate.Result gateResult;
    public final MrzBox localizedBox;
    public final TrackResult trackResult;
    public final OcrOutput ocrOutput;
    public final MrzParseResult parseResult;
    public final MrzPipelineState pipelineState;

    public MrzPipelineOutput(MrzFrameGate.Result gateResult,
                             MrzBox localizedBox,
                             TrackResult trackResult,
                             OcrOutput ocrOutput,
                             MrzParseResult parseResult,
                             MrzPipelineState pipelineState) {
        this.gateResult = gateResult;
        this.localizedBox = localizedBox;
        this.trackResult = trackResult;
        this.ocrOutput = ocrOutput;
        this.parseResult = parseResult;
        this.pipelineState = pipelineState;
    }
}
