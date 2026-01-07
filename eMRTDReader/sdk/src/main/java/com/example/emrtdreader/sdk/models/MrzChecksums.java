package com.example.emrtdreader.sdk.models;

public class MrzChecksums {
    public final Boolean documentNumberOk;
    public final Boolean birthDateOk;
    public final Boolean expiryDateOk;
    public final Boolean finalChecksumOk;
    public final int passedCount;
    public final int totalCount;

    public MrzChecksums(Boolean documentNumberOk, Boolean birthDateOk, Boolean expiryDateOk, Boolean finalChecksumOk) {
        this.documentNumberOk = documentNumberOk;
        this.birthDateOk = birthDateOk;
        this.expiryDateOk = expiryDateOk;
        this.finalChecksumOk = finalChecksumOk;
        this.totalCount = 4;
        this.passedCount = countPassed(documentNumberOk, birthDateOk, expiryDateOk, finalChecksumOk);
    }

    private int countPassed(Boolean documentNumberOk, Boolean birthDateOk, Boolean expiryDateOk, Boolean finalChecksumOk) {
        int passed = 0;
        if (Boolean.TRUE.equals(documentNumberOk)) passed++;
        if (Boolean.TRUE.equals(birthDateOk)) passed++;
        if (Boolean.TRUE.equals(expiryDateOk)) passed++;
        if (Boolean.TRUE.equals(finalChecksumOk)) passed++;
        return passed;
    }
}
