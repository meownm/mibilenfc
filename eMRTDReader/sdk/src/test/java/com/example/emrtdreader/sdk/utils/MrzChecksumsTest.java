package com.example.emrtdreader.sdk.utils;

import com.example.emrtdreader.sdk.models.MrzChecksums;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MrzChecksumsTest {

    @Test
    public void td3ChecksumsAllPass() {
        String line2 = "1234567890GBR6501017F3001012<<<<<<<<<<";

        MrzChecksums checksums = MrzValidation.checksumsTd3(line2);

        assertTrue(checksums.documentNumberOk);
        assertTrue(checksums.birthDateOk);
        assertTrue(checksums.expiryDateOk);
        assertTrue(checksums.finalChecksumOk);
        assertEquals(4, checksums.passedCount);
        assertEquals(4, checksums.totalCount);
    }

    @Test
    public void td3ChecksumsDetectFailures() {
        String line2 = "1234567890GBR6501017F3001012<<<<<<<<<<";
        String line2BadFinal = line2.substring(0, 43) + "1";

        MrzChecksums checksums = MrzValidation.checksumsTd3(line2BadFinal);

        assertTrue(checksums.documentNumberOk);
        assertTrue(checksums.birthDateOk);
        assertTrue(checksums.expiryDateOk);
        assertFalse(checksums.finalChecksumOk);
        assertEquals(3, checksums.passedCount);
        assertEquals(4, checksums.totalCount);
    }

    @Test
    public void nullValuesCountAsFailures() {
        MrzChecksums checksums = new MrzChecksums(null, true, null, false);

        assertEquals(1, checksums.passedCount);
        assertEquals(4, checksums.totalCount);
    }
}
