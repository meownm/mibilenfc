package com.example.emrtdreader.sdk.analysis;

import com.example.emrtdreader.sdk.models.MrzBox;
import com.example.emrtdreader.sdk.models.MrzParseResult;
import com.example.emrtdreader.sdk.models.MrzTracker;
import com.example.emrtdreader.sdk.models.OcrOutput;
import com.example.emrtdreader.sdk.models.TrackResult;

public final class MrzPipelineFacade {
    private final MrzFrameGate gate;
    private final MrzLocalizer localizer;
    private final MrzTracker tracker;
    private final MrzPipelineOcrEngine ocrEngine;
    private final MrzPipelineParser parser;
    private final MrzStateMachine stateMachine;

    public MrzPipelineFacade(MrzFrameGate gate,
                             MrzLocalizer localizer,
                             MrzTracker tracker,
                             MrzPipelineOcrEngine ocrEngine,
                             MrzPipelineParser parser,
                             MrzStateMachine stateMachine) {
        if (gate == null) {
            throw new IllegalArgumentException("gate cannot be null");
        }
        if (localizer == null) {
            throw new IllegalArgumentException("localizer cannot be null");
        }
        if (tracker == null) {
            throw new IllegalArgumentException("tracker cannot be null");
        }
        if (ocrEngine == null) {
            throw new IllegalArgumentException("ocrEngine cannot be null");
        }
        if (parser == null) {
            throw new IllegalArgumentException("parser cannot be null");
        }
        if (stateMachine == null) {
            throw new IllegalArgumentException("stateMachine cannot be null");
        }
        this.gate = gate;
        this.localizer = localizer;
        this.tracker = tracker;
        this.ocrEngine = ocrEngine;
        this.parser = parser;
        this.stateMachine = stateMachine;
    }

    public MrzPipelineOutput onFrame(FrameInput frame) {
        if (frame == null) {
            throw new IllegalArgumentException("frame cannot be null");
        }
        MrzFrameGate.Result gateResult = gate.evaluate(
                frame.yPlane,
                frame.width,
                frame.height,
                frame.previousYPlane
        );
        MrzBox localized = localizer.locate(frame);
        TrackResult trackResult = localized != null ? tracker.track(localized) : null;
        if (trackResult != null && trackResult.stable) {
            stateMachine.onStableBox();
        }

        OcrOutput ocrOutput = null;
        MrzParseResult parseResult = null;
        if (gateResult.pass && trackResult != null && trackResult.stable) {
            ocrOutput = ocrEngine.recognize(frame, trackResult);
            parseResult = parser.parse(ocrOutput);
            if (parseResult != null) {
                stateMachine.onOcrResult(parseResult, frame.timestampMs);
            }
        }

        return new MrzPipelineOutput(
                gateResult,
                localized,
                trackResult,
                ocrOutput,
                parseResult,
                stateMachine.state
        );
    }
}
