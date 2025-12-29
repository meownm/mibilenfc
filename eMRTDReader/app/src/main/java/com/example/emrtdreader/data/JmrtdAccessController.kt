package com.example.emrtdreader.data

import com.example.emrtdreader.domain.AccessKey
import org.jmrtd.BACKey
import org.jmrtd.PassportService

/**
 * Минимальный контроллер доступа:
 * - Делает BAC по MRZ
 * - CAN пока используется только как признак (PACE можно добавить позже)
 */
class JmrtdAccessController(
    private val service: PassportService,
    private val cardAccessStreamProvider: () -> java.io.InputStream?
) {
    fun establish(mrz: AccessKey.Mrz, can: String?) {
        // PACE можно реализовать через EF.CardAccess (PACEInfo) + service.doPACE(...)
        // Сейчас делаем максимально совместимый BAC.
        val bacKey = BACKey(mrz.documentNumber, mrz.dateOfBirth, mrz.dateOfExpiry)
        service.doBAC(bacKey)
    }
}
