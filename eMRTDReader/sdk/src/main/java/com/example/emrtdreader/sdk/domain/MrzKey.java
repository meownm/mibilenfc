package com.example.emrtdreader.sdk.domain;

import java.util.Objects;

public final class MrzKey {
    private final String documentNumber;
    private final String birthDateYYMMDD;
    private final String expiryDateYYMMDD;

    public MrzKey(String documentNumber, String birthDateYYMMDD, String expiryDateYYMMDD) {
        this.documentNumber = Objects.requireNonNull(documentNumber, "documentNumber");
        this.birthDateYYMMDD = Objects.requireNonNull(birthDateYYMMDD, "birthDateYYMMDD");
        this.expiryDateYYMMDD = Objects.requireNonNull(expiryDateYYMMDD, "expiryDateYYMMDD");
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getBirthDateYYMMDD() {
        return birthDateYYMMDD;
    }

    public String getExpiryDateYYMMDD() {
        return expiryDateYYMMDD;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MrzKey)) return false;
        MrzKey mrzKey = (MrzKey) o;
        return documentNumber.equals(mrzKey.documentNumber)
                && birthDateYYMMDD.equals(mrzKey.birthDateYYMMDD)
                && expiryDateYYMMDD.equals(mrzKey.expiryDateYYMMDD);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentNumber, birthDateYYMMDD, expiryDateYYMMDD);
    }
}
