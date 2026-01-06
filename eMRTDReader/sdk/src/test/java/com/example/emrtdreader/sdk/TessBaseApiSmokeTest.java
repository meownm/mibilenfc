package com.example.emrtdreader.sdk;

import static org.junit.Assert.assertNotNull;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TessBaseApiSmokeTest {

    @Test
    public void canCreateTessBaseApiAndSetDefaults() {
        TessBaseAPI tessBaseAPI = new TessBaseAPI();
        try {
            assertNotNull(tessBaseAPI);
            tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<");
            tessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
        } finally {
            tessBaseAPI.end();
        }
    }
}
