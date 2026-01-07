package com.example.emrtdreader.sdk.analysis;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MrzPipelineStateIntegrationTest {

    @Test
    public void valueOfRoundTripsEachState() {
        for (MrzPipelineState state : MrzPipelineState.values()) {
            MrzPipelineState parsed = MrzPipelineState.valueOf(state.name());
            assertNotNull(parsed);
            assertEquals(state, parsed);
        }
    }
}
