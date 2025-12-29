package com.example.emrtdreader.crypto

import org.jmrtd.lds.SODFile
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.X509Certificate

object CryptoHelper {
    fun verifyPassiveAuth(sodFile: SODFile, dataHashes: Map<Int, ByteArray>): AuthResult {
        return try {
            val digestAlgorithm = sodFile.digestAlgorithm
            val signatureAlgorithm = sodFile.digestEncryptionAlgorithm
            val signature = sodFile.encryptedDigest
            val docSigningCertificate = sodFile.docSigningCertificate

            val verifier = Signature.getInstance(signatureAlgorithm)
            verifier.initVerify(docSigningCertificate.publicKey)

            val dataToVerify = dataHashes.entries.sortedBy { it.key }.fold(byteArrayOf()) { acc, entry -> acc + entry.value }
            verifier.update(dataToVerify)

            if (verifier.verify(signature)) {
                AuthResult.SUCCESS
            } else {
                AuthResult.FAILURE
            }
        } catch (e: Exception) {
            AuthResult.FAILURE
        }
    }
}