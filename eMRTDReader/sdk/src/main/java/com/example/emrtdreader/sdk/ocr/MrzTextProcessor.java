package com.example.emrtdreader.sdk.ocr;

import android.util.Log;

import com.example.emrtdreader.sdk.models.MrzFormat;
import com.example.emrtdreader.sdk.models.MrzResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MrzTextProcessor {

    private static final String TAG = "MRZ_PROC";

    private MrzTextProcessor() {}

    // =====================================================================
    // PUBLIC API (СОХРАНЯЕМ КОНТРАКТ)
    // =====================================================================

    /** Используется по всему пайплайну */
    public static MrzResult normalizeAndRepair(String rawOcrText) {
        return parse(rawOcrText);
    }

    /** Основной парсер */
    public static MrzResult parse(String rawOcrText) {
        if (rawOcrText == null || rawOcrText.trim().isEmpty()) {
            return null;
        }

        List<String> lines = normalizeLines(rawOcrText);

        if (lines.size() == 2 && lines.get(0).length() == 44) {
            return parseTD3(lines);
        }

        if (lines.size() == 3 && lines.get(0).length() == 30) {
            return parseTD1(lines);
        }

        return null;
    }

    // =====================================================================
    // TD3 (PASSPORT)
    // =====================================================================

    private static MrzResult parseTD3(List<String> lines) {
        try {
            String l2 = lines.get(1);

            String passportNumber = l2.substring(0, 9).replace("<", "");
            String birthDate = l2.substring(13, 19);
            String expiryDate = l2.substring(21, 27);

            if (!isDateValid(birthDate) || !isDateValid(expiryDate)) {
                return null;
            }

            int confidence = computeConfidence(lines, MrzFormat.TD3);

            return new MrzResult(
                    passportNumber,
                    birthDate,
                    expiryDate,
                    MrzFormat.TD3,
                    confidence
            );

        } catch (Throwable t) {
            Log.d(TAG, "TD3 parse failed", t);
            return null;
        }
    }

    // =====================================================================
    // TD1 (ID CARD)
    // =====================================================================

    private static MrzResult parseTD1(List<String> lines) {
        try {
            String l1 = lines.get(0);
            String l2 = lines.get(1);

            String documentNumber = l1.substring(5, 14).replace("<", "");
            String birthDate = l2.substring(0, 6);
            String expiryDate = l2.substring(8, 14);

            if (!isDateValid(birthDate) || !isDateValid(expiryDate)) {
                return null;
            }

            int confidence = computeConfidence(lines, MrzFormat.TD1);

            return new MrzResult(
                    documentNumber,
                    birthDate,
                    expiryDate,
                    MrzFormat.TD1,
                    confidence
            );

        } catch (Throwable t) {
            Log.d(TAG, "TD1 parse failed", t);
            return null;
        }
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private static List<String> normalizeLines(String raw) {
        String[] split = raw
                .toUpperCase(Locale.US)
                .replace('«', '<')
                .replace('»', '<')
                .replace('‹', '<')
                .replace('›', '<')
                .replaceAll("[^A-Z0-9<\\n]", "")
                .split("\\R");

        List<String> result = new ArrayList<>();
        for (String s : split) {
            if (s == null) continue;
            String line = s.trim();
            if (line.length() >= 30) {
                result.add(padOrTrim(line));
            }
        }
        return result;
    }

    private static String padOrTrim(String line) {
        if (line.length() > 44) {
            return line.substring(0, 44);
        }
        if (line.length() < 30) {
            return String.format(Locale.US, "%-30s", line).replace(' ', '<');
        }
        return line;
    }

    private static boolean isDateValid(String yymmdd) {
        if (yymmdd == null || yymmdd.length() != 6) return false;
        for (int i = 0; i < 6; i++) {
            if (!Character.isDigit(yymmdd.charAt(i))) return false;
        }
        return true;
    }

    private static int computeConfidence(List<String> lines, MrzFormat format) {
        int score = 0;
        for (String l : lines) {
            if (l.contains("<<")) score++;
            if (l.matches(".*[A-Z].*")) score++;
            if (l.matches(".*\\d.*")) score++;
        }
        if (format == MrzFormat.TD3 && lines.size() == 2) score++;
        if (format == MrzFormat.TD1 && lines.size() == 3) score++;
        return Math.min(score, 5);
    }
}
