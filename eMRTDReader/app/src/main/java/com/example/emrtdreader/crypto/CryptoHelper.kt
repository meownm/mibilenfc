package com.example.emrtdreader.crypto

import org.jmrtd.VerificationStatus
import org.jmrtd.lds.icao.SODFile
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.PassportService
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Cryptographic helper class for eMRTD verification
 * Implements passive authentication according to ICAO 9303 standards
 */
object CryptoHelper {
    
    /**
     * Performs passive authentication on eMRTD data
     * Verifies the SOD signature and data group hashes
     * 
     * @param sodBytes SOD file bytes from the passport
     * @param dg1Bytes DG1 file bytes from the passport
     * @param dg2Bytes DG2 file bytes from the passport
     * @return VerificationStatus indicating the result of the verification
     */
    fun performPassiveAuthentication(
        sodBytes: ByteArray,
        dg1Bytes: ByteArray,
        dg2Bytes: ByteArray
    ): VerificationStatus {
        return try {
            // Parse the SOD file
            val sodFile = SODFile(ByteArrayInputStream(sodBytes))
            
            // Get the document signing certificate from SOD
            val docSigningCertBytes = sodFile.docSigningCertificate
            if (docSigningCertBytes == null) {
                return VerificationStatus.INVALID
            }
            
            // Parse the document signing certificate
            val cf = CertificateFactory.getInstance("X.509")
            val docSigningCert = cf.generateCertificate(ByteArrayInputStream(docSigningCertBytes)) as X509Certificate
            
            // Verify the SOD signature (this is the core of passive authentication)
            val sodVerified = sodFile.verify(docSigningCert.publicKey)
            
            if (!sodVerified) {
                return VerificationStatus.INVALID
            }
            
            // Verify hash values in SOD match the data groups
            // Check DG1 hash
            val expectedDG1Hash = sodFile.getDataGroupHash(PassportService.EF_DG1)
            val actualDG1Hash = sodFile.calculateHash(PassportService.EF_DG1, dg1Bytes)
            
            if (!expectedDG1Hash.contentEquals(actualDG1Hash)) {
                return VerificationStatus.INVALID
            }
            
            // Check DG2 hash
            val expectedDG2Hash = sodFile.getDataGroupHash(PassportService.EF_DG2)
            val actualDG2Hash = sodFile.calculateHash(PassportService.EF_DG2, dg2Bytes)
            
            if (!expectedDG2Hash.contentEquals(actualDG2Hash)) {
                return VerificationStatus.INVALID
            }
            
            // If we reach here, all checks passed
            VerificationStatus.VALID
            
        } catch (e: Exception) {
            e.printStackTrace()
            VerificationStatus.INVALID
        }
    }
    
    /**
     * Performs passive authentication on parsed data files
     */
    fun performPassiveAuthentication(
        sodFile: SODFile,
        dg1File: DG1File,
        dg2File: DG2File
    ): VerificationStatus {
        return try {
            // Get the document signing certificate from SOD
            val docSigningCertBytes = sodFile.docSigningCertificate
            if (docSigningCertBytes == null) {
                return VerificationStatus.INVALID
            }
            
            // Parse the document signing certificate
            val cf = CertificateFactory.getInstance("X.509")
            val docSigningCert = cf.generateCertificate(ByteArrayInputStream(docSigningCertBytes)) as X509Certificate
            
            // Verify the SOD signature
            val sodVerified = sodFile.verify(docSigningCert.publicKey)
            
            if (!sodVerified) {
                return VerificationStatus.INVALID
            }
            
            // Verify hash values in SOD match the data groups
            val dg1Bytes = dg1File.encoded
            val dg2Bytes = dg2File.encoded
            
            // Check DG1 hash
            val expectedDG1Hash = sodFile.getDataGroupHash(PassportService.EF_DG1)
            val actualDG1Hash = sodFile.calculateHash(PassportService.EF_DG1, dg1Bytes)
            
            if (!expectedDG1Hash.contentEquals(actualDG1Hash)) {
                return VerificationStatus.INVALID
            }
            
            // Check DG2 hash
            val expectedDG2Hash = sodFile.getDataGroupHash(PassportService.EF_DG2)
            val actualDG2Hash = sodFile.calculateHash(PassportService.EF_DG2, dg2Bytes)
            
            if (!expectedDG2Hash.contentEquals(actualDG2Hash)) {
                return VerificationStatus.INVALID
            }
            
            // If we reach here, all checks passed
            VerificationStatus.VALID
            
        } catch (e: Exception) {
            e.printStackTrace()
            VerificationStatus.INVALID
        }
    }
}