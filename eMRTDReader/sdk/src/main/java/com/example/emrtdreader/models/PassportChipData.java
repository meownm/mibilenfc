package com.example.emrtdreader.models;

import java.io.Serializable;

public class PassportChipData implements Serializable {
    public final String documentNumber;
    public final String surname;
    public final String givenNames;
    public final String nationality;
    public final String dateOfBirth;   // YYMMDD (as in MRZ)
    public final String sex;
    public final String dateOfExpiry;  // YYMMDD
    public final byte[] photoJpeg;     // may be null

    public PassportChipData(
            String documentNumber,
            String surname,
            String givenNames,
            String nationality,
            String dateOfBirth,
            String sex,
            String dateOfExpiry,
            byte[] photoJpeg
    ) {
        this.documentNumber = documentNumber;
        this.surname = surname;
        this.givenNames = givenNames;
        this.nationality = nationality;
        this.dateOfBirth = dateOfBirth;
        this.sex = sex;
        this.dateOfExpiry = dateOfExpiry;
        this.photoJpeg = photoJpeg;
    }
}
