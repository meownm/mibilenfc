package com.example.emrtdreader.models;

import java.io.Serializable;

public class MrzResult implements Serializable {
    public final String line1;
    public final String line2;
    public final String line3; // null for TD3
    public final MrzFormat format;
    public final int confidence; // burst/repair score 0..4

    public MrzResult(String line1, String line2, String line3, MrzFormat format, int confidence) {
        this.line1 = line1;
        this.line2 = line2;
        this.line3 = line3;
        this.format = format;
        this.confidence = confidence;
    }

    public String asMrzText() {
        if (format == MrzFormat.TD1) return line1 + "\n" + line2 + "\n" + (line3 == null ? "" : line3);
        return line1 + "\n" + line2;
    }
}
