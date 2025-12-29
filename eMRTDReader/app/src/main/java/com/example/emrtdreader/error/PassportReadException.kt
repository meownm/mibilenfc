package com.example.emrtdreader.error

sealed class PassportReadException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class TagNotIsoDep : PassportReadException("Tag is not IsoDep compliant")
    class ReadFailed(cause: Throwable) : PassportReadException("Failed to read passport data", cause)
}
