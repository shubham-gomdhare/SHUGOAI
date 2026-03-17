package com.arm.shugoai.app

import android.app.Application
import com.arm.shugoai.app.manager.ModelManager

class ShugoAiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize ModelManager at app startup to trigger model loading if a model is already selected.
        ModelManager.getInstance(this)
    }
}
