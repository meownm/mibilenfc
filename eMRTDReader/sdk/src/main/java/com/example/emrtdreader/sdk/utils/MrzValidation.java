package com.example.emrtdreader.sdk.utils;

import com.example.emrtdreader.sdk.models.MrzChecksums;

public final class MrzValidation {
    private MrzValidation() {}

    public static int scoreTd3(String l1, String l2) {
        return checksumsTd3(l2).passedCount;
    }

    public static int scoreTd1(String l1, String l2, String l3) {
        return checksumsTd1(l1, l2, l3).passedCount;
    }

    public static MrzChecksums checksumsTd3(String l2) {
        boolean documentNumberOk = MrzChecksum.checksum(l2.substring(0, 9)) == MrzChecksum.digit(l2.charAt(9));
        boolean birthDateOk = MrzChecksum.checksum(l2.substring(13, 19)) == MrzChecksum.digit(l2.charAt(19));
        boolean expiryDateOk = MrzChecksum.checksum(l2.substring(21, 27)) == MrzChecksum.digit(l2.charAt(27));

        String composite = l2.substring(0, 10) + l2.substring(13, 20) + l2.substring(21, 43);
        boolean finalChecksumOk = MrzChecksum.checksum(composite) == MrzChecksum.digit(l2.charAt(43));

        return new MrzChecksums(documentNumberOk, birthDateOk, expiryDateOk, finalChecksumOk);
    }

    public static MrzChecksums checksumsTd1(String l1, String l2, String l3) {
        boolean documentNumberOk = MrzChecksum.checksum(l1.substring(5, 14)) == MrzChecksum.digit(l1.charAt(14));
        boolean birthDateOk = MrzChecksum.checksum(l2.substring(0, 6)) == MrzChecksum.digit(l2.charAt(6));
        boolean expiryDateOk = MrzChecksum.checksum(l2.substring(8, 14)) == MrzChecksum.digit(l2.charAt(14));

        String composite = l1.substring(5, 30) + l2.substring(0, 7) + l2.substring(8, 15) + l3.substring(0, 29);
        boolean finalChecksumOk = MrzChecksum.checksum(composite) == MrzChecksum.digit(l3.charAt(29));

        return new MrzChecksums(documentNumberOk, birthDateOk, expiryDateOk, finalChecksumOk);
    }
}
