package com.example.emrtdreader.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MrzResult(
    val line1: String,
    val line2: String,
    val line3: String?,
    val format: MrzFormat
) : Parcelable

@Parcelize
enum class MrzFormat : Parcelable {
    TD1,
    TD3
}
