package com.example.emrtdreader.sdk.analysis;

import com.example.emrtdreader.sdk.models.MrzParseResult;
import com.example.emrtdreader.sdk.models.NormalizedMrz;
import com.example.emrtdreader.sdk.utils.MrzParserValidator;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class MrzStateMachineIntegrationTest {

    @Test
    public void confirmAfterTwoParsedMrzResults() {
        MrzStateMachine machine = new MrzStateMachine();
        MrzParseResult parsed = MrzParserValidator.parse(new NormalizedMrz(Arrays.asList(
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
        )));

        machine.onOcrResult(parsed, 100L);
        machine.onOcrResult(parsed, 200L);

        assertEquals(MrzPipelineState.CONFIRMED, machine.state);
        assertEquals(2, machine.validStreak);
    }
}
