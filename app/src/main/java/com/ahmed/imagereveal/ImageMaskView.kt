package com.ahmed.imagereveal

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ImageMaskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { ADD, REVEAL }

    var mode: Mode = Mode.REVEAL

    // مستوى التعتيم الافتراضي للمربعات الجديدة (0f = شفاف تماماً، 1f = أسود كامل)
    var defaultOpacity: Float = 1f

    private var bitmap: Bitmap? = null
    private val drawMatrix = Matrix()
    private val inverseMatrix = Matrix()

    private val regions = mutableListOf<RectF>()
    private val alphaMap = HashMap<Int, Float>()
    private val baseOpacityMap = HashMap<Int, Float>()

    private var startPoint: FloatArray? = null
    private var currentRect: RectF? = null

    private val blackPaint = Paint().apply { color = Color.BLACK; alpha = 255 }
    private val drawingPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setImage(bmp: Bitmap) {
        bitmap = bmp
        regions.clear()
        alphaMap.clear()
        baseOpacityMap.clear()
        updateMatrix()
        invalidate()
    }

    fun clearRegions() {
        regions.clear()
        alphaMap.clear()
        baseOpacityMap.clear()
        invalidate()
    }

    fun resetReveal() {
        for (i in regions.indices) {
            alphaMap[i] = baseOpacityMap[i] ?: 1f
        }
        invalidate()
    }

    fun exportCurrentState(): Bitmap? {
        val bmp = bitmap ?: return null
        val result = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)

        for (i in regions.indices) {
            val alpha = alphaMap[i] ?: 1f
            if (alpha <= 0.01f) continue
            val r = regions[i]
            val rect = RectF(
                r.left * bmp.width, r.top * bmp.height,
                r.right * bmp.width, r.bottom * bmp.height
            )
            blackPaint.alpha = (alpha * 255).toInt()
            canvas.drawRect(rect, blackPaint)
        }
        return result
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMatrix()
    }

    private fun updateMatrix() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        val scale = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val dx = (width - bmp.width * scale) / 2f
        val dy = (height - bmp.height * scale) / 2f
        drawMatrix.reset()
        drawMatrix.postScale(scale, scale)
        drawMatrix.postTranslate(dx, dy)
        drawMatrix.invert(inverseMatrix)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        canvas.drawBitmap(bmp, drawMatrix, bitmapPaint)

        for (i in regions.indices) {
            val alpha = alphaMap[i] ?: 1f
            if (alpha <= 0.01f) continue
            val r = toViewRect(regions[i], bmp)
            blackPaint.alpha = (alpha * 255).toInt()
            canvas.drawRect(r, blackPaint)
        }

        currentRect?.let { canvas.drawRect(it, drawingPaint) }
    }

    private fun toViewRect(norm: RectF, bmp: Bitmap): RectF {
        val r = RectF(
            norm.left * bmp.width, norm.top * bmp.height,
            norm.right * bmp.width, norm.bottom * bmp.height
        )
        drawMatrix.mapRect(r)
        return r
    }

    private fun toBitmapPoint(x: Float, y: Float): FloatArray {
        val pts = floatArrayOf(x, y)
        inverseMatrix.mapPoints(pts)
        return pts
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bmp = bitmap ?: return false

        when (mode) {
            Mode.ADD -> {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startPoint = floatArrayOf(event.x, event.y)
                        currentRect = RectF(event.x, event.y, event.x, event.y)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        startPoint?.let {
                            currentRect = RectF(
                                minOf(it[0], event.x), minOf(it[1], event.y),
                                maxOf(it[0], event.x), maxOf(it[1], event.y)
                            )
                            invalidate()
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        currentRect?.let { viewRect ->
                            if (viewRect.width() > 10 && viewRect.height() > 10) {
                                val p1 = toBitmapPoint(viewRect.left, viewRect.top)
                                val p2 = toBitmapPoint(viewRect.right, viewRect.bottom)
                                val norm = RectF(
                                    (p1[0] / bmp.width).coerceIn(0f, 1f),
                                    (p1[1] / bmp.height).coerceIn(0f, 1f),
                                    (p2[0] / bmp.width).coerceIn(0f, 1f),
                                    (p2[1] / bmp.height).coerceIn(0f, 1f)
                                )
                                regions.add(norm)
                                val idx = regions.size - 1
                                baseOpacityMap[idx] = defaultOpacity
                                alphaMap[idx] = defaultOpacity
                            }
                        }
                        currentRect = null
                        invalidate()
                    }
                }
            }
            Mode.REVEAL -> {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val p = toBitmapPoint(event.x, event.y)
                    val nx = p[0] / bmp.width
                    val ny = p[1] / bmp.height
                    for (i in regions.indices.reversed()) {
                        val r = regions[i]
                        if (nx in r.left..r.right && ny in r.top..r.bottom && (alphaMap[i] ?: 1f) > 0.05f) {
                            animateReveal(i)
                            break
                        }
                    }
                }
            }
        }
        return true
    }

    private fun animateReveal(index: Int) {
        val startAlpha = alphaMap[index] ?: 1f
        val animator = ValueAnimator.ofFloat(startAlpha, 0f)
        animator.duration = 300
        animator.addUpdateListener {
            alphaMap[index] = it.animatedValue as Float
            invalidate()
        }
        animator.start()
    }
}
