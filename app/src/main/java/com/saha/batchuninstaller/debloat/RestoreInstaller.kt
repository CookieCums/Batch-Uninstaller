package com.saha.batchuninstaller.debloat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Looper
import android.app.PendingIntent
import com.saha.batchuninstaller.RoomStorage.RestorePoint
import com.saha.batchuninstaller.utils.RootExecutor
import com.saha.batchuninstaller.utils.SuRootExecutor
import java.io.FileInputStream
import java.io.IOException

sealed class RestoreInstallResult {
    data class Success(val usedExistingSystemPackage: Boolean) : RestoreInstallResult()
    data class SessionStarted(val sessionId: Int) : RestoreInstallResult()
    data class Failure(val reason: String) : RestoreInstallResult()
}

/**
 * Restores only from a manifest whose APK hashes, package ID and signing digest have been verified.
 * Root and normal PackageInstaller installs share the same verification boundary.
 */
class RestoreInstaller(
    context: Context,
    private val repository: RestoreRepository,
    private val rootExecutor: RootExecutor = SuRootExecutor
) {
    private val appContext = context.applicationContext

    fun restoreWithRoot(point: RestorePoint): RestoreInstallResult {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return RestoreInstallResult.Failure("Restore must run in the background.")
        }
        return try {
            val manifest = repository.verifiedManifest(point)
            if (point.isSystemApp) {
                val first = RestoreRootCommandBuilder.installExisting(point.packageName, useCmd = true)
                    ?: return RestoreInstallResult.Failure("Invalid package name in restore point.")
                if (rootExecutor.execute(first).success) {
                    return RestoreInstallResult.Success(usedExistingSystemPackage = true)
                }
                val fallback = RestoreRootCommandBuilder.installExisting(point.packageName, useCmd = false)
                    ?: return RestoreInstallResult.Failure("Invalid package name in restore point.")
                if (rootExecutor.execute(fallback).success) {
                    return RestoreInstallResult.Success(usedExistingSystemPackage = true)
                }
            }

            val command = RestoreRootCommandBuilder.installMultiple(File(point.backupPath), manifest.files)
                ?: return RestoreInstallResult.Failure("The verified APK files could not be assembled into an install command.")
            val result = rootExecutor.execute(command)
            if (result.success) {
                RestoreInstallResult.Success(usedExistingSystemPackage = false)
            } else {
                val detail = result.output.trim().take(220).ifBlank { "Package manager exited with code ${result.exitCode}." }
                RestoreInstallResult.Failure(detail)
            }
        } catch (e: Exception) {
            RestoreInstallResult.Failure(e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)
        }
    }

    /** Starts a normal PackageInstaller session. The session result is delivered to the supplied PendingIntent. */
    fun installWithoutRoot(
        point: RestorePoint,
        callback: PendingIntent
    ): RestoreInstallResult {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return RestoreInstallResult.Failure("Restore preparation must run in the background.")
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return RestoreInstallResult.Failure("Split APK restore requires Android 5.0 or newer.")
        }

        var sessionId = -1
        try {
            val manifest = repository.verifiedManifest(point)
            val installer = appContext.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(point.packageName)
            sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            try {
                for (entry in manifest.files) {
                    val source = java.io.File(point.backupPath, entry.fileName)
                    FileInputStream(source).use { input ->
                        session.openWrite(entry.fileName, 0L, entry.sizeBytes).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                }
                session.commit(callback.intentSender)
            } finally {
                session.close()
            }
            return RestoreInstallResult.SessionStarted(sessionId)
        } catch (e: Exception) {
            if (sessionId >= 0) {
                try { appContext.packageManager.packageInstaller.abandonSession(sessionId) } catch (_: Exception) { }
            }
            RestoreInstallResult.Failure(e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)
        }
    }
}
