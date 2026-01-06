package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PreprocessParamSelectionTest {
    private static final String TD3_LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    private static final String TD3_LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";
    private static final String TD3_VALID_RAW = TD3_LINE1 + "\n" + TD3_LINE2;

    @Test
    public void pickBestIndexPrefersValidMrz() {
        List<String> texts = Arrays.asList("INVALID", TD3_VALID_RAW, "P<UTO");

        int bestIndex = PreprocessParamSelection.pickBestIndex(texts);

        assertEquals(1, bestIndex);
        assertTrue(PreprocessParamSelection.scoreText(TD3_VALID_RAW)
                > PreprocessParamSelection.scoreText("INVALID"));
    }

    @Test
    public void pickBestIndexHandlesEmptyList() {
        assertEquals(-1, PreprocessParamSelection.pickBestIndex(Collections.emptyList()));
    }
}
