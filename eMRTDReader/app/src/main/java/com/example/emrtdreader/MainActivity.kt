package com.example.emrtdreader

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep layout for compatibility, but auto-forward to MRZ scan.
        setContentView(R.layout.activity_main)
        startActivity(Intent(this, MRZScanActivity::class.java))
        finish()
    }
}
