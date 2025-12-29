package com.example.emrtdreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.emrtdreader.analyzer.MrzImageAnalyzer
import com.example.emrtdreader.databinding.ActivityMrzScanBinding
import com.example.emrtdreader.models.MrzResult
import java.util.concurrent.Executors

class MRZScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMrzScanBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var latestMrzResult: MrzResult? = null

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMrzScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupClickListeners() {
        binding.continueButton.setOnClickListener {
            val result = latestMrzResult ?: return@setOnClickListener
            goToNfcActivity(result)
        }

        binding.enterManuallyButton.setOnClickListener { showManualInput(true) }

        binding.confirmManualInputButton.setOnClickListener {
            val docNum = binding.docNumberEditText.text.toString().trim()
            val dob = binding.dobEditText.text.toString().trim()
            val doe = binding.doeEditText.text.toString().trim()

            if (docNum.isBlank() || dob.length != 6 || doe.length != 6) {
                Toast.makeText(this, "Invalid data format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            goToNfcActivity(docNum, dob, doe)
        }
    }

    private fun showManualInput(show: Boolean) {
        binding.scanViewGroup.visibility = if (show) View.GONE else View.VISIBLE
        binding.manualInputGroup.visibility = if (show) View.VISIBLE else View.GONE
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
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setTargetResolution(Size(1280, 720))
                .build()

            analysis.setAnalyzer(
                cameraExecutor,
                MrzImageAnalyzer {
                    runOnUiThread {
                        handleFinalResult(it)
                    }
                }
            )

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (t: Throwable) {
                Log.e("MRZScanActivity", "Camera bind failed", t)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleFinalResult(finalResult: MrzResult) {
        latestMrzResult = finalResult
        val displayText = finalResult.line1 + "\n" + finalResult.line2 + (finalResult.line3?.let { "\n" + it } ?: "")
        Log.d("MRZScanActivity", "AGGREGATED MRZ Found:\n$displayText")
        
        binding.mrzTextView.text = displayText
        binding.continueButton.visibility = View.VISIBLE
    }
    
    private fun goToNfcActivity(mrzResult: MrzResult) {
        val intent = Intent(this, NFCReadActivity::class.java).apply {
            putExtra(NFCReadActivity.EXTRA_MRZ_RESULT, mrzResult)
        }
        startActivity(intent)
    }

    private fun goToNfcActivity(docNum: String, dob: String, doe: String) {
        val intent = Intent(this, NFCReadActivity::class.java).apply {
            putExtra(NFCReadActivity.EXTRA_DOC_NUM, docNum)
            putExtra(NFCReadActivity.EXTRA_DOB, dob)
            putExtra(NFCReadActivity.EXTRA_DOE, doe)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}