package com.example.emrtdreader.utils;

import com.example.emrtdreader.domain.AccessKey;
import com.example.emrtdreader.models.MrzFormat;
import com.example.emrtdreader.models.MrzResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MrzParserIntegrationTest {

    @Test
    public void toAccessKeyParsesMrzResultEndToEnd() {
        MrzResult mrz = new MrzResult(
                "P<GBRJOHNSON<<EMMA<<<<<<<<<<<<<<<<<<<",
                "1234567890GBR6501017F3001012<<<<<<<<<<",
                null,
                MrzFormat.TD3,
                2
        );

        AccessKey.Mrz key = MrzParser.toAccessKey(mrz);

        assertEquals("123456789", key.documentNumber);
        assertEquals("650101", key.dateOfBirthYYMMDD);
        assertEquals("300101", key.dateOfExpiryYYMMDD);
    }
}
