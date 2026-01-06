package com.example.emrtdreader.sdk.models;

import java.io.Serializable;

public class VerificationResult implements Serializable {
    public final boolean sodSignatureValid;
    public final boolean dgHashesMatch;
    public final boolean cscaTrusted; // DS cert path validated to CSCA
    public final String details;

    public VerificationResult(boolean sodSignatureValid, boolean dgHashesMatch, boolean cscaTrusted, String details) {
        this.sodSignatureValid = sodSignatureValid;
        this.dgHashesMatch = dgHashesMatch;
        this.cscaTrusted = cscaTrusted;
        this.details = details;
    }
}
