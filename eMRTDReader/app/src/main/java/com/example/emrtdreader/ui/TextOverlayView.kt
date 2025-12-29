package com.example.emrtdreader.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.text.Text

class TextOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs) {

    private val textBlockPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val linePaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val elementPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private var textBlocks: List<Text.TextBlock> = emptyList()
    private val transformationMatrix = Matrix()

    fun update(text: Text, sourceWidth: Int, sourceHeight: Int) {
        this.textBlocks = text.textBlocks

        val scaleX = width.toFloat() / sourceHeight
        val scaleY = height.toFloat() / sourceWidth
        val scale = scaleX.coerceAtLeast(scaleY)

        transformationMatrix.reset()
        transformationMatrix.preScale(scale, scale)

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (block in textBlocks) {
            drawRect(canvas, block.boundingBox, textBlockPaint)
            for (line in block.lines) {
                drawRect(canvas, line.boundingBox, linePaint)
//                for (element in line.elements) {
//                    drawRect(canvas, element.boundingBox, elementPaint)
//                }
            }
        }
    }

    private fun drawRect(canvas: Canvas, boundingBox: Rect?, paint: Paint) {
        if (boundingBox == null) return
        val rectF = RectF(boundingBox)
        transformationMatrix.mapRect(rectF)
        canvas.drawRect(rectF, paint)
    }

    fun clear() {
        textBlocks = emptyList()
        invalidate()
    }
}