package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PreprocessParamSelectionIntegrationTest {
    private static final String LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    private static final String LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

    @Test
    public void pickBestIndexPrefersPerfectMrzScore() {
        String valid = LINE1 + "\n" + LINE2;
        String invalid = LINE1.replace('U', '@') + "\n" + LINE2;
        List<String> candidates = Arrays.asList(invalid, valid);

        int bestIndex = PreprocessParamSelection.pickBestIndex(candidates);

        assertEquals(1, bestIndex);
    }
}
