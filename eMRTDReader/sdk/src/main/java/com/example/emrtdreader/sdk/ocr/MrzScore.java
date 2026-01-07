package com.example.emrtdreader.sdk.ocr;

import com.example.emrtdreader.sdk.utils.MrzChecksum;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scores OCR text as a TD3 MRZ candidate.
 *
 * <p>Scoring rules:</p>
 * <ul>
 *     <li>Parse OCR text into two MRZ lines (TD3), trimming whitespace.</li>
 *     <li>Reject unless there are exactly 2 lines of length 44.</li>
 *     <li>Reject if any character is not in [A-Z0-9&lt;].</li>
 *     <li>Compute the four TD3 checksum validations.</li>
 * </ul>
 *
 * <p>The final score is normalized to 0..1 and is the ratio of checksum
 * validations that pass (0-4). If strict parsing fails, the score is 0.</p>
 */
public final class MrzScore {
    private static final int TD3_LINE_LENGTH = 44;

    public int checksumScore;
    public float lengthScore;
    public float charsetScore;
    public float structureScore;
    public float stabilityScore;
    public float totalScore;

    public MrzScore() {}

    public void recalcTotal() {
        totalScore = (checksumScore * 10.0f)
                + (lengthScore * 2.0f)
                + (charsetScore * 2.0f)
                + (structureScore * 3.0f)
                + (stabilityScore * 5.0f);
    }

    public static double score(String text) {
        ParsedMrz parsed = parse(text);
        if (parsed == null) {
            return 0.0;
        }
        if (!isStrictTd3(parsed)) {
            return 0.0;
        }
        int checksumHits = checksumScore(parsed.line2);
        return checksumHits / 4.0;
    }

    static ParsedMrz parse(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replace('\r', '\n');
        String[] rawLines = normalized.split("\n");
        List<String> lines = new ArrayList<>(2);
        for (String raw : rawLines) {
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String compact = trimmed.replaceAll("\\s", "").toUpperCase(Locale.US);
            if (!compact.isEmpty()) {
                lines.add(compact);
            }
        }
        if (lines.size() == 1 && lines.get(0).length() == TD3_LINE_LENGTH * 2) {
            String combined = lines.get(0);
            return new ParsedMrz(combined.substring(0, TD3_LINE_LENGTH),
                    combined.substring(TD3_LINE_LENGTH));
        }
        if (lines.size() != 2) {
            return null;
        }
        return new ParsedMrz(lines.get(0), lines.get(1));
    }

    private static boolean isStrictTd3(ParsedMrz parsed) {
        if (parsed.line1.length() != TD3_LINE_LENGTH || parsed.line2.length() != TD3_LINE_LENGTH) {
            return false;
        }
        for (int i = 0; i < TD3_LINE_LENGTH; i++) {
            if (!isAllowedChar(parsed.line1.charAt(i))) {
                return false;
            }
            if (!isAllowedChar(parsed.line2.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllowedChar(char c) {
        return c == '<' || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z');
    }

    private static int checksumScore(String line2) {
        int score = 0;
        if (MrzChecksum.checksum(line2.substring(0, 9)) == MrzChecksum.digit(line2.charAt(9))) {
            score++;
        }
        if (MrzChecksum.checksum(line2.substring(13, 19)) == MrzChecksum.digit(line2.charAt(19))) {
            score++;
        }
        if (MrzChecksum.checksum(line2.substring(21, 27)) == MrzChecksum.digit(line2.charAt(27))) {
            score++;
        }

        String composite = line2.substring(0, 10) + line2.substring(13, 20) + line2.substring(21, 43);
        if (MrzChecksum.checksum(composite) == MrzChecksum.digit(line2.charAt(43))) {
            score++;
        }
        return score;
    }

    static final class ParsedMrz {
        final String line1;
        final String line2;

        private ParsedMrz(String line1, String line2) {
            this.line1 = line1;
            this.line2 = line2;
        }
    }
}
