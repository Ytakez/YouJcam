package com.youjcam.app

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** Рамка 3:4 (как экран 240x320): вписывается в доступное место по центру. */
class AspectFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availW = MeasureSpec.getSize(widthMeasureSpec)
        val availH = MeasureSpec.getSize(heightMeasureSpec)
        var w = availW
        var h = w * 4 / 3
        if (h > availH) {
            h = availH
            w = h * 3 / 4
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        )
    }
}
