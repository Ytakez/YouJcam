package com.youjcam.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Сохраняет фото в Pictures/YouJcam и видео в Movies/YouJcam. */
class MediaSaver(private val ctx: Context) {

    data class Target(val uri: Uri?, val file: File?)

    fun newTarget(video: Boolean): Target {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = (if (video) "VID_" else "IMG_") + stamp + (if (video) ".mp4" else ".jpg")
        if (Build.VERSION.SDK_INT >= 29) {
            val dir = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/YouJcam")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val coll = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            return Target(ctx.contentResolver.insert(coll, values), null)
        }
        @Suppress("DEPRECATION")
        val base = Environment.getExternalStoragePublicDirectory(
            if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        )
        val dir = File(base, "YouJcam")
        dir.mkdirs()
        return Target(null, File(dir, name))
    }

    fun finish(t: Target) {
        if (t.uri != null) {
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues()
                v.put(MediaStore.MediaColumns.IS_PENDING, 0)
                ctx.contentResolver.update(t.uri, v, null, null)
            }
        } else {
            t.file?.let {
                MediaScannerConnection.scanFile(ctx, arrayOf(it.absolutePath), null, null)
            }
        }
    }

    fun discard(t: Target) {
        try {
            t.uri?.let { ctx.contentResolver.delete(it, null, null) }
            t.file?.delete()
        } catch (_: Exception) {
        }
    }
}
