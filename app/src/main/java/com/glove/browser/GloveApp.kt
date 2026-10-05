package com.glove.browser

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class GloveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        val forceDark = getSharedPreferences("glove", MODE_PRIVATE).getBoolean("forceDark", false)
        AppCompatDelegate.setDefaultNightMode(
            if (forceDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
    }

    companion object {
        @JvmStatic
        lateinit var instance: GloveApp
            private set
    }
}
