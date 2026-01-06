package com.example.emrtdreader.sdk.ocr;

import java.util.List;

public final class PreprocessParamSelection {
    private PreprocessParamSelection() {
    }

    public static int scoreText(String text) {
        return MrzCandidateValidator.score(text);
    }

    public static int pickBestIndex(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return -1;
        }
        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < texts.size(); i++) {
            int score = scoreText(texts.get(i));
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }
}
