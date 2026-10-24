package com.saha.batchuninstaller.debloat

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import androidx.annotation.Keep
import com.google.gson.GsonBuilder
import com.saha.batchuninstaller.RoomStorage.AppDatabase
import com.saha.batchuninstaller.RoomStorage.RestorePoint
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

@Keep
data class RestoreFileEntry(
    val fileName: String,
    val sha256: String,
    val sizeBytes: Long
)

@Keep
data class RestoreManifest(
    val id: String,
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val files: List<RestoreFileEntry>,
    val signingDigest: String,
    val hasData: Boolean,
    val installedAt: Long,
    val removedAt: Long,
    val isSystemApp: Boolean,
    val method: String,
    val sizeBytes: Long
)

data class BackupPreflight(
    val allowed: Boolean,
    val message: String? = null,
    val requiredBytes: Long = 0,
    val availableBytes: Long = 0
)

sealed class RestoreBackupResult {
    data class Success(val point: RestorePoint) : RestoreBackupResult()
    data class Failure(val reason: String) : RestoreBackupResult()
}

/**
 * Makes a verified APK-only restore point before an app is removed.
 *
 * The repository intentionally does not run uninstall commands. Callers must only begin a
 * restorable uninstall after this method succeeds; a failure means the app must be left installed.
 * Optional app-data archives are not created by this increment.
 */
