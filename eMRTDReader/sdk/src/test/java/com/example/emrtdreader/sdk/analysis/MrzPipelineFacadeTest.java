package com.example.emrtdreader.sdk.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.emrtdreader.sdk.models.MrzBox;
import com.example.emrtdreader.sdk.models.MrzFields;
import com.example.emrtdreader.sdk.models.MrzParseResult;
import com.example.emrtdreader.sdk.models.MrzTracker;
import com.example.emrtdreader.sdk.models.OcrOutput;
import com.example.emrtdreader.sdk.models.TrackResult;

import org.junit.Test;

public class MrzPipelineFacadeTest {

    @Test
    public void onFrameSkipsOcrWhenGateFails() {
        MrzFrameGate gate = new MrzFrameGate(new MrzFrameGate.Thresholds(
                200,
                255,
                0,
                0,
                50
        ));
        MrzLocalizer localizer = frame -> new MrzBox(0f, 0f, 1f, 1f);
        MrzTracker tracker = new StableTracker();
        CountingOcrEngine ocrEngine = new CountingOcrEngine();
        CountingParser parser = new CountingParser();
        MrzStateMachine stateMachine = new MrzStateMachine();
        MrzPipelineFacade facade = new MrzPipelineFacade(gate, localizer, tracker, ocrEngine, parser, stateMachine);

        FrameInput frame = new FrameInput(new byte[] {10, 10, 10, 10}, 2, 2, null, 123L);
        MrzPipelineOutput output = facade.onFrame(frame);

        assertEquals(0, ocrEngine.calls);
        assertEquals(0, parser.calls);
        assertNull(output.ocrOutput);
        assertNull(output.parseResult);
        assertEquals(MrzPipelineState.TRACKING, output.pipelineState);
    }

    @Test
    public void onFrameRunsOcrAndParsesWhenStableAndGated() {
        MrzFrameGate gate = new MrzFrameGate(new MrzFrameGate.Thresholds(
                0,
                255,
                0,
                0,
                50
        ));
        MrzLocalizer localizer = frame -> new MrzBox(0f, 0f, 1f, 1f);
        MrzTracker tracker = new StableTracker();
        CountingOcrEngine ocrEngine = new CountingOcrEngine();
        FixedParser parser = new FixedParser(validResult("L898902C3"));
        MrzStateMachine stateMachine = new MrzStateMachine();
        MrzPipelineFacade facade = new MrzPipelineFacade(gate, localizer, tracker, ocrEngine, parser, stateMachine);

        FrameInput frame = new FrameInput(new byte[] {30, 60, 90, 120}, 2, 2, null, 456L);
        MrzPipelineOutput output = facade.onFrame(frame);

        assertEquals(1, ocrEngine.calls);
        assertEquals(1, parser.calls);
        assertEquals(MrzPipelineState.OCR_COOLDOWN, output.pipelineState);
    }

    private static MrzParseResult validResult(String docNumber) {
        MrzFields fields = new MrzFields(docNumber, "740812", "120415", "UTO", "F", "ERIKSSON", "ANNA MARIA");
        return new MrzParseResult(null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                fields, null, null, true);
    }

    private static final class StableTracker extends MrzTracker {
        @Override
        public TrackResult track(MrzBox current) {
            return new TrackResult(true, 3, 0f, current);
        }
    }

    private static final class CountingOcrEngine implements MrzPipelineOcrEngine {
        int calls;

        @Override
        public OcrOutput recognize(FrameInput frame, TrackResult trackResult) {
            calls += 1;
            return new OcrOutput("RAW", 5L, 1.0f, 2);
        }
    }

    private static final class CountingParser implements MrzPipelineParser {
        int calls;

        @Override
        public MrzParseResult parse(OcrOutput output) {
            calls += 1;
            return null;
        }
    }

    private static final class FixedParser implements MrzPipelineParser {
        private final MrzParseResult result;
        int calls;

        private FixedParser(MrzParseResult result) {
            this.result = result;
        }

        @Override
        public MrzParseResult parse(OcrOutput output) {
            calls += 1;
            return result;
        }
    }
}
