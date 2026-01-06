package com.example.emrtdreader.sdk.utils;

public final class MrzChecksum {
    private static final int[] WEIGHTS = new int[]{7, 3, 1};

    private MrzChecksum() {}

    public static int checksum(String data) {
        int sum = 0;
        for (int i = 0; i < data.length(); i++) {
            int v = valueOf(data.charAt(i));
            sum += v * WEIGHTS[i % 3];
        }
        return sum % 10;
    }

    public static int digit(char c) {
        return (c >= '0' && c <= '9') ? (c - '0') : -1;
    }

    public static int valueOf(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'Z') return c - 'A' + 10;
        if (c == '<') return 0;
        return 0;
    }
}