class RestoreRepository(
    context: Context,
    private val database: AppDatabase
) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val json = GsonBuilder().setPrettyPrinting().create()

    private fun backupRoot(): File =
        (appContext.getExternalFilesDir("restore-points") ?: File(appContext.filesDir, "restore-points"))

    /**
     * Batch preflight: make sure backup storage is writable and has room for selected APKs plus
     * a 10% buffer. This method only inspects file metadata; copying is done by backupApp off-main.
     */
    fun preflightBatch(packages: List<ApplicationInfo>): BackupPreflight {
        val root = backupRoot()
        if (!root.exists() && !root.mkdirs()) {
            return BackupPreflight(false, "Could not create the restore-point folder.")
        }
        val probe = File(root, ".appsieve-write-test-${UUID.randomUUID()}")
        try {
            FileOutputStream(probe).use { stream ->
                stream.write(byteArrayOf(0x41, 0x53, 0x56))
                stream.fd.sync()
            }
        } catch (e: IOException) {
            return BackupPreflight(false, "The restore-point folder is not writable: ${e.message}")
        } finally {
            probe.delete()
        }

        val totalBytes = packages.fold(0L) { total, app ->
            val sourceBytes = apkFiles(app).fold(0L) { inner, file ->
                val size = if (file.isFile) file.length().coerceAtLeast(0L) else 0L
                if (Long.MAX_VALUE - inner < size) Long.MAX_VALUE else inner + size
            }
            if (Long.MAX_VALUE - total < sourceBytes) Long.MAX_VALUE else total + sourceBytes
        }
        val reserve = totalBytes / 10 + if (totalBytes % 10L == 0L) 0L else 1L
        val required = if (Long.MAX_VALUE - totalBytes < reserve) Long.MAX_VALUE else totalBytes + reserve
        val available = root.usableSpace.coerceAtLeast(0L)
        if (available < required) {
            val shortfall = required - available
            val requiredText = android.text.format.Formatter.formatFileSize(appContext, required)
            val availableText = android.text.format.Formatter.formatFileSize(appContext, available)
            val shortfallText = android.text.format.Formatter.formatFileSize(appContext, shortfall)
            return BackupPreflight(
                false,
                "Not enough space for restorable uninstall. Need $requiredText, available $availableText (short by $shortfallText).",
                required,
                available
            )
        }
        return BackupPreflight(true, requiredBytes = required, availableBytes = available)
    }

    /**
     * Copies every APK path, verifies hashes from disk, writes restore.json, re-verifies,
     * then inserts the Room row. Failure cleans up partial files and leaves removal to the caller.
     */
    fun backupApp(application: ApplicationInfo): RestoreBackupResult {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return RestoreBackupResult.Failure("Backup must run in the background. The app was not removed.")
        }

        val packageName = application.packageName
        var backupDirectory: File? = null
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            val sourceFiles = apkFiles(packageInfo.applicationInfo)
            if (sourceFiles.isEmpty()) throw IOException("No APK files were found for $packageName.")
            sourceFiles.firstOrNull { !it.isFile || !it.canRead() }?.let { source ->
                throw IOException("APK is not readable: ${source.name}")
            }

            val backupId = UUID.randomUUID().toString()
            val versionDirectory = File(File(backupRoot(), packageName), packageInfo.versionCode.toString())
            backupDirectory = File(versionDirectory, backupId)
            if (!backupDirectory.mkdirs()) throw IOException("Could not create the backup folder for $packageName.")

            val copiedFiles = ArrayList<RestoreFileEntry>()
            for ((index, source) in sourceFiles.withIndex()) {
                val fileName = if (index == 0) "base.apk" else "split_%02d.apk".format(index)
                val destination = File(backupDirectory, fileName)
                val sourceHash = RestoreFileIntegrity.sha256(source)
                val partial = File(backupDirectory, "$fileName.partial")
                source.inputStream().use { input ->
                    FileOutputStream(partial).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                if (!RestoreFileIntegrity.matches(partial, sourceHash)) {
                    throw IOException("Backup verification failed for ${source.name}; the app was not removed.")
                }
                if (!partial.renameTo(destination)) {
                    throw IOException("Could not finalize the backup file ${source.name}.")
                }
                val verifiedHash = RestoreFileIntegrity.sha256(destination)
                copiedFiles.add(RestoreFileEntry(fileName, verifiedHash, destination.length()))
            }

            val signingDigest = signingDigest(packageInfo)
            val removedAt = System.currentTimeMillis()
            val label = packageInfo.applicationInfo.loadLabel(packageManager).toString()
            val systemApp = (packageInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                packageInfo.applicationInfo.sourceDir.startsWith("/system/") ||
                packageInfo.applicationInfo.sourceDir.startsWith("/product/")

            val manifest = RestoreManifest(
                id = backupId,
                packageName = packageName,
                label = label,
                versionName = packageInfo.versionName.orEmpty(),
                versionCode = packageInfo.versionCode.toLong(),
                files = copiedFiles,
                signingDigest = signingDigest,
                hasData = false,
                installedAt = packageInfo.firstInstallTime,
                removedAt = removedAt,
                isSystemApp = systemApp,
                method = if (systemApp) "SOFT_REMOVE" else "APK_BACKUP",
                sizeBytes = copiedFiles.sumOf { it.sizeBytes }
            )

            val manifestFile = File(backupDirectory, "restore.json")
            val manifestPartial = File(backupDirectory, "restore.json.partial")
            FileOutputStream(manifestPartial).use { output ->
                output.write(json.toJson(manifest).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (!manifestPartial.renameTo(manifestFile)) {
                throw IOException("Could not finalize restore metadata for $packageName.")
            }

            // Re-read each copied APK after the manifest has been finalized, immediately before
            // making the restore point visible in the database.
            for (entry in copiedFiles) {
                if (!RestoreFileIntegrity.matches(File(backupDirectory, entry.fileName), entry.sha256)) {
                    throw IOException("Backup hash mismatch for ${entry.fileName}; the app was not removed.")
                }
            }

            val point = RestorePoint(
                id = backupId,
                packageName = packageName,
                label = label,
                versionName = packageInfo.versionName.orEmpty(),
                versionCode = packageInfo.versionCode.toLong(),
                isSystemApp = systemApp,
                method = manifest.method,
                backupPath = backupDirectory.absolutePath,
                hasData = false,
                sizeBytes = manifest.sizeBytes,
                signingDigest = signingDigest,
                installedAt = packageInfo.firstInstallTime,
                removedAt = removedAt
            )
            database.restorePointDao().insert(point)
            RestoreBackupResult.Success(point)
        } catch (e: Exception) {
            backupDirectory?.deleteRecursively()
            val reason = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            RestoreBackupResult.Failure("Could not verify a restorable backup for $packageName: $reason")
        }
    }

    /**
     * Reads the restore manifest, validates every file hash/path, and checks the APK package ID
     * and signing certificate against the stored restore-point metadata. Call off the UI thread.
     */
    @Throws(IOException::class)
    fun verifiedManifest(point: RestorePoint): RestoreManifest {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IOException("Backup verification must run in the background.")
        }
        val directory = File(point.backupPath).canonicalFile
        if (!directory.isDirectory) throw IOException("The restore-point folder is missing.")
        val manifestFile = File(directory, "restore.json")
        if (!manifestFile.isFile) throw IOException("restore.json is missing from this restore point.")

        val manifest = try {
            json.fromJson(manifestFile.readText(Charsets.UTF_8), RestoreManifest::class.java)
        } catch (e: Exception) {
            throw IOException("restore.json is malformed.", e)
        } ?: throw IOException("restore.json is empty.")

        if (manifest.id != point.id || manifest.packageName != point.packageName) {
            throw IOException("The restore metadata does not match the selected restore point.")
        }
        if (manifest.files.isEmpty()) throw IOException("No APK files are recorded in restore.json.")
        val allowedFileName = Regex("""^(base\.apk|split_[0-9]{2}\.apk)$""")
        if (manifest.files.map { it.fileName }.distinct().size != manifest.files.size) {
            throw IOException("The restore metadata contains duplicate APK file names.")
        }
        if (manifest.files.none { it.fileName == "base.apk" }) {
            throw IOException("The base APK is missing from the restore metadata.")
        }

        for (entry in manifest.files) {
            if (!allowedFileName.matches(entry.fileName)) throw IOException("Invalid backup file name.")
            val file = File(directory, entry.fileName).canonicalFile
            if (file.parentFile != directory || !file.isFile) {
                throw IOException("A backup file is missing or points outside the restore folder.")
            }
            if (file.length() != entry.sizeBytes || !RestoreFileIntegrity.matches(file, entry.sha256)) {
                throw IOException("Backup hash mismatch for ${entry.fileName}. No app was installed.")
            }
        }

        val baseApk = File(directory, "base.apk")
        val archived = packageManager.getPackageArchiveInfo(baseApk.absolutePath, PackageManager.GET_SIGNATURES)
            ?: throw IOException("Android could not read the saved APK.")
        if (archived.packageName != point.packageName || manifest.packageName != archived.packageName) {
            throw IOException("The saved APK package name does not match this restore point.")
        }
        val archivedDigest = signingDigest(archived)
        if (archivedDigest.isBlank() || archivedDigest != point.signingDigest || archivedDigest != manifest.signingDigest) {
            throw IOException("The saved APK signing certificate does not match this restore point.")
        }
        return manifest
    }

    /** Removes saved files and the Room row. Call from a background thread. */
    fun deleteBackup(point: RestorePoint): Boolean {
        val directory = File(point.backupPath)
        if (directory.exists() && !directory.deleteRecursively()) return false
        return try {
            database.restorePointDao().delete(point)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun apkFiles(application: ApplicationInfo): List<File> {
        val paths = LinkedHashSet<String>()
        application.sourceDir?.takeIf { it.isNotBlank() }?.let { paths.add(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            application.splitSourceDirs?.filter { it.isNotBlank() }?.forEach { paths.add(it) }
        }
        return paths.map(::File)
    }

    private fun signingDigest(info: PackageInfo): String {
        val signatures = info.signatures ?: return ""
        if (signatures.isEmpty()) return ""
        return signatures.map { signature ->
            val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            digest.joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
        }.sorted().joinToString(",")
    }
}
