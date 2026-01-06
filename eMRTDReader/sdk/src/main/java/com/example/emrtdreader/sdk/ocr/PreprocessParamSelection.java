package com.example.emrtdreader.sdk.ocr;

import java.util.List;

public final class PreprocessParamSelection {
    private PreprocessParamSelection() {
    }

    public static double scoreText(String text) {
        return MrzScore.score(text);
    }

    public static int pickBestIndex(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return -1;
        }
        int bestIndex = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < texts.size(); i++) {
            double score = scoreText(texts.get(i));
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }
}
