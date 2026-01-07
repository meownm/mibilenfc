package com.example.emrtdreader.sdk.ocr;

import android.graphics.Bitmap;

import com.example.emrtdreader.sdk.models.OcrOutput;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MrzOcrEngineIntegrationTest {
    @Test
    public void recognizeReturnsOutputFromEngine() {
        MrzOcrEngine engine = input -> new OcrOutput("P<UTO", 12L, 0.75f, 1);
        PreprocessedMrz input = new PreprocessedMrz(
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888),
                0
        );

        OcrOutput output = engine.recognize(input);

        assertNotNull(output);
        assertEquals("P<UTO", output.rawText);
        assertEquals(12L, output.elapsedMs);
        assertEquals(0.75f, output.whitelistRatio, 0.0001f);
        assertEquals(1, output.ltCount);
    }
}
