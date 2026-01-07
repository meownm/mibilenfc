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

    @Test
    public void recalcTotalReturnsWeightedSum() {
        MrzScore score = new MrzScore();
        score.checksumScore = 2;
        score.lengthScore = 0.5f;
        score.charsetScore = 0.75f;
        score.structureScore = 1.0f;
        score.stabilityScore = 0.2f;

        score.recalcTotal();

        assertEquals(2 * 10.0f + 0.5f * 2.0f + 0.75f * 2.0f + 1.0f * 3.0f + 0.2f * 5.0f,
                score.totalScore,
                0.0001f);
    }

    @Test
    public void recalcTotalHandlesNegativeScores() {
        MrzScore score = new MrzScore();
        score.checksumScore = -1;
        score.lengthScore = -0.5f;
        score.charsetScore = 0.0f;
        score.structureScore = 0.0f;
        score.stabilityScore = 0.0f;

        score.recalcTotal();

        assertEquals(-1 * 10.0f + -0.5f * 2.0f, score.totalScore, 0.0001f);
    }
}
