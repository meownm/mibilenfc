package com.example.emrtdreader.sdk.utils;

import com.example.emrtdreader.sdk.models.MrzChecksums;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MrzChecksumsIntegrationTest {

    @Test
    public void td1ChecksumsForSampleDocumentAreValid() {
        String line1 = "I<UTOD231458907<<<<<<<<<<<<<<<";
        String line2 = "7408122F1204159UTO<<<<<<<<<<<";
        String line3 = "ERIKSSON<<ANNA<MARIA<<<<<<<";

        MrzChecksums checksums = MrzValidation.checksumsTd1(line1, line2, line3);

        assertTrue(checksums.documentNumberOk);
        assertTrue(checksums.birthDateOk);
        assertTrue(checksums.expiryDateOk);
        assertTrue(checksums.finalChecksumOk);
        assertEquals(4, checksums.passedCount);
    }
}
