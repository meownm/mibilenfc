package com.example.emrtdreader.sdk.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OcrOutputTest {
    @Test
    public void constructorSetsFields() {
        OcrOutput output = new OcrOutput("P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<", 42L, 0.92f, 17);

        assertEquals("P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<", output.rawText);
        assertEquals(42L, output.elapsedMs);
        assertEquals(0.92f, output.whitelistRatio, 0.0001f);
        assertEquals(17, output.ltCount);
    }

    @Test
    public void constructorPreservesNegativeValues() {
        OcrOutput output = new OcrOutput("", -5L, -0.5f, -2);

        assertEquals("", output.rawText);
        assertEquals(-5L, output.elapsedMs);
        assertEquals(-0.5f, output.whitelistRatio, 0.0001f);
        assertEquals(-2, output.ltCount);
    }
}
