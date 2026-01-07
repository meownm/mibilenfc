package com.example.emrtdreader.sdk.models;

import com.example.emrtdreader.sdk.ocr.MrzScore;

public final class MrzParseResult {
    public final MrzFormat format;
    public final String line1;
    public final String line2;
    public final String line3;
    public final String documentType;
    public final String issuingCountry;
    public final String documentNumber;
    public final String nationality;
    public final String birthDateYYMMDD;
    public final String sex;
    public final String expiryDateYYMMDD;
    public final String personalNumber;
    public final String surname;
    public final String givenNames;
    public final MrzFields fields;
    public final MrzChecksums checksums;
    public final MrzScore score;
    public final boolean valid;

    public MrzParseResult(MrzFormat format,
                          String line1,
                          String line2,
                          String line3,
                          String documentType,
                          String issuingCountry,
                          String documentNumber,
                          String nationality,
                          String birthDateYYMMDD,
                          String sex,
                          String expiryDateYYMMDD,
                          String personalNumber,
                          String surname,
                          String givenNames,
                          MrzFields fields,
                          MrzChecksums checksums,
                          MrzScore score,
                          boolean valid) {
        this.format = format;
        this.line1 = line1;
        this.line2 = line2;
        this.line3 = line3;
        this.documentType = documentType;
        this.issuingCountry = issuingCountry;
        this.documentNumber = documentNumber;
        this.nationality = nationality;
        this.birthDateYYMMDD = birthDateYYMMDD;
        this.sex = sex;
        this.expiryDateYYMMDD = expiryDateYYMMDD;
        this.personalNumber = personalNumber;
        this.surname = surname;
        this.givenNames = givenNames;
        this.fields = fields;
        this.checksums = checksums;
        this.score = score;
        this.valid = valid;
    }
}
