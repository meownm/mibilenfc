package com.example.emrtdreader.sdk.analyzer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import androidx.camera.core.ImageProxy;

import org.junit.Test;
import org.mockito.Mockito;

public class FrameEnvelopeTest {

    @Test
    public void constructorStoresMetadata() {
        ImageProxy imageProxy = Mockito.mock(ImageProxy.class);

        FrameEnvelope envelope = new FrameEnvelope(imageProxy, 1234L, 90, 1920, 1080);

        assertSame(imageProxy, envelope.getImage());
        assertEquals(1234L, envelope.getTimestampMs());
        assertEquals(90, envelope.getRotationDegrees());
        assertEquals(1920, envelope.getFrameWidth());
        assertEquals(1080, envelope.getFrameHeight());
    }

    @Test(expected = NullPointerException.class)
    public void constructorRejectsNullImage() {
        new FrameEnvelope(null, 0L, 0, 0, 0);
    }
}
