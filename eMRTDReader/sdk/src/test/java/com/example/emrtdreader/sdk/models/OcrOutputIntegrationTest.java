package com.example.emrtdreader.sdk.models;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.assertEquals;

public class OcrOutputIntegrationTest {
    @Test
    public void serializationRoundTripPreservesValues() throws Exception {
        OcrOutput original = new OcrOutput("L898902C36UTO7408122F1204159ZE184226B<<<<<10", 88L, 0.85f, 22);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(original);
        }

        OcrOutput decoded;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            decoded = (OcrOutput) ois.readObject();
        }

        assertEquals(original.rawText, decoded.rawText);
        assertEquals(original.elapsedMs, decoded.elapsedMs);
        assertEquals(original.whitelistRatio, decoded.whitelistRatio, 0.0001f);
        assertEquals(original.ltCount, decoded.ltCount);
    }
}
