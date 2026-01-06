package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertEquals;

import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;

import org.junit.Test;

public class OcrRouterScoringIntegrationTest {
    private static final String LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    private static final String LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

    @Test
    public void routingPicksCandidateWithHigherScore() {
        String mlText = LINE1 + "\n" + LINE2.substring(0, LINE2.length() - 1) + "1";
        String tessText = LINE1 + "\n" + LINE2;

        OcrResult mlResult = new OcrResult(mlText, 12L, new OcrMetrics(0, 0, 0), OcrResult.Engine.ML_KIT);
        OcrResult tessResult = new OcrResult(tessText, 25L, new OcrMetrics(0, 0, 0), OcrResult.Engine.TESSERACT);

        OcrRouter.CandidateSelection selection = OcrRouter.pickBestCandidate(mlResult, tessResult, mlText, tessText);

        assertEquals(OcrResult.Engine.TESSERACT, selection.engine);
        assertEquals(tessText, selection.finalText);
    }
}
