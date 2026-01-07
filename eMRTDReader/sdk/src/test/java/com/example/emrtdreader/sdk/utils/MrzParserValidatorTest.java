package com.example.emrtdreader.sdk.utils;

import com.example.emrtdreader.sdk.models.MrzChecksums;
import com.example.emrtdreader.sdk.models.MrzFormat;
import com.example.emrtdreader.sdk.models.MrzParseResult;
import com.example.emrtdreader.sdk.models.NormalizedMrz;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MrzParserValidatorTest {

    @Test
    public void parseTd3PopulatesFieldsAndChecksums() {
        NormalizedMrz normalized = new NormalizedMrz(Arrays.asList(
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
        ));

        MrzParseResult result = MrzParserValidator.parse(normalized);

        assertEquals(MrzFormat.TD3, result.format);
        assertEquals("P<", result.documentType);
        assertEquals("UTO", result.issuingCountry);
        assertEquals("L898902C3", result.documentNumber);
        assertEquals("UTO", result.nationality);
        assertEquals("740812", result.birthDateYYMMDD);
        assertEquals("F", result.sex);
        assertEquals("120415", result.expiryDateYYMMDD);
        assertEquals("ERIKSSON", result.surname);
        assertEquals("ANNA MARIA", result.givenNames);
        assertNotNull(result.checksums);
        assertTrue(result.valid);
        assertEquals(4, result.checksums.passedCount);
        assertEquals(1.0f, result.score.lengthScore, 0.0f);
        assertEquals(1.0f, result.score.charsetScore, 0.0f);
        assertEquals(1.0f, result.score.structureScore, 0.0f);
        assertEquals(4, result.score.checksumScore);
    }

    @Test
    public void parseTd3FlagsInvalidFinalChecksum() {
        NormalizedMrz normalized = new NormalizedMrz(Arrays.asList(
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<11"
        ));

        MrzParseResult result = MrzParserValidator.parse(normalized);

        MrzChecksums checksums = result.checksums;
        assertFalse(result.valid);
        assertTrue(checksums.documentNumberOk);
        assertTrue(checksums.birthDateOk);
        assertTrue(checksums.expiryDateOk);
        assertFalse(checksums.finalChecksumOk);
    }

    @Test
    public void parseTd2ReturnsFormatButNoTd3Fields() {
        NormalizedMrz normalized = new NormalizedMrz(Arrays.asList(
                "I<UTOD231458907<<<<<<<<<<<<<<<",
                "7408122F1204159UTO<<<<<<<<<<<"
        ));

        MrzParseResult result = MrzParserValidator.parse(normalized);

        assertEquals(MrzFormat.TD2, result.format);
        assertNull(result.documentNumber);
        assertFalse(result.valid);
        assertEquals(1.0f, result.score.lengthScore, 0.0f);
    }

    @Test
    public void parseUnknownFormatReturnsEmptyResult() {
        NormalizedMrz normalized = new NormalizedMrz(Arrays.asList("ABC"));

        MrzParseResult result = MrzParserValidator.parse(normalized);

        assertNull(result.format);
        assertFalse(result.valid);
        assertEquals(0.0f, result.score.lengthScore, 0.0f);
    }
}
