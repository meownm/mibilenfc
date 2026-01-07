package com.example.emrtdreader.sdk.utils;

import com.example.emrtdreader.sdk.models.MrzChecksums;
import com.example.emrtdreader.sdk.models.MrzFields;
import com.example.emrtdreader.sdk.models.MrzFormat;
import com.example.emrtdreader.sdk.models.MrzParseResult;
import com.example.emrtdreader.sdk.models.NormalizedMrz;
import com.example.emrtdreader.sdk.ocr.MrzScore;

import java.util.List;

public final class MrzParserValidator {
    private static final int TD1_LINE_LENGTH = 30;
    private static final int TD2_LINE_LENGTH = 36;
    private static final int TD3_LINE_LENGTH = 44;

    private MrzParserValidator() {}

    public static MrzParseResult parse(NormalizedMrz normalizedMrz) {
        if (normalizedMrz == null || normalizedMrz.lines == null) {
            return emptyResult(null, null, null, null, false);
        }
        List<String> lines = normalizedMrz.lines;
        String line1 = lines.size() > 0 ? lines.get(0) : null;
        String line2 = lines.size() > 1 ? lines.get(1) : null;
        String line3 = lines.size() > 2 ? lines.get(2) : null;
        MrzFormat format = detectFormat(lines);
        boolean lengthOk = isLengthOk(format, line1, line2, line3);
        boolean charsetOk = isAllowedCharset(lines);
        MrzScore score = new MrzScore();
        score.lengthScore = lengthOk ? 1.0f : 0.0f;
        score.charsetScore = charsetOk ? 1.0f : 0.0f;
        score.structureScore = (format == MrzFormat.TD3 && lengthOk && charsetOk) ? 1.0f : 0.0f;
        score.stabilityScore = 0.0f;

        if (format == MrzFormat.TD3 && lengthOk && line1 != null && line2 != null) {
            String documentType = line1.substring(0, 2);
            String issuingCountry = line1.substring(2, 5);
            String namesBlock = line1.substring(5, TD3_LINE_LENGTH);
            String surname = parseSurname(namesBlock);
            String givenNames = parseGivenNames(namesBlock);

            String documentNumberRaw = line2.substring(0, 9);
            String documentNumber = stripFillers(documentNumberRaw);
            String nationality = line2.substring(10, 13);
            String birthDateYYMMDD = line2.substring(13, 19);
            String sex = String.valueOf(line2.charAt(20));
            String expiryDateYYMMDD = line2.substring(21, 27);
            String personalNumber = stripFillers(line2.substring(28, 42));

            MrzChecksums checksums = buildTd3Checksums(line2);
            score.checksumScore = checksums.passedCount;
            score.recalcTotal();
            boolean valid = checksums.passedCount == checksums.totalCount;
            MrzFields fields = new MrzFields(documentNumber, birthDateYYMMDD, expiryDateYYMMDD,
                    nationality, sex, surname, givenNames);

            return new MrzParseResult(format, line1, line2, line3, documentType, issuingCountry,
                    documentNumber, nationality, birthDateYYMMDD, sex, expiryDateYYMMDD, personalNumber,
                    surname, givenNames, fields, checksums, score, valid);
        }

        MrzChecksums checksums = new MrzChecksums(null, null, null, null);
        score.checksumScore = 0;
        score.recalcTotal();
        return new MrzParseResult(format, line1, line2, line3, null, null, null, null, null,
                null, null, null, null, null, null, checksums, score, false);
    }

    private static MrzParseResult emptyResult(String line1, String line2, String line3, MrzFormat format, boolean valid) {
        MrzChecksums checksums = new MrzChecksums(null, null, null, null);
        MrzScore score = new MrzScore();
        score.checksumScore = 0;
        score.lengthScore = 0.0f;
        score.charsetScore = 0.0f;
        score.structureScore = 0.0f;
        score.stabilityScore = 0.0f;
        score.recalcTotal();
        return new MrzParseResult(format, line1, line2, line3, null, null, null, null, null,
                null, null, null, null, null, null, checksums, score, valid);
    }

