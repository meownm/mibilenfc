package com.example.emrtdreader.sdk.crypto;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jmrtd.lds.SODFile;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

final class TestSodFixtures {

    private TestSodFixtures() {}

    static TestSodData buildSodWithHashes() throws Exception {
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
        ensureProvider();

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

    private static void ensureProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    static final class TestSodData {
        final byte[] sodBytes;
        final Map<Integer, byte[]> dgBytes;

        TestSodData(byte[] sodBytes, Map<Integer, byte[]> dgBytes) {
            this.sodBytes = sodBytes;
            this.dgBytes = dgBytes;
        }
    }
}
