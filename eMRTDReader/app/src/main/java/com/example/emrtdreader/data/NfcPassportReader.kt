package com.example.emrtdreader.data

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.example.emrtdreader.crypto.AuthResult
import com.example.emrtdreader.crypto.CryptoHelper
import com.example.emrtdreader.domain.PassportReadResult
import com.example.emrtdreader.error.PassportReadException
import com.example.emrtdreader.models.AccessKey
import com.example.emrtdreader.models.MrzResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.IsoDepCardService
import org.jmrtd.BACKey
import org.jmrtd.PassportService
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.SODFile
import java.io.ByteArrayInputStream

class NfcPassportReader {

    suspend fun read(tag: Tag, mrzResult: MrzResult): PassportReadResult = withContext(Dispatchers.IO) {
        val isoDep = IsoDep.get(tag) ?: throw PassportReadException.TagNotIsoDep()
        val cardService = IsoDepCardService(isoDep)

        try {
            cardService.open()
            val passportService = PassportService(cardService, PassportService.NORMAL_MAX_TRANCEIVE_LENGTH, PassportService.DEFAULT_MAX_BLOCKSIZE, false, false)
            passportService.open()

            // Extract info from the (already validated) MRZ Result
            val docNum = mrzResult.line2.substring(0, 9)
            val dob = mrzResult.line2.substring(13, 19)
            val doe = mrzResult.line2.substring(21, 27)

            val bacKey = BACKey(docNum, dob, doe)
            passportService.sendSelectApplet(false)
            passportService.doBAC(bacKey)

            val dg1Stream = passportService.getInputStream(PassportService.EF_DG1)
            val dg1Bytes = dg1Stream.readBytes()
            val dg1 = DG1File(ByteArrayInputStream(dg1Bytes))

            val dg2Stream = try { passportService.getInputStream(PassportService.EF_DG2) } catch (e: Exception) { null }
            val sodStream = try { passportService.getInputStream(PassportService.EF_SOD) } catch (e: Exception) { null }

            val sodBytes = sodStream?.readBytes()
            val dg2Bytes = dg2Stream?.readBytes()

            val authResult = if (sodBytes != null) {
                val sodFile = SODFile(ByteArrayInputStream(sodBytes))
                val dataHashes = mutableMapOf<Int, ByteArray>()
                dataHashes[1] = dg1Bytes
                dg2Bytes?.let { dataHashes[2] = it }
                
                CryptoHelper.verifyPassiveAuth(sodFile, dataHashes)
            } else {
                AuthResult.SOD_NOT_FOUND
            }

            val photo = dg2Bytes?.let { DG2File(ByteArrayInputStream(it)).faceInfos.firstOrNull()?.faceImageInfos?.firstOrNull()?.imageInputStream?.readBytes() }

            val passportData = PassportData(
                mrz = mrzResult.line1 + "\n" + mrzResult.line2 + (mrzResult.line3?.let { "\n" + it } ?: ""),
                dg1 = dg1Bytes,
                dg2 = dg2Bytes,
                sod = sodBytes,
                photo = photo
            )

            PassportReadResult(passportData, authResult, "")

        } catch (e: Exception) {
            throw PassportReadException.ReadFailed(e)
        } finally {
            cardService.close()
        }
    }
}