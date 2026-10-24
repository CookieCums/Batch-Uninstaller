package com.saha.batchuninstaller.utils

import com.saha.batchuninstaller.debloat.UninstallMode

/** Produces narrowly scoped package-manager commands; it never removes files from system partitions. */
object UninstallCommandBuilder {
    private val packageNamePattern = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)*$""")

    fun build(packageName: String, isSystemApp: Boolean, mode: UninstallMode): String? {
        if (!packageNamePattern.matches(packageName)) return null
        return when {
            !isSystemApp -> "pm uninstall $packageName"
            mode == UninstallMode.RESTORABLE -> "pm uninstall -k --user 0 $packageName"
            else -> "pm uninstall --user 0 $packageName; result=\$?; if [ \$result -eq 0 ]; then pm clear --user 0 $packageName >/dev/null 2>&1 || true; fi; exit \$result"
        }
    }
}
