package com.saha.batchuninstaller.debloat

import java.io.File

/**
 * Builds root package-manager commands only for validated package IDs and verified backup files.
 * No command in this object deletes files from Android system partitions.
 */
object RestoreRootCommandBuilder {
    private val packagePattern = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)*$""")
    private val apkNamePattern = Regex("""^(base\.apk|split_[0-9]{2}\.apk)$""")

    fun installExisting(packageName: String, useCmd: Boolean = true): String? {
        if (!packagePattern.matches(packageName)) return null
        return if (useCmd) {
            "cmd package install-existing --user 0 $packageName"
        } else {
            "pm install-existing --user 0 $packageName"
        }
    }

    fun installMultiple(backupDirectory: File, files: List<RestoreFileEntry>): String? {
        val directory = try { backupDirectory.canonicalFile } catch (_: Exception) { return null }
        if (!directory.isDirectory || files.isEmpty() || files.none { it.fileName == "base.apk" }) return null
        if (files.map { it.fileName }.distinct().size != files.size) return null

        val paths = ArrayList<String>()
        for (entry in files) {
            if (!apkNamePattern.matches(entry.fileName)) return null
            val file = try { File(directory, entry.fileName).canonicalFile } catch (_: Exception) { return null }
            if (file.parentFile != directory || !file.isFile) return null
            paths.add(shellQuote(file.absolutePath))
        }
        return "pm install-multiple -r " + paths.joinToString(" ")
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
