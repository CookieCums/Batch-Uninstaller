package com.saha.batchuninstaller.debloat

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.IOException

class UadDatabaseWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        UadDatabaseRepository(applicationContext).refresh()
        Result.success()
    } catch (e: IOException) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    } catch (_: IllegalArgumentException) {
        Result.failure()
    } catch (_: Exception) {
        Result.retry()
    }
}
