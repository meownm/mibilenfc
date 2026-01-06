package com.example.emrtdreader.sdk.ocr;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PreprocessParamSet {
    private static final List<PreprocessParams> CANDIDATES = Collections.unmodifiableList(Arrays.asList(
            new PreprocessParams(15, 5, 2.0f, 0),
            new PreprocessParams(17, 7, 2.25f, 1),
            new PreprocessParams(21, 9, 2.5f, 1),
            new PreprocessParams(13, 3, 1.75f, 0)
    ));

    private PreprocessParamSet() {
    }

    public static List<PreprocessParams> getCandidates() {
        return CANDIDATES;
    }

    public static PreprocessParams getDefault() {
        return CANDIDATES.get(0);
    }
}