    private static MrzFormat detectFormat(List<String> lines) {
        if (lines == null) return null;
        int count = lines.size();
        if (count == 2) {
            int len1 = lines.get(0) != null ? lines.get(0).length() : 0;
            int len2 = lines.get(1) != null ? lines.get(1).length() : 0;
            if (len1 == TD3_LINE_LENGTH && len2 == TD3_LINE_LENGTH) return MrzFormat.TD3;
            if (len1 == TD2_LINE_LENGTH && len2 == TD2_LINE_LENGTH) return MrzFormat.TD2;
        }
        if (count == 3) {
            int len1 = lines.get(0) != null ? lines.get(0).length() : 0;
            int len2 = lines.get(1) != null ? lines.get(1).length() : 0;
            int len3 = lines.get(2) != null ? lines.get(2).length() : 0;
            if (len1 == TD1_LINE_LENGTH && len2 == TD1_LINE_LENGTH && len3 == TD1_LINE_LENGTH) {
                return MrzFormat.TD1;
            }
        }
        return null;
    }

    private static boolean isLengthOk(MrzFormat format, String line1, String line2, String line3) {
        if (format == MrzFormat.TD3) {
            return line1 != null && line2 != null
                    && line1.length() == TD3_LINE_LENGTH
                    && line2.length() == TD3_LINE_LENGTH;
        }
        if (format == MrzFormat.TD2) {
            return line1 != null && line2 != null
                    && line1.length() == TD2_LINE_LENGTH
                    && line2.length() == TD2_LINE_LENGTH;
        }
        if (format == MrzFormat.TD1) {
            return line1 != null && line2 != null && line3 != null
                    && line1.length() == TD1_LINE_LENGTH
                    && line2.length() == TD1_LINE_LENGTH
                    && line3.length() == TD1_LINE_LENGTH;
        }
        return false;
    }

    private static boolean isAllowedCharset(List<String> lines) {
        if (lines == null) return false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) return false;
            for (int j = 0; j < line.length(); j++) {
                if (!isAllowedChar(line.charAt(j))) return false;
            }
        }
        return true;
    }

    private static boolean isAllowedChar(char c) {
        return c == '<' || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z');
    }

    private static MrzChecksums buildTd3Checksums(String line2) {
        boolean documentNumberOk = MrzChecksum.checksum(line2.substring(0, 9)) == MrzChecksum.digit(line2.charAt(9));
        boolean birthDateOk = MrzChecksum.checksum(line2.substring(13, 19)) == MrzChecksum.digit(line2.charAt(19));
        boolean expiryDateOk = MrzChecksum.checksum(line2.substring(21, 27)) == MrzChecksum.digit(line2.charAt(27));

        String composite = line2.substring(0, 10) + line2.substring(13, 20) + line2.substring(21, 43);
        boolean finalChecksumOk = MrzChecksum.checksum(composite) == MrzChecksum.digit(line2.charAt(43));

        return new MrzChecksums(documentNumberOk, birthDateOk, expiryDateOk, finalChecksumOk);
    }

    private static String parseSurname(String namesBlock) {
        int delimiter = findNameDelimiter(namesBlock);
        if (delimiter < 0) {
            return normalizeName(namesBlock);
        }
        return normalizeName(namesBlock.substring(0, delimiter));
    }

    private static String parseGivenNames(String namesBlock) {
        int delimiter = findNameDelimiter(namesBlock);
        if (delimiter < 0) {
            return "";
        }
        return normalizeName(namesBlock.substring(delimiter + 2));
    }

    private static int findNameDelimiter(String namesBlock) {
        if (namesBlock == null) return -1;
        for (int i = 0; i < namesBlock.length() - 1; i++) {
            if (namesBlock.charAt(i) == '<' && namesBlock.charAt(i + 1) == '<') {
                return i;
            }
        }
        return -1;
    }

    private static String normalizeName(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        boolean lastSpace = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            char mapped = (c == '<') ? ' ' : c;
            if (mapped == ' ') {
                if (!lastSpace) {
                    out.append(mapped);
                }
                lastSpace = true;
            } else {
                out.append(mapped);
                lastSpace = false;
            }
        }
        int end = out.length();
        if (end > 0 && out.charAt(end - 1) == ' ') {
            out.deleteCharAt(end - 1);
        }
        return out.toString();
    }

    private static String stripFillers(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '<') {
                out.append(c);
            }
        }
        return out.toString();
    }
}
