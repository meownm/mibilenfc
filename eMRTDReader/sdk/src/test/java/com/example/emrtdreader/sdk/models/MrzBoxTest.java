package com.example.emrtdreader.sdk.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class MrzBoxTest {
    @Test
    public void constructorSetsBounds() {
        MrzBox box = new MrzBox(1.5f, 2.5f, 10.5f, 20.5f);

        assertEquals(1.5f, box.left, 0.0001f);
        assertEquals(2.5f, box.top, 0.0001f);
        assertEquals(10.5f, box.right, 0.0001f);
        assertEquals(20.5f, box.bottom, 0.0001f);
    }

    @Test
    public void constructorRejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> new MrzBox(5f, 2f, 4f, 10f));
        assertThrows(IllegalArgumentException.class, () -> new MrzBox(1f, 8f, 4f, 6f));
    }
}
