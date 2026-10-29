package com.youjcam.app

/** Один «телефон»: как выглядят фото и какие параметры у видео. */
data class Preset(
    val name: String,
    val mp: Double,          // мегапиксели фото
    val grain: Float,        // зерно 0..1
    val vignette: Float,     // виньетка 0..1
    val gainR: Float,
    val gainG: Float,
    val gainB: Float,
    val contrast: Float,
    val sat: Float,
    val bright: Float,       // сдвиг яркости, доля от 255
    val soft: Float,         // 1 = резко, меньше = мыльнее
    val jpegQuality: Int,
    val videoW: Int,
    val videoH: Int,
    val videoFps: Int,
    val videoBitrate: Int,
    val maxZoom: Float = 4f   // макс. зум модели (×), урезается возможностями камеры
)

object Presets {
    val ALL: List<Preset> = listOf(
        Preset(
            name = "S5610 Classic", mp = 5.0, grain = 0.035f, vignette = 0.25f,
            gainR = 1.04f, gainG = 1.0f, gainB = 0.94f, contrast = 0.95f, sat = 0.85f,
            bright = 0.02f, soft = 0.80f, jpegQuality = 72,
            videoW = 320, videoH = 240, videoFps = 30, videoBitrate = 700_000,
            maxZoom = 8f
        ),
        Preset(
            name = "S5610 Night", mp = 5.0, grain = 0.09f, vignette = 0.40f,
            gainR = 1.08f, gainG = 1.02f, gainB = 0.80f, contrast = 1.05f, sat = 0.80f,
            bright = -0.04f, soft = 0.75f, jpegQuality = 65,
            videoW = 320, videoH = 240, videoFps = 30, videoBitrate = 700_000,
            maxZoom = 8f
        ),
        Preset(
            name = "Старая Nokia", mp = 2.0, grain = 0.05f, vignette = 0.30f,
            gainR = 0.95f, gainG = 1.05f, gainB = 0.92f, contrast = 0.95f, sat = 0.85f,
            bright = 0.0f, soft = 0.60f, jpegQuality = 60,
            videoW = 176, videoH = 144, videoFps = 15, videoBitrate = 250_000,
            maxZoom = 2f
        ),
        Preset(
            name = "Flip 2007", mp = 2.0, grain = 0.04f, vignette = 0.20f,
            gainR = 0.94f, gainG = 0.99f, gainB = 1.06f, contrast = 0.88f, sat = 0.80f,
            bright = 0.02f, soft = 0.65f, jpegQuality = 62,
            videoW = 320, videoH = 240, videoFps = 15, videoBitrate = 300_000,
            maxZoom = 2f
        ),
        Preset(
            name = "Слайдер 2008", mp = 2.0, grain = 0.04f, vignette = 0.15f,
            gainR = 1.05f, gainG = 1.03f, gainB = 0.97f, contrast = 0.90f, sat = 0.70f,
            bright = 0.08f, soft = 0.70f, jpegQuality = 65,
            videoW = 320, videoH = 240, videoFps = 20, videoBitrate = 350_000,
            maxZoom = 3f
        ),
        Preset(
            name = "Cybershot-стиль", mp = 5.0, grain = 0.02f, vignette = 0.15f,
            gainR = 1.0f, gainG = 1.0f, gainB = 1.0f, contrast = 1.12f, sat = 1.20f,
            bright = 0.0f, soft = 0.95f, jpegQuality = 80,
            videoW = 640, videoH = 480, videoFps = 30, videoBitrate = 900_000,
            maxZoom = 5f
        ),
        Preset(
            name = "Xenon-стиль", mp = 5.0, grain = 0.03f, vignette = 0.20f,
            gainR = 0.97f, gainG = 1.0f, gainB = 1.06f, contrast = 1.20f, sat = 1.05f,
            bright = -0.02f, soft = 0.90f, jpegQuality = 78,
            videoW = 320, videoH = 240, videoFps = 30, videoBitrate = 900_000,
            maxZoom = 5f
        ),
        Preset(
            name = "Omnia-стиль", mp = 8.0, grain = 0.03f, vignette = 0.20f,
            gainR = 1.04f, gainG = 1.0f, gainB = 0.95f, contrast = 1.05f, sat = 1.10f,
            bright = 0.0f, soft = 0.90f, jpegQuality = 82,
            videoW = 640, videoH = 480, videoFps = 30, videoBitrate = 1_500_000,
            maxZoom = 4f
        ),
        Preset(
            name = "Pixon-стиль", mp = 8.0, grain = 0.05f, vignette = 0.22f,
            gainR = 1.0f, gainG = 1.0f, gainB = 0.98f, contrast = 1.10f, sat = 1.30f,
            bright = 0.0f, soft = 0.88f, jpegQuality = 80,
            videoW = 640, videoH = 480, videoFps = 30, videoBitrate = 1_500_000,
            maxZoom = 4f
        ),
        Preset(
            name = "Compact 10", mp = 10.0, grain = 0.02f, vignette = 0.12f,
            gainR = 1.02f, gainG = 1.0f, gainB = 0.97f, contrast = 1.05f, sat = 1.05f,
            bright = 0.0f, soft = 0.95f, jpegQuality = 88,
            videoW = 640, videoH = 480, videoFps = 30, videoBitrate = 1_500_000,
            maxZoom = 5f
        )
    )
}
