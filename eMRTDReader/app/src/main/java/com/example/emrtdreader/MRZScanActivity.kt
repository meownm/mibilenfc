package com.example.emrtdreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.emrtdreader.databinding.ActivityMrzScanBinding
import com.example.emrtdreader.utils.MRZParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MRZScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMrzScanBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var latestMrz: String? = null
    private val isAnalysisPaused = AtomicBoolean(false)

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMrzScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.continueButton.isEnabled = false
        binding.continueButton.setOnClickListener {
            val mrz = latestMrz ?: return@setOnClickListener
            val intent = Intent(this, NFCReadActivity::class.java)
            intent.putExtra(NFCReadActivity.EXTRA_MRZ, mrz)
            startActivity(intent)
        }

        binding.manualScanButton.setOnClickListener {
            binding.cameraPreviewView.bitmap?.let { runRecognition(it) }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { recognizer.close() }
        cameraExecutor.shutdown()
    }

    private fun startCamera() {
        binding.scanProgressBar.visibility = View.VISIBLE

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreviewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor, MrzAnalyzer { visionText, imageProxy ->
                handleRecognitionResult(visionText, imageProxy)
            })

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, analysis)
            } catch (t: Throwable) {
                Log.e("MRZScanActivity", "Camera bind failed", t)
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun runRecognition(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        isAnalysisPaused.set(true) // Pause analysis during manual scan
        binding.scanProgressBar.visibility = View.VISIBLE
        recognizer.process(image)
            .addOnSuccessListener { handleRecognitionResult(it, null, isManual = true) }
            .addOnFailureListener { Log.e("MRZScanActivity", "Manual recognition failed", it) }
            .addOnCompleteListener { 
                isAnalysisPaused.set(false) // Resume analysis
                binding.scanProgressBar.visibility = View.GONE
            }
    }

    private fun handleRecognitionResult(visionText: Text, imageProxy: ImageProxy?, isManual: Boolean = false) {
        // Draw all found text blocks
        runOnUiThread {
            binding.textOverlayView.update(visionText, imageProxy?.width ?: 0, imageProxy?.height ?: 0)
        }

        val rawText = visionText.text
        if (isManual) {
            runOnUiThread { binding.mrzTextView.text = rawText }
        }

        val mrz = MRZParser.tryExtractMrz(rawText)
        if (mrz != null) {
            Log.d("MRZScanActivity", "MRZ Found:\n$mrz")
            latestMrz = mrz
            // Pause further analysis once a valid MRZ is found
            isAnalysisPaused.set(true)
            runOnUiThread {
                binding.scanProgressBar.visibility = View.GONE
                binding.textOverlayView.clear()
                binding.mrzTextView.text = mrz // Show the clean MRZ
                binding.continueButton.isEnabled = true
            }
        } else if (!isAnalysisPaused.get()) {
            // If no MRZ, but we found something that looks like it, pause and re-analyze.
            if (rawText.contains("P<")) {
                isAnalysisPaused.set(true)
                Log.d("MRZScanActivity", "Potential MRZ found. Pausing for detailed analysis.")
                // We already have the result, just re-run the handler with manual flag
                // to show the raw text for debugging.
                handleRecognitionResult(visionText, imageProxy, isManual = true)
                // Un-pause after a short delay if no valid MRZ is confirmed
                binding.root.postDelayed({ isAnalysisPaused.set(false) }, 1000)
            }
        }
    }

    private inner class MrzAnalyzer(
        private val onResult: (Text, ImageProxy) -> Unit
    ) : ImageAnalysis.Analyzer {

        override fun analyze(imageProxy: ImageProxy) {
            if (isAnalysisPaused.get()) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }

            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    onResult(visionText, imageProxy)
                }
                .addOnFailureListener { t ->
                    Log.d("MRZScanActivity", "Text recognition failed: ${t.message}")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }
}
