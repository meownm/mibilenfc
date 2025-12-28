package com.example.emrtdreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ResultActivity : AppCompatActivity() {
    
    private lateinit var photoImageView: ImageView
    private lateinit var verificationStatusTextView: TextView
    private lateinit var nameTextView: TextView
    private lateinit var passportNumberTextView: TextView
    private lateinit var nationalityTextView: TextView
    private lateinit var dateOfBirthTextView: TextView
    private lateinit var expiryDateTextView: TextView
    private lateinit var personalNumberTextView: TextView
    private lateinit var mrzDataTextView: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        
        // Prevent screenshots for security
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        
        initViews()
        displayPassportData()
    }
    
    private fun initViews() {
        photoImageView = findViewById(R.id.photoImageView)
        verificationStatusTextView = findViewById(R.id.verificationStatusTextView)
        nameTextView = findViewById(R.id.nameTextView)
        passportNumberTextView = findViewById(R.id.passportNumberTextView)
        nationalityTextView = findViewById(R.id.nationalityTextView)
        dateOfBirthTextView = findViewById(R.id.dateOfBirthTextView)
        expiryDateTextView = findViewById(R.id.expiryDateTextView)
        personalNumberTextView = findViewById(R.id.personalNumberTextView)
        mrzDataTextView = findViewById(R.id.mrzDataTextView)
    }
    
    private fun displayPassportData() {
        val passportData = intent.getSerializableExtra("PASSPORT_DATA") as? PassportData
        if (passportData != null) {
            // Display verification status
            verificationStatusTextView.text = if (passportData.signatureValid) {
                getString(R.string.signature_valid)
            } else {
                getString(R.string.signature_invalid)
            }
            verificationStatusTextView.setTextColor(
                if (passportData.signatureValid) 
                    resources.getColor(R.color.green) 
                else 
                    resources.getColor(R.color.red)
            )
            
            // Display personal data
            val fullName = "${passportData.firstName} ${passportData.lastName}".trim()
            nameTextView.text = fullName
            passportNumberTextView.text = passportData.documentNumber
            nationalityTextView.text = passportData.nationality
            dateOfBirthTextView.text = passportData.dateOfBirth
            expiryDateTextView.text = passportData.expiryDate
            personalNumberTextView.text = passportData.personalNumber
            
            // Display MRZ data
            mrzDataTextView.text = passportData.mrzData
            
            // Display photo if available
            if (passportData.faceImage != null) {
                try {
                    val bitmap = BitmapFactory.decodeByteArray(passportData.faceImage, 0, passportData.faceImage.size)
                    photoImageView.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    // If we can't decode the image, keep the placeholder
                }
            }
        }
    }
}

// Make PassportData serializable so it can be passed between activities
import java.io.Serializable

// Update the PassportData class to implement Serializable
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