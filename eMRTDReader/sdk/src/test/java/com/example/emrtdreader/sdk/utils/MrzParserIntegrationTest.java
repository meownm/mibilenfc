package com.example.emrtdreader.sdk.utils;

import com.example.emrtdreader.sdk.domain.AccessKey;
import com.example.emrtdreader.sdk.models.MrzFormat;
import com.example.emrtdreader.sdk.models.MrzResult;

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

    @Test
    public void toAccessKeyParsesTd2MrzLine2Fields() {
        MrzResult mrz = new MrzResult(
                "I<UTOJOHNSON<<EMMA<<<<<<<<<<<<<<<<<<<<",
                "A12B34567<UTO6501012M3001012<<<<<<<<",
                null,
                MrzFormat.TD2,
                2
        );

        AccessKey.Mrz key = MrzParser.toAccessKey(mrz);

        assertEquals("A12B34567", key.documentNumber);
        assertEquals("650101", key.dateOfBirthYYMMDD);
        assertEquals("300101", key.dateOfExpiryYYMMDD);
    }
}
