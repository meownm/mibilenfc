package com.example.emrtdreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    
    private lateinit var startScanButton: Button
    private lateinit var titleTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var securityNoteTextView: TextView
    
    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupClickListeners()
        
        // Check for camera permission
        if (!hasCameraPermission()) {
            requestCameraPermission()
        }
    }
    
    private fun initViews() {
        startScanButton = findViewById(R.id.startScanButton)
        titleTextView = findViewById(R.id.titleTextView)
        descriptionTextView = findViewById(R.id.descriptionTextView)
        securityNoteTextView = findViewById(R.id.securityNoteTextView)
    }
    
    private fun setupClickListeners() {
        startScanButton.setOnClickListener {
            if (hasCameraPermission()) {
                startMRZScanActivity()
            } else {
                requestCameraPermission()
            }
        }
    }
    
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startMRZScanActivity() {
        val intent = Intent(this, MRZScanActivity::class.java)
        startActivity(intent)
    }
}