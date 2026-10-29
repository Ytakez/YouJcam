package com.youjcam.app

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        bindSection(
            sw = findViewById(R.id.swPhotoAuto),
            slider = findViewById(R.id.slPhoto),
            desc = findViewById(R.id.tvPhotoDesc),
            initial = AppSettings.photoLevel(this),
            save = { AppSettings.setPhotoLevel(this, it) },
            describe = { photoText(it) }
        )
        bindSection(
            sw = findViewById(R.id.swVideoAuto),
            slider = findViewById(R.id.slVideo),
            desc = findViewById(R.id.tvVideoDesc),
            initial = AppSettings.videoLevel(this),
            save = { AppSettings.setVideoLevel(this, it) },
            describe = { videoText(it) }
        )
        fillModels()
    }

    private fun bindSection(
        sw: MaterialSwitch,
        slider: Slider,
        desc: TextView,
        initial: Int,
        save: (Int) -> Unit,
        describe: (Int) -> String
    ) {
        val auto = initial < 0
        slider.value = (if (auto) 2 else initial).toFloat()
        sw.isChecked = auto

        fun refresh() {
            val a = sw.isChecked
            slider.isEnabled = !a
            slider.alpha = if (a) 0.4f else 1f
            desc.text = if (a) getString(R.string.q_auto_desc) else describe(slider.value.toInt())
        }
        refresh()

        sw.setOnCheckedChangeListener { _, checked ->
            save(if (checked) Quality.AUTO else slider.value.toInt())
            refresh()
        }
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !sw.isChecked) {
                save(value.toInt())
                refresh()
            }
        }
    }

    private fun mpText(mp: Double) =
        if (mp % 1.0 == 0.0) mp.toInt().toString() else mp.toString()

    private fun photoText(i: Int): String {
        val l = Quality.PHOTO[i]
        return "${getString(l.nameRes)} — ${mpText(l.mp)} Мп · JPEG ${l.jpeg}"
    }

    private fun videoText(i: Int): String {
        val l = Quality.VIDEO[i]
        return "${getString(l.nameRes)} — ${l.w}×${l.h} · ${l.fps} к/с · ${l.bitrate / 1000} кбит/с"
    }

    private fun fillModels() {
        val box = findViewById<LinearLayout>(R.id.modelsList)
        val pad = (6 * resources.displayMetrics.density).toInt()
        for (p in Presets.ALL) {
            val tv = TextView(this)
            tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            tv.setPadding(0, pad, 0, pad)
            val zoom = if (p.maxZoom % 1f == 0f) p.maxZoom.toInt().toString() else p.maxZoom.toString()
            tv.text = "${p.name}\n${mpText(p.mp)} Мп · ${p.videoW}×${p.videoH} · зум до ×$zoom"
            box.addView(tv)
        }
    }
}
