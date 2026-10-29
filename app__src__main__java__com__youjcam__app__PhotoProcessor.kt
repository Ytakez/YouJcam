package com.youjcam.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Превращает JPEG с камеры в «кадр старого телефона» и пишет JPEG в поток. */
object PhotoProcessor {

    fun process(jpeg: ByteArray, p: Preset, out: OutputStream) {
        val targetPx = (p.mp * 1_000_000.0).toLong()

        // 1) читаем размеры и декодируем сразу уменьшенно, чтобы не съесть память
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while ((bounds.outWidth.toLong() / (sample * 2)) *
            (bounds.outHeight.toLong() / (sample * 2)) >= targetPx
        ) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
            ?: throw IllegalStateException("decode failed")

        // 2) поворот по EXIF
        val deg = exifDegrees(jpeg)
        if (deg != 0) {
            val m = Matrix()
            m.postRotate(deg.toFloat())
            val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (r !== bmp) bmp.recycle()
            bmp = r
        }

        // 3) приводим к мегапикселям пресета (в большую сторону не растягиваем)
        val px = bmp.width.toLong() * bmp.height
        if (px > targetPx * 1.05) {
            val s = sqrt(targetPx.toDouble() / px.toDouble())
            val r = Bitmap.createScaledBitmap(
                bmp,
                max(1, (bmp.width * s).roundToInt()),
                max(1, (bmp.height * s).roundToInt()),
                true
            )
            if (r !== bmp) bmp.recycle()
            bmp = r
        }

        // 4) мыльность дешёвого объектива: вниз и обратно вверх
        if (p.soft < 0.98f) {
            val w = bmp.width
            val h = bmp.height
            val small = Bitmap.createScaledBitmap(
                bmp,
                max(1, (w * p.soft).toInt()),
                max(1, (h * p.soft).toInt()),
                true
            )
            val big = Bitmap.createScaledBitmap(small, w, h, true)
            if (small !== big) small.recycle()
            if (big !== bmp) bmp.recycle()
            bmp = big
        }

        if (!bmp.isMutable) {
            val c = bmp.copy(Bitmap.Config.ARGB_8888, true)
            bmp.recycle()
            bmp = c
        }

        // 5) цвет, виньетка, зерно
        applyLook(bmp, p)

        // 6) слабый JPEG
        bmp.compress(Bitmap.CompressFormat.JPEG, p.jpegQuality, out)
        out.flush()
        bmp.recycle()
    }

    private fun exifDegrees(jpeg: ByteArray): Int = try {
        val ex = ExifInterface(ByteArrayInputStream(jpeg))
        when (ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) {
        0
    }

    private fun clamp255(v: Float): Int = if (v < 0f) 0 else if (v > 255f) 255 else v.toInt()

    private fun applyLook(bmp: Bitmap, p: Preset) {
        val w = bmp.width
        val h = bmp.height
        val row = IntArray(w)
        var seed = 0x2F6E2B1 xor System.nanoTime().toInt()
        if (seed == 0) seed = 1
        val cx = max(1f, (w - 1) / 2f)
        val cy = max(1f, (h - 1) / 2f)
        val lumaNoise = p.grain * 255f * 1.2f
        val chromaNoise = lumaNoise * 0.35f
        val bright = p.bright * 255f

        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            val dy = (y - cy) / cy
            for (x in 0 until w) {
                val c = row[x]
                var r = ((c shr 16) and 255).toFloat()
                var g = ((c shr 8) and 255).toFloat()
                var b = (c and 255).toFloat()

                // насыщенность
                val l = 0.299f * r + 0.587f * g + 0.114f * b
                r = l + (r - l) * p.sat
                g = l + (g - l) * p.sat
                b = l + (b - l) * p.sat

                // контраст, яркость, оттенок
                r = ((r - 128f) * p.contrast + 128f + bright) * p.gainR
                g = ((g - 128f) * p.contrast + 128f + bright) * p.gainG
                b = ((b - 128f) * p.contrast + 128f + bright) * p.gainB

                // виньетка
                val dx = (x - cx) / cx
                val d2 = (dx * dx + dy * dy) * 0.5f
                val v = 1f - p.vignette * (0.35f * d2 + 0.65f * d2 * d2)
                r *= v
                g *= v
                b *= v

                // зерно
                seed = seed xor (seed shl 13)
                seed = seed xor (seed ushr 17)
                seed = seed xor (seed shl 5)
                val n1 = ((seed and 0xFF) - 127.5f) / 127.5f
                val n2 = (((seed shr 8) and 0xFF) - 127.5f) / 127.5f
                val n3 = (((seed shr 16) and 0xFF) - 127.5f) / 127.5f
                val n = n1 * lumaNoise
                r += n + n2 * chromaNoise
                g += n
                b += n + n3 * chromaNoise

                row[x] = (0xFF shl 24) or (clamp255(r) shl 16) or (clamp255(g) shl 8) or clamp255(b)
            }
            bmp.setPixels(row, 0, w, 0, y, w, 1)
        }
    }
}
