package com.youjcam.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.hypot
import kotlin.random.Random

/** Лёгкий живой оверлей поверх превью: виньетка и зерно. Не перехватывает касания. */
class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var vignette = 0.25f
    private var grain = 0.04f
    private val vigPaint = Paint()
    private val grainPaint = Paint()

    init {
        val n = 96
        val px = IntArray(n * n)
        val rnd = Random(7)
        for (i in px.indices) {
            val v = rnd.nextInt(256)
            px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val noise = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        grainPaint.shader = BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        isClickable = false
        isFocusable = false
    }

    fun setLook(vignette: Float, grain: Float) {
        this.vignette = vignette
        this.grain = grain
        rebuild()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun rebuild() {
        if (width == 0 || height == 0) return
        val r = hypot(width.toFloat(), height.toFloat()) / 2f
        val edgeAlpha = (vignette * 255f * 1.6f).toInt().coerceIn(0, 255)
        vigPaint.shader = RadialGradient(
            width / 2f, height / 2f, r,
            intArrayOf(0x00000000, 0x00000000, edgeAlpha shl 24),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        grainPaint.alpha = (grain * 255f * 2.5f).toInt().coerceIn(0, 255)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, vigPaint)
        canvas.drawRect(0f, 0f, w, h, grainPaint)
    }
}
