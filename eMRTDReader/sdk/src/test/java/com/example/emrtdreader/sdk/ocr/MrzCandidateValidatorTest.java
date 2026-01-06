package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MrzCandidateValidatorTest {
    private static final String TD3_LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    private static final String TD3_LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

    @Test
    public void acceptsValidMrzText() {
        String raw = TD3_LINE1 + "\n" + TD3_LINE2;
        assertTrue(MrzCandidateValidator.isValid(raw));
    }

    @Test
    public void rejectsShortText() {
        assertFalse(MrzCandidateValidator.isValid("P<UTOERIKSSON"));
    }

    @Test
    public void rejectsInvalidCharacters() {
        String raw = TD3_LINE1 + "\n" + "L898902C36UTO7408122F1204159ZE184226B<<<<<1@";
        assertFalse(MrzCandidateValidator.isValid(raw));
    }

    @Test
    public void rejectsMissingSeparators() {
        String raw = TD3_LINE1.replace("<<", "<A") + "\n" + TD3_LINE2;
        assertFalse(MrzCandidateValidator.isValid(raw));
    }
}
