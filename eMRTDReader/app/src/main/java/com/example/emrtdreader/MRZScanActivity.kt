package com.example.emrtdreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.emrtdreader.databinding.ActivityMrzScanBinding
import com.example.emrtdreader.utils.MRZParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MRZScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMrzScanBinding
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var imageCapture: ImageCapture? = null
    private var latestParsedMrz: MRZParser.MRZData? = null

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
        binding.captureButton.setOnClickListener { takePictureAndRecognize() }
        binding.enterManuallyButton.setOnClickListener { showManualInput(true) }

        binding.continueButton.setOnClickListener {
            val mrzData = latestParsedMrz ?: return@setOnClickListener
            goToNfcActivity(mrzData.documentNumber, mrzData.dateOfBirth, mrzData.expiryDate, mrzData.mrzString)
        }

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
            imageCapture = ImageCapture.Builder().build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (t: Throwable) {
                Log.e("MRZScanActivity", "Camera bind failed", t)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePictureAndRecognize() {
        val bitmap = binding.cameraPreviewView.bitmap ?: run {
            Toast.makeText(this, "Camera preview not available.", Toast.LENGTH_SHORT).show()
            return
        }

        val frame = binding.mrzFrame
        val frameRect = Rect(frame.left, frame.top, frame.right, frame.bottom)

        val previewWidth = binding.cameraPreviewView.width
        val previewHeight = binding.cameraPreviewView.height
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height

        val scaleX = bitmapWidth.toFloat() / previewWidth
        val scaleY = bitmapHeight.toFloat() / previewHeight

        val adjustedFrameRect = Rect(
            (frameRect.left * scaleX).toInt(),
            (frameRect.top * scaleY).toInt(),
            (frameRect.right * scaleX).toInt(),
            (frameRect.bottom * scaleY).toInt()
        )

        if (!Rect(0, 0, bitmapWidth, bitmapHeight).contains(adjustedFrameRect)) {
             Toast.makeText(this, "Please align the MRZ within the frame.", Toast.LENGTH_SHORT).show()
             return
        }

        val croppedBitmap = Bitmap.createBitmap(bitmap, adjustedFrameRect.left, adjustedFrameRect.top, adjustedFrameRect.width(), adjustedFrameRect.height())

        binding.mrzTextView.text = "Processing..."
        val image = InputImage.fromBitmap(croppedBitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text
                binding.mrzTextView.text = rawText
                val mrz = MRZParser.tryExtractMrz(rawText)
                if (mrz != null) {
                    val parsed = MRZParser.parseMRZ(mrz)
                    if (parsed != null) {
                        latestParsedMrz = parsed
                        binding.continueButton.visibility = View.VISIBLE
                    }
                } else {
                    Toast.makeText(this, "MRZ not found, please try again.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                binding.mrzTextView.text = ""
                Toast.makeText(this, "Recognition failed: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MRZScanActivity", "Recognition failed", e)
            }
    }

    private fun goToNfcActivity(docNum: String, dob: String, doe: String, mrzString: String? = null) {
        val intent = Intent(this, NFCReadActivity::class.java).apply {
            mrzString?.let { putExtra(NFCReadActivity.EXTRA_MRZ_STRING, it) }
            putExtra(NFCReadActivity.EXTRA_DOC_NUM, docNum)
            putExtra(NFCReadActivity.EXTRA_DOB, dob)
            putExtra(NFCReadActivity.EXTRA_DOE, doe)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { recognizer.close() }
    }
}