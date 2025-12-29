package com.example.emrtdreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

class MRZScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMrzScanBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var latestMrz: String? = null

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
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreviewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor, MrzAnalyzer { mrz ->
                latestMrz = mrz
                runOnUiThread {
                    binding.mrzTextView.text = mrz
                    binding.continueButton.isEnabled = true
                }
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

    private inner class MrzAnalyzer(
        private val onMrzFound: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }

            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val raw = visionText.text ?: ""
                    val mrz = MRZParser.tryExtractMrz(raw)
                    if (mrz != null) onMrzFound(mrz)
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
