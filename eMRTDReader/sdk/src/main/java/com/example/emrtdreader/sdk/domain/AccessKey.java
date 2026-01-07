package com.example.emrtdreader.sdk.domain;

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

        public Mrz(MrzKey key) {
            this(key.getDocumentNumber(), key.getBirthDateYYMMDD(), key.getExpiryDateYYMMDD());
        }

        public MrzKey toMrzKey() {
            return new MrzKey(documentNumber, dateOfBirthYYMMDD, dateOfExpiryYYMMDD);
        }
    }
}
