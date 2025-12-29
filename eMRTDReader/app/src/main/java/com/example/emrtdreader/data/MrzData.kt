package com.example.emrtdreader.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MrzData(
    val documentNumber: String,
    val dateOfBirth: String,
    val dateOfExpiry: String
) : Parcelable
