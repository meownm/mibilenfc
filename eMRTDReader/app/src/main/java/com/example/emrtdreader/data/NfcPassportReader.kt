package com.example.emrtdreader.data

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.example.emrtdreader.crypto.AuthResult
import com.example.emrtdreader.crypto.CryptoHelper
import com.example.emrtdreader.domain.AccessKey
import com.example.emrtdreader.domain.PassportReadResult
import com.example.emrtdreader.error.PassportReadException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.IsoDepCardService
import org.jmrtd.BACKey
import org.jmrtd.PassportService
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.SODFile
import java.io.InputStream

class NfcPassportReader {

    suspend fun read(tag: Tag, accessKey: AccessKey.Mrz): PassportReadResult = withContext(Dispatchers.IO) {
        val isoDep = IsoDep.get(tag) ?: throw PassportReadException.TagNotIsoDep()
        val cardService = IsoDepCardService(isoDep)

        try {
            cardService.open()
            val passportService = PassportService(cardService, PassportService.NORMAL_MAX_TRANCEIVE_LENGTH, PassportService.DEFAULT_MAX_BLOCKSIZE, false, false)
            passportService.open()

            // Perform BAC
            val bacKey = BACKey(accessKey.documentNumber, accessKey.dateOfBirthYYMMDD, accessKey.dateOfExpiryYYMMDD)
            passportService.sendSelectApplet(false)
            passportService.doBAC(bacKey)

            // Read required data groups
            val dg1Stream = passportService.getInputStream(PassportService.EF_DG1)
            val dg1 = DG1File(dg1Stream.readBytes())

            val dg2Stream = try { passportService.getInputStream(PassportService.EF_DG2) } catch (e: Exception) { null }
            val sodStream = try { passportService.getInputStream(PassportService.EF_SOD) } catch (e: Exception) { null }

            val sodBytes = sodStream?.readBytes()
            val dg1Bytes = dg1.encoded
            val dg2Bytes = dg2Stream?.readBytes()

            // --- Passive Authentication ---
            val authResult = if (sodBytes != null) {
                val sodFile = SODFile(sodBytes)
                CryptoHelper.verifyPassiveAuth(sodFile, mapOf(PassportService.EF_DG1 to dg1Bytes, PassportService.EF_DG2 to dg2Bytes))
            } else {
                AuthResult.SOD_NOT_FOUND
            }

            val mrzInfo = dg1.mrzInfo
            val photo = dg2Bytes?.let { DG2File(it).faceInfos.firstOrNull()?.faceImageInfos?.firstOrNull()?.imageInputStream?.readBytes() }

            val passportData = com.example.emrtdreader.domain.PassportData(
                documentNumber = mrzInfo.documentNumber,
                surname = mrzInfo.primaryIdentifier,
                givenNames = mrzInfo.secondaryIdentifier,
                nationality = mrzInfo.nationality,
                dateOfBirth = mrzInfo.dateOfBirth,
                sex = mrzInfo.gender.toString(),
                dateOfExpiry = mrzInfo.dateOfExpiry,
                personalNumber = mrzInfo.personalNumber,
                photo = photo
            )

            PassportReadResult(passportData, authResult, "") // JSON string can be generated here if needed

        } catch (e: Exception) {
            // More granular error handling can be added here
            throw PassportReadException.ReadFailed(e)
        } finally {
            cardService.close()
        }
    }
}