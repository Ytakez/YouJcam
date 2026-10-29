package com.youjcam.app

/** Уровень качества фото: от худшего (0) до лучшего (4). */
data class PhotoLevel(
    val nameRes: Int,
    val mp: Double,
    val jpeg: Int,
    val soft: Float,
    val grainMul: Float
)

/** Уровень качества видео: от худшего (0) до лучшего (4). */
data class VideoLevel(
    val nameRes: Int,
    val w: Int,
    val h: Int,
    val fps: Int,
    val bitrate: Int
)

object Quality {
    const val AUTO = -1

    val PHOTO: List<PhotoLevel> = listOf(
        PhotoLevel(R.string.q_worst, 0.3, 40, 0.55f, 1.6f),
        PhotoLevel(R.string.q_low, 1.3, 55, 0.70f, 1.3f),
        PhotoLevel(R.string.q_mid, 3.0, 70, 0.85f, 1.0f),
        PhotoLevel(R.string.q_high, 5.0, 82, 0.95f, 0.8f),
        PhotoLevel(R.string.q_best, 12.0, 92, 1.00f, 0.6f)
    )

    val VIDEO: List<VideoLevel> = listOf(
        VideoLevel(R.string.q_worst, 176, 144, 15, 250_000),
        VideoLevel(R.string.q_low, 320, 240, 15, 400_000),
        VideoLevel(R.string.q_mid, 320, 240, 30, 800_000),
        VideoLevel(R.string.q_high, 640, 480, 30, 1_500_000),
        VideoLevel(R.string.q_best, 1280, 720, 30, 4_000_000)
    )

    /** Пресет модели + выбранное в настройках качество. AUTO = как у модели. */
    fun effective(base: Preset, photo: Int, video: Int): Preset {
        var p = base
        PHOTO.getOrNull(photo)?.let { lv ->
            p = p.copy(
                mp = lv.mp,
                jpegQuality = lv.jpeg,
                soft = lv.soft,
                grain = (p.grain * lv.grainMul).coerceAtMost(0.2f)
            )
        }
        VIDEO.getOrNull(video)?.let { lv ->
            p = p.copy(videoW = lv.w, videoH = lv.h, videoFps = lv.fps, videoBitrate = lv.bitrate)
        }
        return p
    }
}
