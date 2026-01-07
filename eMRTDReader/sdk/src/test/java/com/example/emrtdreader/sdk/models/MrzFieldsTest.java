package com.example.emrtdreader.sdk.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.example.emrtdreader.sdk.domain.AccessKey;

import org.junit.Test;

public class MrzFieldsTest {

    @Test
    public void constructorStoresFieldsAndAccessKeyMapping() {
        MrzFields fields = new MrzFields(
            "L898902C3",
            "740812",
            "120415",
            "UTO",
            "F",
            "ERIKSSON",
            "ANNA MARIA"
        );

        assertEquals("L898902C3", fields.getDocumentNumber());
        assertEquals("740812", fields.getBirthDateYYMMDD());
        assertEquals("120415", fields.getExpiryDateYYMMDD());
        assertEquals("UTO", fields.getNationality());
        assertEquals("F", fields.getSex());
        assertEquals("ERIKSSON", fields.getSurname());
        assertEquals("ANNA MARIA", fields.getGivenNames());

        AccessKey.Mrz key = fields.toAccessKey();
        assertEquals("L898902C3", key.documentNumber);
        assertEquals("740812", key.dateOfBirthYYMMDD);
        assertEquals("120415", key.dateOfExpiryYYMMDD);
    }

    @Test
    public void equalsUsesAllFields() {
        MrzFields first = new MrzFields(
            "L898902C3",
            "740812",
            "120415",
            "UTO",
            "F",
            "ERIKSSON",
            "ANNA MARIA"
        );
        MrzFields second = new MrzFields(
            "L898902C3",
            "740812",
            "120415",
            "UTO",
            "F",
            "ERIKSSON",
            "ANNA MARIA"
        );
        MrzFields different = new MrzFields(
            "X1234567",
            "740812",
            "120415",
            "UTO",
            "F",
            "ERIKSSON",
            "ANNA MARIA"
        );

        assertEquals(first, second);
        assertNotEquals(first, different);
    }

    @Test(expected = NullPointerException.class)
    public void constructorRejectsNulls() {
        new MrzFields(
            null,
            "740812",
            "120415",
            "UTO",
            "F",
            "ERIKSSON",
            "ANNA MARIA"
        );
    }
}
