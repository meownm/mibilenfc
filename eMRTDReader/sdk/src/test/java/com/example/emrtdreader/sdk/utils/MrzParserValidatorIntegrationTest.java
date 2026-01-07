package com.example.emrtdreader.sdk.utils;

import com.example.emrtdreader.sdk.domain.AccessKey;
import com.example.emrtdreader.sdk.models.MrzParseResult;
import com.example.emrtdreader.sdk.models.NormalizedMrz;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MrzParserValidatorIntegrationTest {

    @Test
    public void parsedFieldsBuildAccessKey() {
        NormalizedMrz normalized = new NormalizedMrz(Arrays.asList(
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
        ));

        MrzParseResult result = MrzParserValidator.parse(normalized);

        assertTrue(result.valid);
        assertNotNull(result.fields);

        AccessKey.Mrz key = result.fields.toAccessKey();
        assertEquals("L898902C3", key.documentNumber);
        assertEquals("740812", key.dateOfBirthYYMMDD);
        assertEquals("120415", key.dateOfExpiryYYMMDD);
    }
}
