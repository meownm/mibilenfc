package com.example.emrtdreader.sdk.ocr;

import java.util.Locale;

public final class MrzCandidateValidator {
    private MrzCandidateValidator() {}

    public static boolean isValid(String text) {
        String normalized = normalize(text);
        if (normalized.length() < 44) return false;
        if (!normalized.contains("<<")) return false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '<') continue;
            if (c >= '0' && c <= '9') continue;
            if (c >= 'A' && c <= 'Z') continue;
            return false;
        }
        return true;
    }

    public static String normalize(String text) {
        if (text == null) return "";
        String stripped = text.replaceAll("\\s", "");
        return stripped.toUpperCase(Locale.US);
    }
}
