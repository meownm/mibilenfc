package com.example.emrtdreader.sdk.utils;

import com.example.emrtdreader.sdk.domain.AccessKey;
import com.example.emrtdreader.sdk.models.MrzFormat;
import com.example.emrtdreader.sdk.models.MrzResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MrzParserTest {

    @Test
    public void toAccessKeyReturnsMrzForTd3() {
        MrzResult mrz = new MrzResult(
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
                null,
                MrzFormat.TD3,
                4
        );

        AccessKey.Mrz key = MrzParser.toAccessKey(mrz);

        assertEquals("L898902C3", key.documentNumber);
        assertEquals("740812", key.dateOfBirthYYMMDD);
        assertEquals("120415", key.dateOfExpiryYYMMDD);
    }

    @Test
    public void toAccessKeyReturnsMrzForTd1() {
        MrzResult mrz = new MrzResult(
                "IDUSA123456789<<<<<<<<<<<<<<<",
                "7001015M2501012<<<<<<<<<<<<<<<",
                "DOE<<JOHN<<<<<<<<<<<<<<<<<<<<",
                MrzFormat.TD1,
                3
        );

        AccessKey.Mrz key = MrzParser.toAccessKey(mrz);

        assertEquals("123456789", key.documentNumber);
        assertEquals("700101", key.dateOfBirthYYMMDD);
        assertEquals("250101", key.dateOfExpiryYYMMDD);
    }

    @Test
    public void toAccessKeyReturnsNullWhenMrzIsNull() {
        assertNull(MrzParser.toAccessKey(null));
    }
}
