package com.example.emrtdreader.model

import java.io.Serializable

/**
 * Data class to hold passport information
 * Implements Serializable so it can be passed between activities
 */
data class PassportData(
    val firstName: String,
    val lastName: String,
    val documentNumber: String,
    val nationality: String,
    val dateOfBirth: String,
    val expiryDate: String,
    val personalNumber: String,
    val mrzData: String,
    val faceImage: ByteArray?,
    val signatureValid: Boolean
) : Serializable