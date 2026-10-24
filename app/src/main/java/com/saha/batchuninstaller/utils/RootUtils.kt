/*
 * This file is part of Batch Uninstaller.
 *
 * Batch Uninstaller is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.saha.batchuninstaller.utils

import android.content.pm.ApplicationInfo
import android.util.Log
import com.saha.batchuninstaller.debloat.UninstallMode

object RootUtils {
    private const val TAG = "AppSieveRoot"

    fun ApplicationInfo.uninstallAppRoot(
        mode: UninstallMode,
        rootExecutor: RootExecutor = SuRootExecutor
    ): Boolean {
        val isSystemApp = (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            sourceDir.startsWith("/system/") ||
            sourceDir.startsWith("/product/")
        val command = UninstallCommandBuilder.build(packageName, isSystemApp, mode)
        if (command == null) {
            Log.e(TAG, "Refusing unsafe package name: $packageName")
            return false
        }
        val result = rootExecutor.execute(command)
        if (!result.success) {
            Log.e(TAG, "Uninstall failed for $packageName (exit=${result.exitCode}, timeout=${result.timedOut}): ${result.output}")
        }
        return result.success
    }
}
