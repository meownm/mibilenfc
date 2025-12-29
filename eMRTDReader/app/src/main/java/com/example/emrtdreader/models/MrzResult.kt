package com.example.emrtdreader.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class MrzFormat : Parcelable {
    TD1,
    TD3
}

@Parcelize
data class MrzResult(
    val line1: String,
    val line2: String,
    val line3: String?,
    val format: MrzFormat
) : Parcelable
