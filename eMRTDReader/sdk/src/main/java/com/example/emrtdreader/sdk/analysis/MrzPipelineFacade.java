package com.example.emrtdreader.sdk.analysis;

import com.example.emrtdreader.sdk.models.MrzBox;
import com.example.emrtdreader.sdk.models.MrzParseResult;
import com.example.emrtdreader.sdk.models.MrzTracker;
import com.example.emrtdreader.sdk.models.OcrOutput;
import com.example.emrtdreader.sdk.models.TrackResult;

public final class MrzPipelineFacade {
    static final long OCR_INTERVAL_MS = 250L;

    private final MrzFrameGate gate;
    private final MrzLocalizer localizer;
    private final MrzTracker tracker;
    private final MrzPipelineOcrEngine ocrEngine;
    private final MrzPipelineParser parser;
    private final MrzStateMachine stateMachine;
    private boolean ocrInFlight;
    private long lastOcrMs;

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
                frame.previousYPlane,
                frame.roiHint
        );
        MrzBox localized = localizer.locate(frame);
        TrackResult trackResult = localized != null ? tracker.track(localized) : null;
        boolean stable = trackResult != null && trackResult.stable;
        if (stable) {
            stateMachine.onStableBox();
        }

        OcrOutput ocrOutput = null;
        MrzParseResult parseResult = null;
        long nowMs = frame.timestampMs;
        if (gateResult.pass && stable && shouldRunOcr(nowMs)) {
            ocrInFlight = true;
            lastOcrMs = nowMs;
            try {
                ocrOutput = ocrEngine.recognize(frame, trackResult);
                parseResult = parser.parse(ocrOutput);
            } finally {
                ocrInFlight = false;
            }
            stateMachine.onOcrResult(parseResult, nowMs);
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

    private boolean shouldRunOcr(long nowMs) {
        if (ocrInFlight) {
            return false;
        }
        if (lastOcrMs == 0L) {
            return true;
        }
        return nowMs - lastOcrMs >= OCR_INTERVAL_MS;
    }
}
