package com.example.emrtdreader.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PassportData(
    val documentNumber: String,
    val surname: String,
    val givenNames: String,
    val nationality: String,
    val dateOfBirth: String,
    val sex: String,
    val dateOfExpiry: String,
    val personalNumber: String?,
    val photo: ByteArray?
) : Parcelable
