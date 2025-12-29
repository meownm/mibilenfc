package com.example.emrtdreader.domain

sealed class AccessKey {
    data class Mrz(
        val documentNumber: String,
        val dateOfBirthYYMMDD: String,
        val dateOfExpiryYYMMDD: String
    ) : AccessKey()

    data class Can(val can: String) : AccessKey()
}
