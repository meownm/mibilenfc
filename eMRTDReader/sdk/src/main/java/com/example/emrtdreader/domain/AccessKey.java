package com.example.emrtdreader.domain;

import java.io.Serializable;

public abstract class AccessKey implements Serializable {

    public static final class Mrz extends AccessKey {
        public final String documentNumber;
        public final String dateOfBirthYYMMDD;
        public final String dateOfExpiryYYMMDD;

        public Mrz(String documentNumber, String dob, String doe) {
            this.documentNumber = documentNumber;
            this.dateOfBirthYYMMDD = dob;
            this.dateOfExpiryYYMMDD = doe;
        }
    }
}
