package com.example.emrtdreader.sdk.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.example.emrtdreader.sdk.models.MrzBox;
import com.example.emrtdreader.sdk.models.MrzTracker;
import com.example.emrtdreader.sdk.models.OcrOutput;
import com.example.emrtdreader.sdk.models.TrackResult;

import org.junit.Test;

public class MrzPipelineFacadeIntegrationTest {
    private static final String LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    private static final String LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

    @Test
    public void confirmsAfterTwoStableOcrResults() {
        MrzFrameGate gate = new MrzFrameGate(new MrzFrameGate.Thresholds(
                0,
                255,
                0,
                0,
                50
        ));
        MrzLocalizer localizer = frame -> new MrzBox(0f, 0f, 100f, 40f);
        MrzTracker tracker = new MrzTracker();
        MrzPipelineOcrEngine ocrEngine = new MrzPipelineOcrEngine() {
            @Override
            public OcrOutput recognize(FrameInput frame, TrackResult trackResult) {
                return new OcrOutput(LINE1 + "\n" + LINE2, 12L, 0.92f, 18);
            }
        };
        MrzPipelineParser parser = new DefaultMrzPipelineParser();
        MrzStateMachine stateMachine = new MrzStateMachine();
        MrzPipelineFacade facade = new MrzPipelineFacade(gate, localizer, tracker, ocrEngine, parser, stateMachine);

        FrameInput frame = new FrameInput(new byte[] {
                30, 60, 90, 120,
                60, 90, 120, (byte) 180,
                90, 120, (byte) 180, (byte) 220,
                120, (byte) 180, (byte) 220, (byte) 240
        }, 4, 4, null, 100L);

        MrzPipelineOutput first = facade.onFrame(frame);
        MrzPipelineOutput second = facade.onFrame(frame);
        MrzPipelineOutput third = facade.onFrame(frame);
        MrzPipelineOutput fourth = facade.onFrame(frame);

        assertNotNull(third.parseResult);
        assertEquals(MrzPipelineState.OCR_COOLDOWN, third.pipelineState);
        assertNotNull(fourth.parseResult);
        assertEquals(MrzPipelineState.CONFIRMED, fourth.pipelineState);
    }
}
