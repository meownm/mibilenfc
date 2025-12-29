package com.example.emrtdreader.model

import java.io.Serializable

data class PassportData(
    val documentNumber: String,
    val surname: String,
    val givenNames: String,
    val nationality: String,
    val dateOfBirth: String,
    val sex: String,
    val dateOfExpiry: String,
    val personalNumber: String,
    val personalNumber2: String,
    val issuingState: String,
    val photo: ByteArray?
) : Serializable
