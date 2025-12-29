package com.example.emrtdreader.data

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.example.emrtdreader.crypto.AuthResult
import com.example.emrtdreader.crypto.CryptoHelper
import com.example.emrtdreader.crypto.CryptoProvider
import com.example.emrtdreader.domain.AccessKey
import com.example.emrtdreader.domain.PassportReadResult
import com.example.emrtdreader.error.PassportReadException
import com.example.emrtdreader.model.PassportData
import net.sf.scuba.smartcards.IsoDepCardService
import org.jmrtd.PassportService
import org.jmrtd.lds.DG1File
import org.jmrtd.lds.DG2File
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.iso19794.FaceInfo
import java.io.ByteArrayInputStream

class NfcPassportReader {

    fun read(tag: Tag, mrz: AccessKey.Mrz, can: String?): PassportReadResult {
        CryptoProvider.ensureBouncyCastle()

        val isoDep = IsoDep.get(tag) ?: throw PassportReadException.TagNotIsoDep()
        isoDep.timeout = 10_000

        try {
            isoDep.connect()

            val cardService = IsoDepCardService(isoDep)
            cardService.open()

            val service = PassportService(
                cardService,
                PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                PassportService.DEFAULT_MAX_BLOCKSIZE,
                false,
                false
            )
            service.open()
            service.sendSelectApplet(false)

            // Secure messaging: (BAC now; PACE can be added later)
            runCatching {
                val controller = JmrtdAccessController(service) { service.getInputStream(PassportService.EF_CARD_ACCESS) }
                controller.establish(mrz, can)
            }.getOrElse { throw PassportReadException.PaceOrBacFailed(it) }

            // DG1 (mandatory)
            val dg1Bytes = service.getInputStream(PassportService.EF_DG1).readAllBytesCompat()
            val dg1File = DG1File(ByteArrayInputStream(dg1Bytes))
            val mrzInfo = dg1File.mrzInfo

            // DG2 (optional)
            val dg2Bytes: ByteArray? = runCatching {
                service.getInputStream(PassportService.EF_DG2).readAllBytesCompat()
            }.getOrNull()

            // SOD (optional)
            val sodBytes: ByteArray? = runCatching {
                service.getInputStream(PassportService.EF_SOD).readAllBytesCompat()
            }.getOrNull()

            val photoBytes = dg2Bytes?.let { extractFirstFaceImage(it) }

            val authResult = if (sodBytes != null) {
                runCatching {
                    val sodFile = SODFile(ByteArrayInputStream(sodBytes))
                    CryptoHelper.verifyPassiveAuth(sodFile = sodFile, dg1 = dg1Bytes, dg2 = dg2Bytes)
                }.getOrElse { AuthResult.UNKNOWN_CA }
            } else {
                AuthResult.UNKNOWN_CA
            }

            val passportData = PassportData(
                documentNumber = mrzInfo.documentNumber ?: "",
                surname = mrzInfo.primaryIdentifier ?: "",
                givenNames = mrzInfo.secondaryIdentifier ?: "",
                nationality = mrzInfo.nationality ?: "",
                dateOfBirth = mrzInfo.dateOfBirth ?: "",
                sex = mrzInfo.sex?.toString().orEmpty(),
                dateOfExpiry = mrzInfo.dateOfExpiry ?: "",
                personalNumber = mrzInfo.personalNumber ?: "",
                personalNumber2 = "",
                issuingState = mrzInfo.issuingState ?: "",
                photo = photoBytes
            )

            val json = buildJson(
                data = passportData,
                auth = authResult,
                usedCan = !can.isNullOrBlank()
            )

            return PassportReadResult(
                passportData = passportData,
                authResult = authResult,
                json = json
            )
        } catch (e: PassportReadException) {
            throw e
        } catch (t: Throwable) {
            throw PassportReadException.ReadFailed(t)
        } finally {
            runCatching { isoDep.close() }
        }
    }

    private fun extractFirstFaceImage(dg2Bytes: ByteArray): ByteArray? {
        return runCatching {
            val dg2 = DG2File(ByteArrayInputStream(dg2Bytes))
            val faceInfos = dg2.faceInfos ?: return null
            if (faceInfos.isEmpty()) return null

            val faceInfo = faceInfos[0] as? FaceInfo ?: return null
            val imgInfos = faceInfo.faceImageInfos ?: return null
            if (imgInfos.isEmpty()) return null

            imgInfos[0].imageInputStream.use { it.readBytes() }
        }.getOrNull()
    }

    private fun buildJson(data: PassportData, auth: AuthResult, usedCan: Boolean): String {
        fun esc(s: String): String =
            s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ")

        val photoLen = data.photo?.size ?: 0

        return """{
  "documentNumber": "${esc(data.documentNumber)}",
  "surname": "${esc(data.surname)}",
  "givenNames": "${esc(data.givenNames)}",
  "nationality": "${esc(data.nationality)}",
  "dateOfBirth": "${esc(data.dateOfBirth)}",
  "sex": "${esc(data.sex)}",
  "dateOfExpiry": "${esc(data.dateOfExpiry)}",
  "issuingState": "${esc(data.issuingState)}",
  "personalNumber": "${esc(data.personalNumber)}",
  "photoBytes": $photoLen,
  "authResult": "${auth.name}",
  "paceCanProvided": $usedCan
}"""
    }

    private fun java.io.InputStream.readAllBytesCompat(): ByteArray {
        return this.use { it.readBytes() }
    }
}
