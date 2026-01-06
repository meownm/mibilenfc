package com.example.emrtdreader.crypto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.example.emrtdreader.models.VerificationResult;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jmrtd.lds.SODFile;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
        TestSodData sodData = buildSodWithHashes();

        VerificationResult result = PassiveAuthVerifier.verify(context, sodData.sodBytes, sodData.dgBytes);

        assertTrue(result.sodSignatureValid);
        assertTrue(result.dgHashesMatch);
        assertFalse(result.cscaTrusted);
    }

    @Test
    public void verify_reportsHashMismatch() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        TestSodData sodData = buildSodWithHashes();

        sodData.dgBytes.put(2, "WRONG".getBytes());
        VerificationResult result = PassiveAuthVerifier.verify(context, sodData.sodBytes, sodData.dgBytes);

        assertTrue(result.sodSignatureValid);
        assertFalse(result.dgHashesMatch);
    }

    private static TestSodData buildSodWithHashes() throws Exception {
        byte[] dg1 = "DG1".getBytes();
        byte[] dg2 = "DG2".getBytes();

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        Map<Integer, byte[]> dgHashes = new HashMap<>();
        dgHashes.put(1, md.digest(dg1));
        dgHashes.put(2, md.digest(dg2));

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        X509Certificate cert = generateSelfSignedCert(keyPair);

        SODFile sodFile = new SODFile("SHA-256", "SHA256withRSA", dgHashes, keyPair.getPrivate(), cert);

        Map<Integer, byte[]> dgBytes = new HashMap<>();
        dgBytes.put(1, dg1);
        dgBytes.put(2, dg2);

        return new TestSodData(sodFile.getEncoded(), dgBytes);
    }

    private static X509Certificate generateSelfSignedCert(KeyPair keyPair) throws Exception {
        X500Name subject = new X500Name("CN=Test DS");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 60000);
        Date notAfter = new Date(System.currentTimeMillis() + 3600000);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                serial,
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic()
        );
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }

    private static final class TestSodData {
        private final byte[] sodBytes;
        private final Map<Integer, byte[]> dgBytes;

        private TestSodData(byte[] sodBytes, Map<Integer, byte[]> dgBytes) {
            this.sodBytes = sodBytes;
            this.dgBytes = dgBytes;
        }
    }
}
