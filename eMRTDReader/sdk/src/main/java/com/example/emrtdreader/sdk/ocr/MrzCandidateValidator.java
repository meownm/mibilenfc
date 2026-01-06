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

    public static int score(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return 0;
        }
        int valid = 0;
        int invalid = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '<' || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z')) {
                valid++;
            } else {
                invalid++;
            }
        }
        int score = valid - invalid;
        if (normalized.contains("<<")) {
            score += 20;
        }
        if (normalized.length() >= 44) {
            score += 20;
        }
        if (isValid(normalized)) {
            score += 1000;
        }
        return score;
    }

    public static String normalize(String text) {
        if (text == null) return "";
        String stripped = text.replaceAll("\\s", "");
        return stripped.toUpperCase(Locale.US);
    }
}
