package com.example.emrtdreader.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PassportData(
    val mrz: String,
    val dg1: ByteArray?,
    val dg2: ByteArray?,
    val sod: ByteArray?,
    val photo: ByteArray?
) : Parcelable