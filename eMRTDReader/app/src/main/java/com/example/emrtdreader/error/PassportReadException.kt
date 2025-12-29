package com.example.emrtdreader.error

sealed class PassportReadException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NfcNotAvailable : PassportReadException("NFC is not available or disabled")
    class TagNotIsoDep : PassportReadException("NFC tag does not support IsoDep")
    class MrzInvalid : PassportReadException("MRZ is invalid")
    class PaceOrBacFailed(cause: Throwable) : PassportReadException("Failed to establish secure channel (PACE/BAC)", cause)
    class ReadFailed(cause: Throwable) : PassportReadException("Failed to read passport data", cause)
}
