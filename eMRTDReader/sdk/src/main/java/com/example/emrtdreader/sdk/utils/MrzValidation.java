package com.example.emrtdreader.sdk.utils;

public final class MrzValidation {
    private MrzValidation() {}

    public static int scoreTd3(String l1, String l2) {
        int score = 0;
        if (MrzChecksum.checksum(l2.substring(0,9)) == MrzChecksum.digit(l2.charAt(9))) score++;
        if (MrzChecksum.checksum(l2.substring(13,19)) == MrzChecksum.digit(l2.charAt(19))) score++;
        if (MrzChecksum.checksum(l2.substring(21,27)) == MrzChecksum.digit(l2.charAt(27))) score++;

        String composite = l2.substring(0,10) + l2.substring(13,20) + l2.substring(21,43);
        if (MrzChecksum.checksum(composite) == MrzChecksum.digit(l2.charAt(43))) score++;

        return score;
    }

    public static int scoreTd1(String l1, String l2, String l3) {
        int score = 0;
        if (MrzChecksum.checksum(l1.substring(5,14)) == MrzChecksum.digit(l1.charAt(14))) score++;
        if (MrzChecksum.checksum(l2.substring(0,6)) == MrzChecksum.digit(l2.charAt(6))) score++;
        if (MrzChecksum.checksum(l2.substring(8,14)) == MrzChecksum.digit(l2.charAt(14))) score++;

        String composite = l1.substring(5,30) + l2.substring(0,7) + l2.substring(8,15) + l3.substring(0,29);
        if (MrzChecksum.checksum(composite) == MrzChecksum.digit(l3.charAt(29))) score++;

        return score;
    }
}
