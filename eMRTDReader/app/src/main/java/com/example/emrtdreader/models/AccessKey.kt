package com.example.emrtdreader.models

sealed class AccessKey {
    data class Mrz(
        val documentNumber: String,
        val dateOfBirthYYMMDD: String,
        val dateOfExpiryYYMMDD: String
    ) : AccessKey()
}