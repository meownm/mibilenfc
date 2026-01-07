package com.example.emrtdreader.sdk.ocr;

import android.graphics.Bitmap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PreprocessedMrzTest {
    @Test
    public void constructorSetsFields() {
        Bitmap bitmap = Bitmap.createBitmap(10, 5, Bitmap.Config.ARGB_8888);
        PreprocessedMrz preprocessed = new PreprocessedMrz(bitmap, 90);

        assertSame(bitmap, preprocessed.bitmap);
        assertEquals(90, preprocessed.rotationDegrees);
    }

    @Test
    public void constructorAllowsNullBitmapAndNegativeRotation() {
        PreprocessedMrz preprocessed = new PreprocessedMrz(null, -45);

        assertNull(preprocessed.bitmap);
        assertEquals(-45, preprocessed.rotationDegrees);
    }
}
