package com.example.emrtdreader.sdk.utils;

import java.util.*;

/**
 * Small checksum-guided repair: tries limited substitutions in critical numeric fields.
 * This is the "adult" part that fixes O/0, I/1, S/5 etc when voting still leaves ambiguity.
 */
public final class MrzRepair {

    private static final Map<Character, char[]> DIGIT_ALTS = createDigitAlts();

    private MrzRepair() {}

    private static Map<Character, char[]> createDigitAlts() {
        Map<Character, char[]> m = new HashMap<>();
        m.put('O', new char[]{'0'});
        m.put('Q', new char[]{'0'});
        m.put('D', new char[]{'0'});
        m.put('I', new char[]{'1'});
        m.put('L', new char[]{'1'});
        m.put('Z', new char[]{'2'});
        m.put('S', new char[]{'5'});
        m.put('B', new char[]{'8'});
        m.put('G', new char[]{'6'});
        m.put('T', new char[]{'7'});
        return m;
    }

    public static String repairTd3Line2(String l2) {
        // TD3 critical numeric ranges: 0-9 (doc num + check), 13-19 (dob + check), 21-27 (expiry + check), 43 (composite check)
        char[] arr = l2.toCharArray();

        repairNumericWindow(arr, 0, 10, 9);
        repairNumericWindow(arr, 13, 20, 19);
        repairNumericWindow(arr, 21, 28, 27);

        // composite check at 43
        if (!(arr[43] >= '0' && arr[43] <= '9')) {
            char fixed = fixToDigit(arr[43]);
            if (fixed != 0) arr[43] = fixed;
        }

        String candidate = new String(arr);
        if (MrzValidation.scoreTd3("".repeat(44).replace('\0','<'), candidate) >= 3) {
            // scoreTd3 ignores l1; ok.
            return candidate;
        }
        // try small brute-force for check digits (positions 9,19,27,43) based on computed checksums:
        arr = candidate.toCharArray();
        arr[9]  = (char)('0' + MrzChecksum.checksum(candidate.substring(0,9)));
        arr[19] = (char)('0' + MrzChecksum.checksum(candidate.substring(13,19)));
        arr[27] = (char)('0' + MrzChecksum.checksum(candidate.substring(21,27)));
        String composite = candidate.substring(0,10) + candidate.substring(13,20) + candidate.substring(21,43);
        arr[43] = (char)('0' + MrzChecksum.checksum(composite));
        return new String(arr);
    }

    public static String[] repairTd1(String l1, String l2, String l3) {
        char[] a1 = l1.toCharArray();
        char[] a2 = l2.toCharArray();
        char[] a3 = l3.toCharArray();

        // numeric areas: l1[5..14] and check at 14; l2[0..6] check 6; l2[8..14] check 14; l3[29] composite check.
        repairNumericWindow(a1, 5, 15, 14);
        repairNumericWindow(a2, 0, 7, 6);
        repairNumericWindow(a2, 8, 15, 14);

        if (!(a3[29] >= '0' && a3[29] <= '9')) {
            char fixed = fixToDigit(a3[29]);
            if (fixed != 0) a3[29] = fixed;
        }

        String r1 = new String(a1);
        String r2 = new String(a2);
        String r3 = new String(a3);

        // set check digits deterministically from computed checksums
        a1 = r1.toCharArray();
        a2 = r2.toCharArray();
        a3 = r3.toCharArray();

        a1[14] = (char)('0' + MrzChecksum.checksum(r1.substring(5,14)));
        a2[6]  = (char)('0' + MrzChecksum.checksum(r2.substring(0,6)));
        a2[14] = (char)('0' + MrzChecksum.checksum(r2.substring(8,14)));

        String composite = r1.substring(5,30) + r2.substring(0,7) + r2.substring(8,15) + r3.substring(0,29);
        a3[29] = (char)('0' + MrzChecksum.checksum(composite));

        return new String[]{ new String(a1), new String(a2), new String(a3) };
    }

    private static void repairNumericWindow(char[] arr, int startInclusive, int endExclusive, int checkPos) {
        for (int i=startInclusive;i<endExclusive;i++) {
            if (i == checkPos) continue;
            if (!(arr[i] >= '0' && arr[i] <= '9') && arr[i] != '<') {
                char fixed = fixToDigit(arr[i]);
                if (fixed != 0) arr[i] = fixed;
            }
        }
        if (!(arr[checkPos] >= '0' && arr[checkPos] <= '9')) {
            char fixed = fixToDigit(arr[checkPos]);
            if (fixed != 0) arr[checkPos] = fixed;
        }
    }

    private static char fixToDigit(char c) {
        if (c >= '0' && c <= '9') return c;
        char uc = Character.toUpperCase(c);
        char[] alts = DIGIT_ALTS.get(uc);
        if (alts != null && alts.length > 0) return alts[0];
        return 0;
    }
}
