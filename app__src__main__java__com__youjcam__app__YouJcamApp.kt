package com.youjcam.app

import android.app.Application
import com.google.android.material.color.DynamicColors

class YouJcamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
