package com.example.emrtdreader.crypto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.example.emrtdreader.models.VerificationResult;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.security.Security;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PassiveAuthVerifierTest {

    @BeforeClass
    public static void setUpProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    public void verify_returnsValidSignatureAndHashes() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        TestSodFixtures.TestSodData sodData = TestSodFixtures.buildSodWithHashes();

        VerificationResult result = PassiveAuthVerifier.verify(context, sodData.sodBytes, sodData.dgBytes);

        assertTrue(result.sodSignatureValid);
        assertTrue(result.dgHashesMatch);
        assertFalse(result.cscaTrusted);
    }

    @Test
    public void verify_reportsHashMismatch() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        TestSodFixtures.TestSodData sodData = TestSodFixtures.buildSodWithHashes();

        sodData.dgBytes.put(2, "WRONG".getBytes());
        VerificationResult result = PassiveAuthVerifier.verify(context, sodData.sodBytes, sodData.dgBytes);

        assertTrue(result.sodSignatureValid);
        assertFalse(result.dgHashesMatch);
    }

    @Test
    public void verify_reportsMissingSod() {
        Context context = ApplicationProvider.getApplicationContext();

        VerificationResult result = PassiveAuthVerifier.verify(context, new byte[0], null);

        assertFalse(result.sodSignatureValid);
        assertFalse(result.dgHashesMatch);
        assertFalse(result.cscaTrusted);
        assertTrue(result.details.contains("SOD missing"));
    }
}
