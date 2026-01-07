package com.example.emrtdreader.sdk.models;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class MrzFailReasonTest {

    @Test
    public void valuesExposeAllReasonsInOrder() {
        assertArrayEquals(new MrzFailReason[] {
                MrzFailReason.UNKNOWN_FORMAT,
                MrzFailReason.BAD_LENGTH,
                MrzFailReason.BAD_CHARSET,
                MrzFailReason.CHECKSUM_FAIL,
                MrzFailReason.LOW_STRUCTURE_SCORE,
                MrzFailReason.LOW_CONFIDENCE,
                MrzFailReason.INCONSISTENT_BETWEEN_FRAMES
        }, MrzFailReason.values());
    }

    @Test
    public void valueOfRejectsUnknownReason() {
        assertThrows(IllegalArgumentException.class, () -> MrzFailReason.valueOf("NOT_A_REASON"));
    }
}
