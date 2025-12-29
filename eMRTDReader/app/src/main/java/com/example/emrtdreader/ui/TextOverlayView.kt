package com.example.emrtdreader.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.text.Text

class TextOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var visionText: Text? = null
    private var transformationMatrix = Matrix()

    fun update(text: Text, matrix: Matrix) {
        this.visionText = text
        this.transformationMatrix = matrix
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val text = visionText ?: return

        for (block in text.textBlocks) {
            val blockRect = RectF(block.boundingBox)
            transformationMatrix.mapRect(blockRect)
            canvas.drawRect(blockRect, paint)
        }
    }

    fun clear() {
        visionText = null
        invalidate()
    }
}