package com.example.emrtdreader.crypto

enum class AuthResult {
    VALID,
    INVALID_SIGNATURE,
    UNKNOWN_CA,
    EXPIRED_CERT
}
