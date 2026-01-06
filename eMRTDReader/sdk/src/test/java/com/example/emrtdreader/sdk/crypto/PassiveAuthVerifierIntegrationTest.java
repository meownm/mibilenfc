package com.example.emrtdreader.sdk.crypto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.example.emrtdreader.sdk.models.VerificationResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PassiveAuthVerifierIntegrationTest {

    @Test
    public void verify_reportsSignatureHashesAndTrustStatus() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        TestSodFixtures.TestSodData sodData = TestSodFixtures.buildSodWithHashes();

        VerificationResult result = PassiveAuthVerifier.verify(context, sodData.sodBytes, sodData.dgBytes);

        assertTrue(result.sodSignatureValid);
        assertTrue(result.dgHashesMatch);
        assertFalse(result.cscaTrusted);
        assertTrue(result.details.contains("SOD signature: OK"));
        assertTrue(result.details.contains("DG hashes: OK"));
        assertTrue(result.details.contains("CSCA trust"));
    }
}
