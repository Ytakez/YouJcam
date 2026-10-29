package com.youjcam.app

import android.content.Context

/** Настройки приложения (SharedPreferences). */
object AppSettings {
    private const val FILE = "youjcam_prefs"
    private const val K_PHOTO = "photo_level"
    private const val K_VIDEO = "video_level"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun clean(v: Int) = if (v in 0..4) v else Quality.AUTO

    fun photoLevel(c: Context): Int = clean(sp(c).getInt(K_PHOTO, Quality.AUTO))
    fun videoLevel(c: Context): Int = clean(sp(c).getInt(K_VIDEO, Quality.AUTO))

    fun setPhotoLevel(c: Context, v: Int) = sp(c).edit().putInt(K_PHOTO, v).apply()
    fun setVideoLevel(c: Context, v: Int) = sp(c).edit().putInt(K_VIDEO, v).apply()
}
