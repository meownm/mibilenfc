package com.example.emrtdreader.crypto

import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.jmrtd.lds.SODFile
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Date

object CryptoHelper {

    /**
     * Passive Authentication without CSCA trust store:
     * - verifies SOD signature using Document Signer certificate from SOD
     * - verifies DG hashes against SOD (if DG bytes provided)
     *
     * Returned AuthResult:
     * - INVALID_SIGNATURE: signature invalid OR DG hash mismatch
     * - EXPIRED_CERT: DS cert expired/not yet valid
     * - UNKNOWN_CA: signature valid but chain trust is not validated (no CSCA store)
     */
    fun verifyPassiveAuth(sodFile: SODFile, dg1: ByteArray?, dg2: ByteArray?): AuthResult {
        val dsCert = extractDsCertificate(sodFile) ?: return AuthResult.INVALID_SIGNATURE

        // Date validity
        val now = Date()
        try {
            dsCert.checkValidity(now)
        } catch (_: Throwable) {
            return AuthResult.EXPIRED_CERT
        }

        // Verify CMS signature
        val signatureOk = verifyCmsSignature(sodFile, dsCert)
        if (!signatureOk) return AuthResult.INVALID_SIGNATURE

        // Verify hashes (if provided)
        val digestAlg = sodFile.digestAlgorithm
        val hashes = sodFile.dataGroupHashes
        if (dg1 != null) {
            val expected = hashes[1] ?: return AuthResult.INVALID_SIGNATURE
            val actual = digest(digestAlg, dg1)
            if (!expected.contentEquals(actual)) return AuthResult.INVALID_SIGNATURE
        }
        if (dg2 != null) {
            val expected = hashes[2] ?: return AuthResult.INVALID_SIGNATURE
            val actual = digest(digestAlg, dg2)
            if (!expected.contentEquals(actual)) return AuthResult.INVALID_SIGNATURE
        }

        // Without CSCA store we cannot validate chain -> UNKNOWN_CA
        return AuthResult.UNKNOWN_CA
    }

    private fun digest(alg: String, data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance(alg)
        return md.digest(data)
    }

    private fun extractDsCertificate(sodFile: SODFile): X509Certificate? {
        return try {
            val certBytes = sodFile.docSigningCertificate?.encoded
            if (certBytes != null) return sodFile.docSigningCertificate

            // Fallback: parse from CMS if needed
            val cms = CMSSignedData(sodFile.encoded)
            val certStore = cms.certificates
            val holders = certStore.getMatches(null).filterIsInstance<X509CertificateHolder>()
            val holder = holders.firstOrNull() ?: return null
            JcaX509CertificateConverter().setProvider("BC").getCertificate(holder)
        } catch (_: Throwable) {
            null
        }
    }

    private fun verifyCmsSignature(sodFile: SODFile, dsCert: X509Certificate): Boolean {
        return try {
            val cms = CMSSignedData(sodFile.encoded)
            val signers = cms.signerInfos.signers
            val signer = signers.firstOrNull() ?: return false
            val verifier = JcaSimpleSignerInfoVerifierBuilder()
                .setProvider("BC")
                .build(dsCert)
            signer.verify(verifier)
        } catch (_: Throwable) {
            false
        }
    }
}
