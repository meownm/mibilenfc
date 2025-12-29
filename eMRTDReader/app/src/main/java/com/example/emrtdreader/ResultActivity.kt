package com.example.emrtdreader

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.emrtdreader.model.PassportData

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val photoImageView: ImageView = findViewById(R.id.photoImageView)
        val verificationStatusTextView: TextView = findViewById(R.id.verificationStatusTextView)

        val nameTextView: TextView = findViewById(R.id.nameTextView)
        val passportNumberTextView: TextView = findViewById(R.id.passportNumberTextView)
        val nationalityTextView: TextView = findViewById(R.id.nationalityTextView)
        val dateOfBirthTextView: TextView = findViewById(R.id.dateOfBirthTextView)
        val expiryDateTextView: TextView = findViewById(R.id.expiryDateTextView)
        val personalNumberTextView: TextView = findViewById(R.id.personalNumberTextView)
        val mrzDataTextView: TextView = findViewById(R.id.mrzDataTextView)

        val passportData = intent.getSerializableExtra("PASSPORT_DATA") as? PassportData
        val json = intent.getStringExtra("PASSPORT_JSON").orEmpty()
        val auth = intent.getStringExtra("AUTH_RESULT").orEmpty()

        verificationStatusTextView.text = if (auth.isNotBlank()) "Passive Auth: $auth" else "Passive Auth: N/A"

        if (passportData != null) {
            val fullName = listOf(passportData.surname, passportData.givenNames).filter { it.isNotBlank() }.joinToString(" ")
            nameTextView.text = fullName.ifBlank { "-" }
            passportNumberTextView.text = passportData.documentNumber.ifBlank { "-" }
            nationalityTextView.text = passportData.nationality.ifBlank { "-" }
            dateOfBirthTextView.text = passportData.dateOfBirth.ifBlank { "-" }
            expiryDateTextView.text = passportData.dateOfExpiry.ifBlank { "-" }
            personalNumberTextView.text = passportData.personalNumber.ifBlank { "-" }

            val photo = passportData.photo
            if (photo != null && photo.isNotEmpty()) {
                val bmp = BitmapFactory.decodeByteArray(photo, 0, photo.size)
                if (bmp != null) {
                    photoImageView.setImageBitmap(bmp)
                } else {
                    // Many passports use JPEG2000; BitmapFactory can't decode it.
                    photoImageView.setImageResource(R.drawable.photo_placeholder)
                }
            } else {
                photoImageView.setImageResource(R.drawable.photo_placeholder)
            }
        }

        mrzDataTextView.text = if (json.isNotBlank()) json else "-"
    }
}
