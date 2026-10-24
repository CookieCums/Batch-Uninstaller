package com.saha.batchuninstaller

import android.app.Application
import com.google.android.material.color.DynamicColors

/**
 * Keeps a brand-colored fallback theme on older releases and applies Material You
 * system-generated colors on Android 12+ when the platform supports them.
 */
class AppSieveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
