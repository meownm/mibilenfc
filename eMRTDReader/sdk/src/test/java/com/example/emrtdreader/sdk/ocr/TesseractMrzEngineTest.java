package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.Bitmap;

import com.example.emrtdreader.sdk.models.OcrOutput;
import com.googlecode.tesseract.android.TessBaseAPI;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.mockito.Mockito;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TesseractMrzEngineTest {
    @Test
    public void recognizeComputesWhitelistMetrics() {
        TessBaseAPI tess = Mockito.mock(TessBaseAPI.class);
        when(tess.init(anyString(), anyString(), anyInt())).thenReturn(true);
        when(tess.getUTF8Text()).thenReturn("P<UTO\n1234");

        TesseractMrzEngine engine = new TesseractMrzEngine(tess, "/data", "ocrb");
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);

        OcrOutput output = engine.recognize(new PreprocessedMrz(bitmap, 0));

        assertEquals("P<UTO\n1234", output.rawText);
        assertEquals(1.0f, output.whitelistRatio, 0.0001f);
        assertEquals(1, output.ltCount);
        verify(tess).setImage(bitmap);
        verify(tess).setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, TesseractMrzEngine.MRZ_WHITELIST);
    }

    @Test
    public void recognizeThrowsWhenBitmapMissing() {
        TessBaseAPI tess = Mockito.mock(TessBaseAPI.class);
        when(tess.init(anyString(), anyString(), anyInt())).thenReturn(true);
        TesseractMrzEngine engine = new TesseractMrzEngine(tess, "/data", "ocrb");

        assertThrows(IllegalArgumentException.class, () -> engine.recognize(new PreprocessedMrz(null, 0)));
    }

    @Test
    public void recognizeDisablesLanguageModelsOnInit() {
        TessBaseAPI tess = Mockito.mock(TessBaseAPI.class);
        when(tess.init(anyString(), anyString(), anyInt())).thenReturn(true);
        when(tess.getUTF8Text()).thenReturn("P<UTO");
        TesseractMrzEngine engine = new TesseractMrzEngine(tess, "/data", "ocrb");
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);

        engine.recognize(new PreprocessedMrz(bitmap, 0));
        engine.recognize(new PreprocessedMrz(bitmap, 0));

        verify(tess, times(1)).init(anyString(), anyString(), anyInt());
        verify(tess).setVariable("load_system_dawg", "0");
        verify(tess).setVariable("load_freq_dawg", "0");
    }
}
