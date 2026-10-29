@file:Suppress("DEPRECATION")

package com.youjcam.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var overlay: OverlayView
    private lateinit var tvPreset: TextView
    private lateinit var tvInfo: TextView
    private lateinit var tvRec: TextView
    private lateinit var btnFlash: MaterialButton
    private lateinit var btnSwitch: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var frame: View
    private lateinit var zoomRow: View
    private lateinit var tvZoom: TextView
    private lateinit var zoomSlider: Slider
    private lateinit var shutter: View
    private lateinit var chipGroup: ChipGroup
    private lateinit var modeToggle: MaterialButtonToggleGroup

    private val chipIds = ArrayList<Int>()
    private val saver by lazy { MediaSaver(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var camera: Camera? = null
    private var cameraId = 0
    private var sensorOrientation = 90
    private var surfaceReady = false
    private var hasPerms = false
    private var audioOk = false

    private var preset: Preset = Presets.ALL[0]   // выбранная модель
    private var eff: Preset = Presets.ALL[0]      // модель + качество из настроек

    private var zoomRatios: List<Int> = emptyList()
    private var zoomMaxIdx = 0
    private var zoomIdx = 0
    private var pinchRatio = 1f
    private var isVideo = false
    private var flashOn = false
    private var busy = false

    private var recorder: MediaRecorder? = null
    private var recTarget: MediaSaver.Target? = null
    private var recPfd: ParcelFileDescriptor? = null
    private var recStart = 0L

    private val tick = object : Runnable {
        override fun run() {
            val s = (SystemClock.elapsedRealtime() - recStart) / 1000
            tvRec.text = String.format(Locale.US, "● %02d:%02d", s / 60, s % 60)
            handler.postDelayed(this, 500)
        }
    }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPerms()
            if (hasPerms) openCamera() else toast(getString(R.string.need_perms))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surface)
        overlay = findViewById(R.id.overlay)
        tvPreset = findViewById(R.id.tvPreset)
        tvInfo = findViewById(R.id.tvInfo)
        tvRec = findViewById(R.id.tvRec)
        btnFlash = findViewById(R.id.btnFlash)
        btnSwitch = findViewById(R.id.btnSwitch)
        shutter = findViewById(R.id.shutter)
        chipGroup = findViewById(R.id.chipGroup)
        modeToggle = findViewById(R.id.modeToggle)
        btnSettings = findViewById(R.id.btnSettings)
        frame = findViewById(R.id.frame)
        zoomRow = findViewById(R.id.zoomRow)
        tvZoom = findViewById(R.id.tvZoom)
        zoomSlider = findViewById(R.id.zoomSlider)

        surfaceView.holder.addCallback(this)

        // лента пресетов
        for (p in Presets.ALL) {
            val chip = layoutInflater.inflate(R.layout.item_preset_chip, chipGroup, false) as Chip
            chip.id = View.generateViewId()
            chip.text = p.name
            chipIds.add(chip.id)
            chipGroup.addView(chip)
        }
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val i = chipIds.indexOf(id)
            if (i >= 0) selectPreset(i)
        }
        chipGroup.check(chipIds[0])

        modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) setMode(checkedId == R.id.btnVideo)
        }
        modeToggle.check(R.id.btnPhoto)

        btnFlash.setOnClickListener {
            flashOn = !flashOn
            btnFlash.setIconResource(if (flashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
        }
        btnSwitch.setOnClickListener {
            if (recorder != null || busy) return@setOnClickListener
            val n = Camera.getNumberOfCameras()
            if (n > 1) {
                cameraId = (cameraId + 1) % n
                openCamera()
            }
        }
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        zoomSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) setZoom(value.toInt())
        }
        val scaler = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                pinchRatio = zoomRatios.getOrNull(zoomIdx)?.let { it / 100f } ?: 1f
                return zoomMaxIdx > 0
            }

            override fun onScale(d: ScaleGestureDetector): Boolean {
                if (zoomMaxIdx <= 0) return false
                val top = zoomRatios[zoomMaxIdx] / 100f
                pinchRatio = (pinchRatio * d.scaleFactor).coerceIn(1f, top)
                var best = 0
                var bestDiff = Float.MAX_VALUE
                for (i in 0..zoomMaxIdx) {
                    val diff = abs(zoomRatios[i] / 100f - pinchRatio)
                    if (diff < bestDiff) { bestDiff = diff; best = i }
                }
                setZoom(best)
                return true
            }
        })
        frame.setOnTouchListener { _, ev ->
            scaler.onTouchEvent(ev)
            zoomMaxIdx > 0
        }
        shutter.setOnClickListener {
            if (isVideo) toggleRecording() else capture()
        }

        selectPreset(0)
        refreshPerms()
        if (!hasPerms) permLauncher.launch(neededPermissions())
    }

    // ---------- разрешения ----------

    private fun neededPermissions(): Array<String> {
        val list = arrayListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT <= 28) list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return list.toTypedArray()
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun refreshPerms() {
        audioOk = granted(Manifest.permission.RECORD_AUDIO)
        hasPerms = granted(Manifest.permission.CAMERA) &&
                (Build.VERSION.SDK_INT > 28 || granted(Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    // ---------- жизненный цикл / поверхность ----------

    override fun onResume() {
        super.onResume()
        refreshPerms()
        refreshEffective()
        if (surfaceReady && hasPerms && camera == null) openCamera()
    }

    override fun onPause() {
        if (recorder != null) stopRecording()
        closeCamera()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        worker.shutdown()
        super.onDestroy()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        if (hasPerms) openCamera()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        closeCamera()
    }

    // ---------- камера ----------

    private fun closestTo(sizes: List<Camera.Size>, targetPixels: Int): Camera.Size {
        val s43 = sizes.filter { it.width * 3 == it.height * 4 }
        val pool = if (s43.isNotEmpty()) s43 else sizes
        return pool.minByOrNull { abs(it.width.toLong() * it.height - targetPixels) }!!
    }

    private fun openCamera() {
        if (!surfaceReady || !hasPerms) return
        closeCamera()
        val cam = try {
            Camera.open(cameraId)
        } catch (e: Exception) {
            toast(getString(R.string.error_camera))
            return
        }
        camera = cam
        try {
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(cameraId, info)
            sensorOrientation = info.orientation
            val front = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
            val display = if (front) (360 - (info.orientation % 360)) % 360 else info.orientation
            cam.setDisplayOrientation(display)

            val params = cam.parameters
            val prev = closestTo(params.supportedPreviewSizes, 640 * 480)
            params.setPreviewSize(prev.width, prev.height)
            val pic = closestTo(params.supportedPictureSizes, (eff.mp * 1_000_000).toInt())
            params.setPictureSize(pic.width, pic.height)
            params.jpegQuality = 95
            params.setRotation(info.orientation)
            val focus = params.supportedFocusModes
            if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            }
            cam.parameters = params
            cam.setPreviewDisplay(surfaceView.holder)
            cam.startPreview()
            zoomIdx = 0
            setupZoom()
        } catch (e: Exception) {
            toast(getString(R.string.error_camera))
            closeCamera()
        }
    }

    private fun closeCamera() {
        if (recorder != null) stopRecording()
        camera?.let {
            try { it.stopPreview() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        camera = null
    }

    private fun applyPictureSize() {
        val cam = camera ?: return
        try {
            val params = cam.parameters
            val s = closestTo(params.supportedPictureSizes, (eff.mp * 1_000_000).toInt())
            params.setPictureSize(s.width, s.height)
            cam.parameters = params
        } catch (_: Exception) {
        }
    }

    private fun applyFlash(video: Boolean) {
        val cam = camera ?: return
        try {
            val params = cam.parameters
            val modes = params.supportedFlashModes ?: return
            val want = when {
                !flashOn -> Camera.Parameters.FLASH_MODE_OFF
                video -> Camera.Parameters.FLASH_MODE_TORCH
                else -> Camera.Parameters.FLASH_MODE_ON
            }
            if (modes.contains(want)) {
                params.flashMode = want
                cam.parameters = params
            }
        } catch (_: Exception) {
        }
    }

    // ---------- пресеты и режимы ----------

    private fun selectPreset(i: Int) {
        preset = Presets.ALL[i]
        refreshEffective()
    }

    /** Пересчитывает «модель + качество из настроек» и применяет к камере. */
    private fun refreshEffective() {
        eff = Quality.effective(preset, AppSettings.photoLevel(this), AppSettings.videoLevel(this))
        overlay.setLook(eff.vignette, eff.grain)
        updateInfo()
        applyPictureSize()
        setupZoom()
    }

    private fun levelName(photo: Boolean): String {
        val l = if (photo) AppSettings.photoLevel(this) else AppSettings.videoLevel(this)
        val res = if (photo) Quality.PHOTO.getOrNull(l)?.nameRes else Quality.VIDEO.getOrNull(l)?.nameRes
        return getString(res ?: R.string.q_auto_short)
    }

    // ---------- зум ----------

    /** Диапазон зума = минимум из зума модели и возможностей камеры телефона. */
    private fun setupZoom() {
        val cam = camera ?: return
        try {
            val params = cam.parameters
            val ratios = params.zoomRatios
            if (!params.isZoomSupported || ratios == null || ratios.size < 2) {
                noZoom()
                return
            }
            val limit = (preset.maxZoom * 100f).toInt()
            var maxIdx = 0
            for (i in ratios.indices) if (ratios[i] <= limit) maxIdx = i
            maxIdx = minOf(maxIdx, params.maxZoom)
            if (maxIdx < 1) {
                params.zoom = 0
                cam.parameters = params
                noZoom()
                return
            }
            zoomRatios = ratios.toList()
            zoomMaxIdx = maxIdx
            zoomIdx = zoomIdx.coerceIn(0, maxIdx)
            params.zoom = zoomIdx
            cam.parameters = params

            zoomSlider.value = 0f
            zoomSlider.valueFrom = 0f
            zoomSlider.valueTo = maxIdx.toFloat()
            zoomSlider.stepSize = 1f
            zoomSlider.value = zoomIdx.toFloat()
            zoomRow.visibility = View.VISIBLE
            updateZoomUi()
        } catch (_: Exception) {
        }
    }

    private fun noZoom() {
        zoomRatios = emptyList()
        zoomMaxIdx = 0
        zoomIdx = 0
        zoomRow.visibility = View.GONE
    }

    private fun setZoom(idx: Int) {
        val cam = camera ?: return
        if (zoomMaxIdx <= 0) return
        val i = idx.coerceIn(0, zoomMaxIdx)
        try {
            val params = cam.parameters
            params.zoom = i
            cam.parameters = params
            zoomIdx = i
        } catch (_: Exception) {
        }
        updateZoomUi()
    }

    private fun updateZoomUi() {
        val r = zoomRatios.getOrNull(zoomIdx) ?: 100
        tvZoom.text = String.format(Locale.US, "×%.1f", r / 100f)
        if (zoomSlider.value.toInt() != zoomIdx) zoomSlider.value = zoomIdx.toFloat()
    }

    private fun setMode(video: Boolean) {
        isVideo = video
        shutter.setBackgroundResource(R.drawable.bg_shutter)
        updateInfo()
    }

    private fun updateInfo() {
        tvPreset.text = preset.name
        tvInfo.text = if (isVideo) {
            "${eff.videoW}×${eff.videoH} • ${eff.videoFps} к/с • ${levelName(false)}"
        } else {
            val mp = if (eff.mp % 1.0 == 0.0) eff.mp.toInt().toString() else eff.mp.toString()
            "$mp Мп • ${levelName(true)}"
        }
    }

    private fun setBusy(b: Boolean) {
        shutter.alpha = if (b) 0.5f else 1f
        shutter.isEnabled = !b
    }

    private fun setChipsEnabled(enabled: Boolean) {
        for (i in 0 until chipGroup.childCount) chipGroup.getChildAt(i).isEnabled = enabled
        for (i in 0 until modeToggle.childCount) modeToggle.getChildAt(i).isEnabled = enabled
    }

    // ---------- фото ----------

    private fun capture() {
        val cam = camera ?: return
        if (busy) return
        busy = true
        setBusy(true)
        val p = eff
        try {
            applyFlash(false)
            cam.takePicture(null, null, Camera.PictureCallback { data, c ->
                try { c.startPreview() } catch (_: Exception) {}
                worker.execute {
                    var ok = false
                    val t = saver.newTarget(false)
                    try {
                        val out: OutputStream =
                            if (t.uri != null) contentResolver.openOutputStream(t.uri)!!
                            else FileOutputStream(t.file!!)
                        out.use { PhotoProcessor.process(data, p, it) }
                        saver.finish(t)
                        ok = true
                    } catch (e: Throwable) {
                        saver.discard(t)
                    }
                    runOnUiThread {
                        busy = false
                        setBusy(false)
                        toast(getString(if (ok) R.string.saved_photo else R.string.error_photo))
                    }
                }
            })
        } catch (e: Exception) {
            busy = false
            setBusy(false)
            toast(getString(R.string.error_photo))
        }
    }

    // ---------- видео ----------

    private fun toggleRecording() {
        if (recorder != null) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        if (camera == null || busy) return
        applyFlash(true)
        // сначала как у настоящего телефона: MPEG-4 + AMR; если не выйдет, то H.264 + AAC
        var ok = tryRecord(eff, MediaRecorder.VideoEncoder.MPEG_4_SP, MediaRecorder.AudioEncoder.AMR_NB)
        // если MPEG-4 нет в телефоне: H.264, но звук всё равно AMR (тот самый «телефонный»)
        if (!ok) ok = tryRecord(eff, MediaRecorder.VideoEncoder.H264, MediaRecorder.AudioEncoder.AMR_NB)
        if (!ok) ok = tryRecord(eff, MediaRecorder.VideoEncoder.H264, MediaRecorder.AudioEncoder.AAC)
        if (ok) {
            shutter.setBackgroundResource(R.drawable.bg_shutter_rec)
            tvRec.visibility = View.VISIBLE
            recStart = SystemClock.elapsedRealtime()
            handler.post(tick)
            setChipsEnabled(false)
            btnSwitch.isEnabled = false
        } else {
            applyFlash(false)
            toast(getString(R.string.error_video))
        }
    }

    private fun tryRecord(p: Preset, vEnc: Int, aEnc: Int): Boolean {
        val cam = camera ?: return false
        val target = saver.newTarget(true)
        var pfd: ParcelFileDescriptor? = null
        val mr = MediaRecorder()
        try {
            val params = cam.parameters
            val sizes = params.supportedVideoSizes ?: params.supportedPreviewSizes
            val size = sizes.firstOrNull { it.width == p.videoW && it.height == p.videoH }
                ?: closestTo(sizes, p.videoW * p.videoH)

            cam.unlock()
            mr.setCamera(cam)
            if (audioOk) mr.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            mr.setVideoSource(MediaRecorder.VideoSource.CAMERA)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setVideoEncoder(vEnc)
            if (audioOk) {
                mr.setAudioEncoder(aEnc)
                if (aEnc == MediaRecorder.AudioEncoder.AMR_NB) {
                    mr.setAudioSamplingRate(8000)
                    mr.setAudioChannels(1)
                    mr.setAudioEncodingBitRate(12200)
                } else {
                    mr.setAudioSamplingRate(16000)
                    mr.setAudioChannels(1)
                    mr.setAudioEncodingBitRate(32000)
                }
            }
            mr.setVideoSize(size.width, size.height)
            mr.setVideoFrameRate(p.videoFps)
            mr.setVideoEncodingBitRate(p.videoBitrate)
            mr.setOrientationHint(sensorOrientation)
            if (target.uri != null) {
                pfd = contentResolver.openFileDescriptor(target.uri, "w")
                mr.setOutputFile(pfd!!.fileDescriptor)
            } else {
                mr.setOutputFile(target.file!!.absolutePath)
            }
            mr.setPreviewDisplay(surfaceView.holder.surface)
            mr.prepare()
            mr.start()
            recorder = mr
            recTarget = target
            recPfd = pfd
            return true
        } catch (e: Exception) {
            try { mr.reset() } catch (_: Exception) {}
            try { mr.release() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            saver.discard(target)
            try { cam.lock() } catch (_: Exception) {}
            return false
        }
    }

    private fun stopRecording() {
        val mr = recorder ?: return
        var good = true
        try {
            mr.stop()
        } catch (e: RuntimeException) {
            good = false
        }
        try { mr.reset() } catch (_: Exception) {}
        try { mr.release() } catch (_: Exception) {}
        recorder = null
        try { recPfd?.close() } catch (_: Exception) {}
        recPfd = null
        try { camera?.lock() } catch (_: Exception) {}
        try { camera?.startPreview() } catch (_: Exception) {}

        val t = recTarget
        recTarget = null
        if (t != null) {
            if (good) saver.finish(t) else saver.discard(t)
        }

        handler.removeCallbacks(tick)
        tvRec.visibility = View.GONE
        shutter.setBackgroundResource(R.drawable.bg_shutter)
        setChipsEnabled(true)
        btnSwitch.isEnabled = true
        applyFlash(false)
        toast(getString(if (good) R.string.saved_video else R.string.error_video))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
