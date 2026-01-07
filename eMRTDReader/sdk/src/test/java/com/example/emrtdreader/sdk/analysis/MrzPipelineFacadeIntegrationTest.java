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

        long step = MrzPipelineFacade.OCR_INTERVAL_MS + 1L;
        FrameInput firstFrame = new FrameInput(new byte[] {
                30, 60, 90, 120,
                60, 90, 120, (byte) 180,
                90, 120, (byte) 180, (byte) 220,
                120, (byte) 180, (byte) 220, (byte) 240
        }, 4, 4, null, 100L);
        FrameInput secondFrame = new FrameInput(firstFrame.yPlane, 4, 4, null, 100L + step);
        FrameInput thirdFrame = new FrameInput(firstFrame.yPlane, 4, 4, null, 100L + step * 2L);
        FrameInput fourthFrame = new FrameInput(firstFrame.yPlane, 4, 4, null, 100L + step * 3L);
        FrameInput fifthFrame = new FrameInput(firstFrame.yPlane, 4, 4, null, 100L + step * 4L);

        MrzPipelineOutput first = facade.onFrame(firstFrame);
        MrzPipelineOutput second = facade.onFrame(secondFrame);
        MrzPipelineOutput third = facade.onFrame(thirdFrame);
        MrzPipelineOutput fourth = facade.onFrame(fourthFrame);
        MrzPipelineOutput fifth = facade.onFrame(fifthFrame);

        assertNotNull(fourth.parseResult);
        assertEquals(MrzPipelineState.OCR_COOLDOWN, fourth.pipelineState);
        assertNotNull(fifth.parseResult);
        assertEquals(MrzPipelineState.CONFIRMED, fifth.pipelineState);
    }
}
