package com.saha.batchuninstaller.debloat

import android.content.Context
import android.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object UadDatabaseScheduler {
    private const val WORK_NAME = "uad-database-refresh"
    private const val AUTO_UPDATE = "database_auto_update"
    private const val INTERVAL_DAYS = "database_update_days"

    fun schedule(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(AUTO_UPDATE, true)) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        val days = prefs.getInt(INTERVAL_DAYS, 1).coerceIn(1, 7)
        val request = PeriodicWorkRequestBuilder<UadDatabaseWorker>(days.toLong(), TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, request
        )
    }

    fun setInterval(context: Context, days: Int?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (days == null) {
            prefs.edit().putBoolean(AUTO_UPDATE, false).apply()
        } else {
            require(days == 1 || days == 3 || days == 7) { "Interval must be 1, 3, or 7 days" }
            prefs.edit().putBoolean(AUTO_UPDATE, true).putInt(INTERVAL_DAYS, days).apply()
        }
        schedule(context)
    }
}
