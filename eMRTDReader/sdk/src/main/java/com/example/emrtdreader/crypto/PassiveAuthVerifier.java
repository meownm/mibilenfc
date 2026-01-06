package com.example.emrtdreader.crypto;

import android.content.Context;

import com.example.emrtdreader.models.VerificationResult;

import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.util.Store;
import org.bouncycastle.cert.X509CertificateHolder;

import org.jmrtd.lds.SODFile;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.*;
import java.util.*;

public final class PassiveAuthVerifier {

    private PassiveAuthVerifier() {}

    public static VerificationResult verify(Context ctx, byte[] sodBytes, Map<Integer, byte[]> dgBytes) {
        if (sodBytes == null || sodBytes.length == 0) {
            return new VerificationResult(false, false, false, "SOD missing");
        }

        boolean sigOk = false;
        boolean hashesOk = false;
        boolean cscaOk = false;

        StringBuilder details = new StringBuilder();

        try {
            // Parse SOD using jmrtd (keeps compatibility)
            SODFile sod = new SODFile(new ByteArrayInputStream(sodBytes));

            // 1) Verify CMS signature correctly
            CMSSignedData cms = new CMSSignedData(sodBytes);
            SignerInformationStore signers = cms.getSignerInfos();
            Store<X509CertificateHolder> certs = cms.getCertificates();

            X509Certificate dsCert = sod.getDocSigningCertificate();

            sigOk = verifySigner(cms, signers, certs, dsCert);
            details.append("SOD signature: ").append(sigOk ? "OK" : "FAIL").append("\n");

            // 2) Compare DG hashes against SOD content
            String digestAlg = sod.getDigestAlgorithm();
            MessageDigest md = MessageDigest.getInstance(digestAlg);

            Map<Integer, byte[]> expected = sod.getDataGroupHashes();
            hashesOk = true;
            for (Map.Entry<Integer, byte[]> e : expected.entrySet()) {
                int dgNum = e.getKey();
                byte[] dg = dgBytes.get(dgNum);
                if (dg == null) {
                    // if we didn't read optional DG, do not fail hard; only fail if it's one we have
                    continue;
                }
                byte[] actualHash = md.digest(dg);
                if (!Arrays.equals(actualHash, e.getValue())) {
                    hashesOk = false;
                    details.append("DG").append(dgNum).append(" hash mismatch\n");
                    break;
                }
            }
            if (hashesOk) details.append("DG hashes: OK\n");

            // 3) Validate DS certificate to CSCA trust anchors (best-effort)
            cscaOk = validateDsToCsca(ctx, dsCert);
            details.append("CSCA trust: ").append(cscaOk ? "OK" : "FAIL").append("\n");

        } catch (Throwable e) {
            details.append("PA error: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
        }

        return new VerificationResult(sigOk, hashesOk, cscaOk, details.toString());
    }

    private static boolean verifySigner(CMSSignedData cms,
                                       SignerInformationStore signers,
                                       Store<X509CertificateHolder> certs,
                                       X509Certificate dsCert) {
        try {
            Collection<SignerInformation> signerInfos = signers.getSigners();
            if (signerInfos == null || signerInfos.isEmpty()) return false;

            SignerInformation signer = signerInfos.iterator().next();
            return signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(dsCert));
        } catch (Throwable e) {
            // Fallback: try to find cert from CMS store
            try {
                Collection<SignerInformation> signerInfos = signers.getSigners();
                if (signerInfos == null || signerInfos.isEmpty()) return false;
                SignerInformation signer = signerInfos.iterator().next();

                Collection<X509CertificateHolder> matches = certs.getMatches(signer.getSID());
                if (matches == null || matches.isEmpty()) return false;

                X509CertificateHolder holder = matches.iterator().next();
                X509Certificate cert = new JcaX509CertificateConverter().getCertificate(holder);
                return signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert));
            } catch (Throwable ignore) {
                return false;
            }
        }
    }

    private static boolean validateDsToCsca(Context ctx, X509Certificate dsCert) {
        try {
            List<X509Certificate> csca = CscaStore.loadFromAssets(ctx);
            if (csca.isEmpty()) return false;

            Set<TrustAnchor> anchors = new HashSet<>();
            for (X509Certificate ca : csca) {
                anchors.add(new TrustAnchor(ca, null));
            }

            PKIXParameters params = new PKIXParameters(anchors);
            params.setRevocationEnabled(false);

            // cert path: only DS cert; issuer should be one of anchors
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            CertPath path = cf.generateCertPath(Collections.singletonList(dsCert));
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            validator.validate(path, params);

            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
