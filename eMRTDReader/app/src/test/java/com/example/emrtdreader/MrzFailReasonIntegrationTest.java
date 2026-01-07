package com.example.emrtdreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Intent;

import com.example.emrtdreader.sdk.models.MrzFailReason;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MrzFailReasonIntegrationTest {

    @Test
    public void intentExtrasPreserveMrzFailReason() {
        Intent intent = new Intent();
        intent.putExtra("mrzFailReason", MrzFailReason.CHECKSUM_FAIL);

        Object extra = intent.getSerializableExtra("mrzFailReason");
        assertNotNull(extra);
        assertEquals(MrzFailReason.CHECKSUM_FAIL, extra);
    }
}
