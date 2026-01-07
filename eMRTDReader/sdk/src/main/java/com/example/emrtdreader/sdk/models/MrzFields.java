package com.example.emrtdreader.sdk.models;

import com.example.emrtdreader.sdk.domain.AccessKey;

import java.io.Serializable;
import java.util.Objects;

public final class MrzFields implements Serializable {
    private final String documentNumber;
    private final String birthDateYYMMDD;
    private final String expiryDateYYMMDD;
    private final String nationality;
    private final String sex;
    private final String surname;
    private final String givenNames;

    public MrzFields(String documentNumber,
                     String birthDateYYMMDD,
                     String expiryDateYYMMDD,
                     String nationality,
                     String sex,
                     String surname,
                     String givenNames) {
        this.documentNumber = Objects.requireNonNull(documentNumber, "documentNumber");
        this.birthDateYYMMDD = Objects.requireNonNull(birthDateYYMMDD, "birthDateYYMMDD");
        this.expiryDateYYMMDD = Objects.requireNonNull(expiryDateYYMMDD, "expiryDateYYMMDD");
        this.nationality = Objects.requireNonNull(nationality, "nationality");
        this.sex = Objects.requireNonNull(sex, "sex");
        this.surname = Objects.requireNonNull(surname, "surname");
        this.givenNames = Objects.requireNonNull(givenNames, "givenNames");
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

    public String getNationality() {
        return nationality;
    }

    public String getSex() {
        return sex;
    }

    public String getSurname() {
        return surname;
    }

    public String getGivenNames() {
        return givenNames;
    }

    public AccessKey.Mrz toAccessKey() {
        return new AccessKey.Mrz(documentNumber, birthDateYYMMDD, expiryDateYYMMDD);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MrzFields mrzFields = (MrzFields) o;
        return documentNumber.equals(mrzFields.documentNumber)
            && birthDateYYMMDD.equals(mrzFields.birthDateYYMMDD)
            && expiryDateYYMMDD.equals(mrzFields.expiryDateYYMMDD)
            && nationality.equals(mrzFields.nationality)
            && sex.equals(mrzFields.sex)
            && surname.equals(mrzFields.surname)
            && givenNames.equals(mrzFields.givenNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentNumber, birthDateYYMMDD, expiryDateYYMMDD, nationality, sex, surname, givenNames);
    }

    @Override
    public String toString() {
        return "MrzFields{"
            + "documentNumber='" + documentNumber + '\''
            + ", birthDateYYMMDD='" + birthDateYYMMDD + '\''
            + ", expiryDateYYMMDD='" + expiryDateYYMMDD + '\''
            + ", nationality='" + nationality + '\''
            + ", sex='" + sex + '\''
            + ", surname='" + surname + '\''
            + ", givenNames='" + givenNames + '\''
            + '}';
    }
}
