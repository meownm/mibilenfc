package com.example.emrtdreader.sdk.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

public class MrzKeyTest {

    @Test
    public void equalsAndHashCodeMatchForSameValues() {
        MrzKey first = new MrzKey("L898902C3", "740812", "120415");
        MrzKey second = new MrzKey("L898902C3", "740812", "120415");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void equalsReturnsFalseForDifferentValues() {
        MrzKey first = new MrzKey("L898902C3", "740812", "120415");
        MrzKey second = new MrzKey("X12345678", "740812", "120415");

        assertNotEquals(first, second);
        assertNotEquals(first, new Object());
    }

    @Test
    public void constructorRejectsNullDocumentNumber() {
        try {
            new MrzKey(null, "740812", "120415");
            fail("Expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void constructorRejectsNullBirthDate() {
        try {
            new MrzKey("L898902C3", null, "120415");
            fail("Expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void constructorRejectsNullExpiryDate() {
        try {
            new MrzKey("L898902C3", "740812", null);
            fail("Expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }
}
