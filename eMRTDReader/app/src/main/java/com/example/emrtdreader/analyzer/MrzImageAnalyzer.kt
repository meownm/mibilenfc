package com.example.emrtdreader.analyzer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.emrtdreader.utils.MrzBurstAggregator
import com.example.emrtdreader.utils.MrzNormalizer
import com.example.emrtdreader.utils.MrzResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class MrzImageAnalyzer(
    private val onFinalMrz: (MrzResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val aggregator = MrzBurstAggregator()
    private var finished = false

    private var lastAnalysisTime = 0L
    private val analysisIntervalMs = 300L

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(image: ImageProxy) {
        if (finished) {
            image.close()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < analysisIntervalMs) {
            image.close()
            return
        }
        lastAnalysisTime = now

        scope.launch {
            try {
                processImage(image)
            } finally {
                image.close()
            }
        }
    }

    private suspend fun processImage(image: ImageProxy) {
        val bitmap = imageProxyToBitmap(image)
        val mrzBitmap = cropMrzRegion(bitmap)

        val ocrText = runOcr(mrzBitmap)
        val normalizedResult = MrzNormalizer.normalize(ocrText)
        
        val aggregated = aggregator.aggregate(normalizedResult)
        if (aggregated != null && aggregated.confidence >= 3) {
            finished = true
            onFinalMrz(aggregated.result)
            aggregator.reset()
        }
    }

    private suspend fun runOcr(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
                .addOnFailureListener { if (cont.isActive) cont.resume("") }
        }

    private fun cropMrzRegion(bitmap: Bitmap): Bitmap {
        val h = bitmap.height
        val w = bitmap.width
        return Bitmap.createBitmap(bitmap, 0, (h * 0.65).toInt(), w, (h * 0.35).toInt())
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
        val jpegBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }
}