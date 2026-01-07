package com.example.emrtdreader.sdk.analysis;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class MrzPipelineStateTest {

    @Test
    public void valuesExposeExpectedOrdering() {
        assertArrayEquals(new MrzPipelineState[]{
                MrzPipelineState.SEARCHING,
                MrzPipelineState.TRACKING,
                MrzPipelineState.OCR_RUNNING,
                MrzPipelineState.OCR_COOLDOWN,
                MrzPipelineState.CONFIRMED,
                MrzPipelineState.TIMEOUT
        }, MrzPipelineState.values());
    }

    @Test(expected = IllegalArgumentException.class)
    public void valueOfRejectsUnknownState() {
        MrzPipelineState.valueOf("UNKNOWN");
    }
}
