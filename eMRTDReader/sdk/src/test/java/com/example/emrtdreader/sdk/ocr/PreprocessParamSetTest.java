package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PreprocessParamSetTest {
    @Test
    public void candidateListMatchesExpected() {
        List<PreprocessParams> expected = Arrays.asList(
                new PreprocessParams(15, 5, 2.0f, 0),
                new PreprocessParams(17, 7, 2.25f, 1),
                new PreprocessParams(21, 9, 2.5f, 1),
                new PreprocessParams(13, 3, 1.75f, 0)
        );

        assertEquals(expected, PreprocessParamSet.getCandidates());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void candidateListIsImmutable() {
        PreprocessParamSet.getCandidates().add(new PreprocessParams(15, 5, 2.0f, 0));
    }

    @Test
    public void candidateValuesAreValid() {
        for (PreprocessParams params : PreprocessParamSet.getCandidates()) {
            assertTrue(params.blockSize >= 3);
            assertTrue(params.blockSize % 2 == 1);
            assertTrue(params.scale > 0f);
            assertTrue(params.blurRadius >= 0);
            assertFalse(params.toString().isEmpty());
        }
    }
}
