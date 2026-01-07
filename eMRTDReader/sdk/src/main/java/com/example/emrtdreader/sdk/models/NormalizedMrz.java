package com.example.emrtdreader.sdk.models;

import java.util.ArrayList;
import java.util.List;

public final class NormalizedMrz {
    public final List<String> lines;

    public NormalizedMrz(List<String> lines) {
        if (lines == null) {
            throw new IllegalArgumentException("lines");
        }
        this.lines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            this.lines.add(lines.get(i));
        }
    }
}
