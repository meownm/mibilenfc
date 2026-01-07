package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.graphics.Bitmap;

import com.example.emrtdreader.sdk.models.MrzFormat;
import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrOutput;
import com.googlecode.tesseract.android.TessBaseAPI;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TesseractMrzEngineIntegrationTest {
    private static final String TD3_LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    private static final String TD3_LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

    @Test
    public void recognizeFeedsMrzTextProcessor() {
        TessBaseAPI tess = Mockito.mock(TessBaseAPI.class);
        when(tess.init(anyString(), anyString(), anyInt())).thenReturn(true);
        when(tess.getUTF8Text()).thenReturn(TD3_LINE1 + "\n" + TD3_LINE2);
        TesseractMrzEngine engine = new TesseractMrzEngine(tess, "/data", "ocrb");
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);

        OcrOutput output = engine.recognize(new PreprocessedMrz(bitmap, 0));
        MrzResult result = MrzTextProcessor.normalizeAndRepair(output.rawText);

        assertNotNull(result);
        assertEquals(MrzFormat.TD3, result.format);
        assertEquals(TD3_LINE1, result.line1);
        assertEquals(TD3_LINE2, result.line2);
        assertEquals(1.0f, output.whitelistRatio, 0.0001f);
    }
}
