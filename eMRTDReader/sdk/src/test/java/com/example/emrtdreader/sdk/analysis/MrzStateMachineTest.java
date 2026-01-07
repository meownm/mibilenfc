package com.example.emrtdreader.sdk.analysis;

import com.example.emrtdreader.sdk.domain.MrzKey;
import com.example.emrtdreader.sdk.models.MrzFields;
import com.example.emrtdreader.sdk.models.MrzParseResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MrzStateMachineTest {

    @Test
    public void onStableBoxStartsTrackingSession() {
        MrzStateMachine machine = new MrzStateMachine();

        machine.onStableBox();

        assertEquals(MrzPipelineState.TRACKING, machine.state);
        assertTrue(machine.sessionStartMs > 0);
        assertEquals(0, machine.validStreak);
        assertNull(machine.lastValidKey);
    }

    @Test
    public void confirmedAfterTwoMatchingKeys() {
        MrzStateMachine machine = new MrzStateMachine();
        MrzParseResult result = validResult("L898902C3");

        machine.onOcrResult(result, 100L);
        assertEquals(MrzPipelineState.OCR_COOLDOWN, machine.state);
        assertEquals(1, machine.validStreak);
        assertNotNull(machine.lastValidKey);

        machine.onOcrResult(result, 200L);
        assertEquals(MrzPipelineState.CONFIRMED, machine.state);
        assertEquals(2, machine.validStreak);
        assertEquals(new MrzKey("L898902C3", "740812", "120415"), machine.lastValidKey);
    }

    @Test
    public void mismatchResetsStreak() {
        MrzStateMachine machine = new MrzStateMachine();
        MrzParseResult first = validResult("L898902C3");
        MrzParseResult second = validResult("X12345678");

        machine.onOcrResult(first, 100L);
        machine.onOcrResult(second, 200L);

        assertEquals(MrzPipelineState.OCR_COOLDOWN, machine.state);
        assertEquals(1, machine.validStreak);
        assertEquals(new MrzKey("X12345678", "740812", "120415"), machine.lastValidKey);
    }

    @Test
    public void invalidResultClearsStreak() {
        MrzStateMachine machine = new MrzStateMachine();
        MrzParseResult valid = validResult("L898902C3");
        MrzParseResult invalid = new MrzParseResult(null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, false);

        machine.onOcrResult(valid, 100L);
        machine.onOcrResult(invalid, 200L);

        assertEquals(MrzPipelineState.OCR_COOLDOWN, machine.state);
        assertEquals(0, machine.validStreak);
        assertNull(machine.lastValidKey);
    }

    private static MrzParseResult validResult(String docNumber) {
        MrzFields fields = new MrzFields(docNumber, "740812", "120415", "UTO", "F", "ERIKSSON", "ANNA MARIA");
        return new MrzParseResult(null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                fields, null, null, true);
    }
}
