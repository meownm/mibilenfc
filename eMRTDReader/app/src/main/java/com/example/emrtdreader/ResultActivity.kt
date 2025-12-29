package com.example.emrtdreader

import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.emrtdreader.data.PassportData
import com.example.emrtdreader.databinding.ActivityResultBinding

class ResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PASSPORT_DATA = "EXTRA_PASSPORT_DATA"
    }

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val passportData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PASSPORT_DATA, PassportData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PASSPORT_DATA)
        }

        if (passportData != null) {
            displayPassportData(passportData)
        } else {
            binding.mrzDataTextView.text = "No valid passport data found."
        }
    }

    private fun displayPassportData(passport: PassportData) {
        binding.mrzDataTextView.text = passport.mrz

        passport.photo?.let {
            val bmp = BitmapFactory.decodeByteArray(it, 0, it.size)
            binding.photoImageView.setImageBitmap(bmp)
        }

        // TODO: Create a new layout or views to display other DG1 fields if needed
        // For example: 
        // val dg1 = DG1File(passport.dg1)
        // binding.nameTextView.text = dg1.mrzInfo.primaryIdentifier + " " + dg1.mrzInfo.secondaryIdentifier
    }
}