package com.example.emrtdreader.domain;

import com.example.emrtdreader.models.PassportChipData;
import com.example.emrtdreader.models.VerificationResult;

import java.io.Serializable;

public class PassportReadResult implements Serializable {
    public final PassportChipData chipData;
    public final VerificationResult verification;

    public PassportReadResult(PassportChipData chipData, VerificationResult verification) {
        this.chipData = chipData;
        this.verification = verification;
    }
}
