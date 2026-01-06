package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MrzScoreTest {
    private static final String LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    private static final String LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

    @Test
    public void scoreValidMrzReturnsOne() {
        String mrz = LINE1 + "\n" + LINE2;
        assertEquals(1.0, MrzScore.score(mrz), 0.0);
    }

    @Test
    public void scoreInvalidCharactersReturnsZero() {
        String badLine1 = LINE1.replace('U', '@');
        String mrz = badLine1 + "\n" + LINE2;
        assertEquals(0.0, MrzScore.score(mrz), 0.0);
    }

    @Test
    public void scoreInvalidLengthReturnsZero() {
        String shortLine2 = LINE2.substring(0, LINE2.length() - 1);
        String mrz = LINE1 + "\n" + shortLine2;
        assertEquals(0.0, MrzScore.score(mrz), 0.0);
    }

    @Test
    public void scoreInvalidChecksumReturnsPartial() {
        String badLine2 = LINE2.substring(0, LINE2.length() - 1) + "1";
        String mrz = LINE1 + "\n" + badLine2;
        assertEquals(0.75, MrzScore.score(mrz), 0.0001);
    }
}
