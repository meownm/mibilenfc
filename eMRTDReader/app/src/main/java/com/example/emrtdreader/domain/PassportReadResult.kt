package com.example.emrtdreader.domain

import com.example.emrtdreader.crypto.AuthResult
import com.example.emrtdreader.data.PassportData

data class PassportReadResult(
    val passportData: PassportData,
    val authResult: AuthResult,
    val json: String
)
