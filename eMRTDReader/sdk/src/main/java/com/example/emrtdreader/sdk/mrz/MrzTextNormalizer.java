package com.example.emrtdreader.sdk.mrz;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utilities for MRZ text normalization before parsing.
 *
 * Responsibilities:
 * - normalize OCR typography noise
 * - convert quotes / spaces to '<'
 * - contextually fix K -> < in filler zones
 * - split text into exactly 2 MRZ lines
 */
public final class MrzTextNormalizer {

    private MrzTextNormalizer() {}

    /** Normalize raw OCR text to MRZ-safe alphabet */
    public static String normalize(String text) {
        if (text == null) return "";

        String s = text.toUpperCase(Locale.US)
                .replace('«', '<')
                .replace('»', '<')
                .replace('‹', '<')
                .replace('›', '<')
                .replace('"', '<')
                .replace(' ', '<');

        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') ||
                    c == '<' ||
                    c == 'K' ||
                    c == '\n') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Replace K -> < only when K is used as MRZ filler */
    public static String fixKAsFiller(String line) {
        if (line == null) return null;

        char[] a = line.toCharArray();
        for (int i = 0; i < a.length; i++) {
            if (a[i] != 'K') continue;

            int left = Math.max(0, i - 4);
            int right = Math.min(a.length - 1, i + 4);

            int fillerLike = 0;
            for (int j = left; j <= right; j++) {
                if (a[j] == '<') fillerLike++;
            }

            if (fillerLike >= 2) {
                a[i] = '<';
            }
        }
        return new String(a);
    }

    /** Split normalized text and keep first 2 non-empty lines */
    public static List<String> splitToTwoLines(String normalized) {
        String[] parts = normalized.split("\\n+");
        List<String> out = new ArrayList<>(2);
        for (String p : parts) {
            if (p == null) continue;
            p = p.trim();
            if (!p.isEmpty()) {
                out.add(p);
            }
            if (out.size() == 2) break;
        }
        return out;
    }

    /** Pad or trim MRZ line to exactly 44 chars */
    public static String padOrTrim44(String line) {
        if (line == null) return null;
        if (line.length() > 44) return line.substring(0, 44);
        if (line.length() < 44) {
            return line + "<".repeat(44 - line.length());
        }
        return line;
    }
}
