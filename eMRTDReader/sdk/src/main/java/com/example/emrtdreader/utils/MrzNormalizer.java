package com.example.emrtdreader.utils;

import com.example.emrtdreader.models.MrzFormat;
import com.example.emrtdreader.models.MrzResult;

import java.util.*;

public final class MrzNormalizer {
    private static final String ALLOWED = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<";
    private static final Map<Character, Character> OCR_DIGIT_FIX = createFixMap();

    private MrzNormalizer() {}

    private static Map<Character, Character> createFixMap() {
        Map<Character, Character> m = new HashMap<>();
        m.put('O','0'); m.put('Q','0'); m.put('D','0');
        m.put('I','1'); m.put('L','1');
        m.put('Z','2');
        m.put('S','5');
        m.put('B','8');
        m.put('G','6');
        m.put('T','7');
        return m;
    }

    public static MrzResult normalizeBest(String rawOcrText) {
        if (rawOcrText == null) return null;

        List<MrzResult> candidates = new ArrayList<>();
        candidates.addAll(findTd3Candidates(rawOcrText));
        candidates.addAll(findTd1Candidates(rawOcrText));

        MrzResult best = null;
        for (MrzResult c : candidates) {
            if (best == null || c.confidence > best.confidence) best = c;
        }
        return best != null && best.confidence >= 3 ? best : null;
    }

    private static String normalizeRaw(String text) {
        String t = text.toUpperCase(Locale.US).replace(" ", "").replace("\n", "");
        StringBuilder sb = new StringBuilder(t.length());
        for (int i=0;i<t.length();i++){
            char c=t.charAt(i);
            if (ALLOWED.indexOf(c)>=0) sb.append(c);
        }
        return sb.toString();
    }

    // ---------- TD3 ----------
    private static List<MrzResult> findTd3Candidates(String text) {
        String clean = normalizeRaw(text);
        List<MrzResult> out = new ArrayList<>();
        if (clean.length() < 88) return out;

        for (int i=0;i<=clean.length()-88;i++) {
            String l1 = clean.substring(i, i+44);
            String l2 = clean.substring(i+44, i+88);
            String nl1 = normalizeLine(l1, false, 44);
            String nl2 = normalizeLine(l2, true, 44);

            int score = MrzValidation.scoreTd3(nl1, nl2);
            if (score > 0) out.add(new MrzResult(nl1, nl2, null, MrzFormat.TD3, score));
        }
        return out;
    }

    // ---------- TD1 ----------
    private static List<MrzResult> findTd1Candidates(String text) {
        String clean = normalizeRaw(text);
        List<MrzResult> out = new ArrayList<>();
        if (clean.length() < 90) return out;

        for (int i=0;i<=clean.length()-90;i++) {
            String l1 = clean.substring(i, i+30);
            String l2 = clean.substring(i+30, i+60);
            String l3 = clean.substring(i+60, i+90);

            String nl1 = normalizeLine(l1, false, 30);
            String nl2 = normalizeLine(l2, true, 30);
            String nl3 = normalizeLine(l3, false, 30);

            int score = MrzValidation.scoreTd1(nl1, nl2, nl3);
            if (score > 0) out.add(new MrzResult(nl1, nl2, nl3, MrzFormat.TD1, score));
        }
        return out;
    }

    private static String normalizeLine(String line, boolean numeric, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i=0;i<line.length();i++) {
            char c = line.charAt(i);
            if (ALLOWED.indexOf(c) >= 0) {
                sb.append(c);
            } else if (numeric) {
                Character fixed = OCR_DIGIT_FIX.get(c);
                sb.append(fixed != null ? fixed : '<');
            } else {
                sb.append('<');
            }
        }
        while (sb.length() < length) sb.append('<');
        if (sb.length() > length) sb.setLength(length);
        return sb.toString();
    }
}
