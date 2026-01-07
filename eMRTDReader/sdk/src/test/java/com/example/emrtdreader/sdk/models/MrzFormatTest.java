package com.example.emrtdreader.sdk.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MrzFormatTest {

    @Test
    public void valueOfAcceptsTd2() {
        assertEquals(MrzFormat.TD2, MrzFormat.valueOf("TD2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void valueOfRejectsUnknownFormats() {
        MrzFormat.valueOf("TD4");
    }
}
