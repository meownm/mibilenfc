package com.example.emrtdreader

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.emrtdreader.utils.MRZParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MRZScanActivity : AppCompatActivity() {
    
    private lateinit var previewView: PreviewView
    private lateinit var continueButton: Button
    private lateinit var instructionTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var cameraExecutor: ExecutorService
    
    private var mrzText: String = ""
    private var isMrzDetected: Boolean = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mrz_scan)
        
        initViews()
        setupClickListeners()
        startCamera()
        
        cameraExecutor = Executors.newSingleThreadExecutor()
    }
    
    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        continueButton = findViewById(R.id.continueButton)
        instructionTextView = findViewById(R.id.instructionTextView)
        statusTextView = findViewById(R.id.statusTextView)
    }
    
    private fun setupClickListeners() {
        continueButton.setOnClickListener {
            if (isMrzDetected) {
                startNFCReadActivity()
            } else {
                Toast.makeText(this, "Please scan MRZ first", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, MRZAnalyzer())
                }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e("MRZScanActivity", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    inner class MRZAnalyzer : ImageAnalysis.Analyzer {
        private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        private var lastAnalysisTime = 0L
        
        override fun analyze(imageProxy: ImageProxy) {
            // Throttle analysis to avoid excessive processing
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastAnalysisTime < 1000) { // Analyze every 1 second
                imageProxy.close()
                return
            }
            lastAnalysisTime = currentTime
            
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val resultText = visionText.text
                        
                        // Check if the text contains MRZ format (typically has 2 lines starting with P or ID)
                        if (isMRZFormat(resultText)) {
                            runOnUiThread {
                                mrzText = resultText
                                isMrzDetected = true
                                statusTextView.text = getString(R.string.mrz_success)
                                statusTextView.setTextColor(ContextCompat.getColor(this@MRZScanActivity, R.color.green))
                                statusTextView.visibility = android.view.View.VISIBLE
                                continueButton.isEnabled = true
                                
                                // Stop the analyzer after successful detection
                                imageProxy.close()
                                return@runOnUiThread
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("MRZScanActivity", "Text recognition failed", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
        
        private fun isMRZFormat(text: String): Boolean {
            // Use the MRZParser utility to validate MRZ format
            return MRZParser.parseMRZ(text) != null
        }
    }
    
    private fun startNFCReadActivity() {
        val intent = Intent(this, NFCReadActivity::class.java)
        intent.putExtra("MRZ_DATA", mrzText)
        startActivity(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}