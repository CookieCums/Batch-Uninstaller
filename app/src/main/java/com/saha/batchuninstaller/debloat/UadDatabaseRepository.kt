package com.saha.batchuninstaller.debloat

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.preference.PreferenceManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Downloads and validates the five source categories away from the main thread.
 *
 * Files are staged and validated as a complete set before the cache directory is swapped.
 * If a request or validation fails, the last known-good category directory stays in place.
 */
class UadDatabaseRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
    private val cacheParent = File(appContext.filesDir, "uad")
    private val cacheDir = File(cacheParent, "categories")
    private val stagingDir = File(cacheParent, "categories.staging")
    private val previousDir = File(cacheParent, "categories.previous")
    private val legacyCacheFile = File(cacheParent, "uad_lists.json")

    companion object {
        const val SOURCE_BASE_URL = "https://raw.githubusercontent.com/MuntashirAkon/android-debloat-list/master/"
        private const val PREF_PREFIX = "debloat_list_"
        private const val PREF_FETCHED_AT = "uad_fetched_at"
        private const val MAX_BYTES_PER_FILE = 8 * 1024 * 1024
        private const val MAX_RETRIES = 3
        private const val MAX_CONCURRENT_DOWNLOADS = 3
        private val RETRY_BACKOFF_MS = longArrayOf(1_000L, 2_000L, 4_000L)

        private val SOURCES = listOf(
            CategorySource("aosp.json", "AOSP"),
            CategorySource("carrier.json", "Carrier"),
            CategorySource("google.json", "Google"),
            CategorySource("misc.json", "Misc"),
            CategorySource("oem.json", "OEM")
        )

        private fun etagKey(fileName: String) = PREF_PREFIX + fileName + "_etag"
        private fun lastModifiedKey(fileName: String) = PREF_PREFIX + fileName + "_last_modified"
    }

    private data class CategorySource(val fileName: String, val category: String)

    private data class CategoryPayload(
        val source: CategorySource,
        val json: String,
        val entries: Map<String, UadPackageRecord>,
        val discardedEntryCount: Int,
        val etag: String?,
        val lastModified: String?,
        val notModified: Boolean
    )

    @Synchronized
    @Throws(IOException::class, IllegalArgumentException::class)
    fun refresh(): UadDatabaseSnapshot {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException("Database refresh must run off the main thread")
        }

        val executor = Executors.newFixedThreadPool(MAX_CONCURRENT_DOWNLOADS)
        val futures = ArrayList<Future<CategoryPayload>>()
        try {
            for (source in SOURCES) {
                futures += executor.submit(Callable { downloadCategory(source) })
            }

            val payloads = futures.map { future -> getResult(future) }
            val merged = UadDatabaseParser.mergeCategories(payloads.map { it.entries })
            if (merged.isEmpty()) throw IllegalArgumentException("The downloaded debloat database contains no valid entries")

            // The install happens only after every category has downloaded or validated from cache.
            installPayloads(payloads)
            val now = System.currentTimeMillis()
            val editor = prefs.edit().putLong(PREF_FETCHED_AT, now)
            for (payload in payloads) {
                editor.putString(etagKey(payload.source.fileName), payload.etag)
                editor.putString(lastModifiedKey(payload.source.fileName), payload.lastModified)
                if (payload.discardedEntryCount > 0) {
                    android.util.Log.w(
                        "AppSieveDatabase",
                        "Skipped ${payload.discardedEntryCount} invalid entries in ${payload.source.fileName}"
                    )
                }
            }
            editor.apply()

            val revision = payloads.mapNotNull { it.etag }.distinct().joinToString(",").take(180)
                .ifEmpty { "MuntashirAkon/android-debloat-list@master" }
            return UadDatabaseSnapshot(
                packages = merged,
                revision = revision,
                fetchedAtMillis = now,
                fromCache = payloads.all { it.notModified },
                discardedEntryCount = payloads.sumOf { it.discardedEntryCount }
            )
        } finally {
            executor.shutdownNow()
        }
    }

    /**
     * Reads a full local category cache first, then seeds it from bundled snapshots if needed.
     * The legacy single-file cache is only a last-resort migration fallback.
     */
    fun loadCached(): UadDatabaseSnapshot? {
        if (!cacheDir.isDirectory && previousDir.isDirectory) previousDir.renameTo(cacheDir)
        val cached = try {
            if (cacheDir.isDirectory) readCachedPayloads() else null
        } catch (_: Exception) {
            null
        }
        if (cached != null) {
            val merged = UadDatabaseParser.mergeCategories(cached.map { it.entries })
            if (merged.isNotEmpty()) {
                return UadDatabaseSnapshot(
                    merged,
                    cached.mapNotNull { it.etag }.distinct().joinToString(",").take(180),
                    lastSuccessfulUpdateMillis(),
                    true,
                    cached.sumOf { it.discardedEntryCount }
                )
            }
        }

        try {
            val bundled = readBundledPayloads()
            installPayloads(bundled)
            val merged = UadDatabaseParser.mergeCategories(bundled.map { it.entries })
            return UadDatabaseSnapshot(
                merged,
                "bundled snapshot",
                lastSuccessfulUpdateMillis(),
                true,
                bundled.sumOf { it.discardedEntryCount }
            )
        } catch (assetError: Exception) {
            // Preserve data from older AppSieve builds if bundled assets are unexpectedly unavailable.
            if (legacyCacheFile.isFile) {
                try {
                    val legacy = UadDatabaseParser.parse(legacyCacheFile.readText(Charsets.UTF_8))
                    return UadDatabaseSnapshot(
                        legacy,
                        prefs.getString("uad_etag", null),
                        lastSuccessfulUpdateMillis(),
                        true
                    )
                } catch (_: Exception) {
                    android.util.Log.w("AppSieveDatabase", "No valid cached or bundled debloat list is available", assetError)
                }
            } else {
                android.util.Log.w("AppSieveDatabase", "No valid cached or bundled debloat list is available", assetError)
            }
        }
        return null
    }

    private fun downloadCategory(source: CategorySource): CategoryPayload {
        val validCached = readCachedCategory(source)
        val storedEtag = if (validCached != null) prefs.getString(etagKey(source.fileName), null) else null
        var attempt = 0

        while (true) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(SOURCE_BASE_URL + source.fileName).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "AppSieve-Android")
                    if (!storedEtag.isNullOrBlank()) setRequestProperty("If-None-Match", storedEtag)
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    val cached = validCached ?: throw IOException(
                        "GitHub returned HTTP 304 for ${source.fileName} but no valid cached copy exists"
                    )
                    return cached.copy(
                        etag = connection.getHeaderField("ETag") ?: storedEtag,
                        lastModified = connection.getHeaderField("Last-Modified")
                            ?: prefs.getString(lastModifiedKey(source.fileName), null),
                        notModified = true
                    )
                }
                if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                    throw InvalidDatabaseFileException("The list file '${source.fileName}' could not be read (HTTP 404)")
                }
                if (status >= 500) {
                    throw IOException("GitHub returned HTTP $status for ${source.fileName}")
                }
                if (status !in 200..299) {
                    throw InvalidDatabaseFileException("The list file '${source.fileName}' could not be read (HTTP $status)")
                }

                val json = readBoundedResponse(connection)
                val parsed = try {
                    UadDatabaseParser.parseCategoryDetailed(json, source.category)
                } catch (e: IllegalArgumentException) {
                    throw InvalidDatabaseFileException("The list file '${source.fileName}' contains invalid JSON or no valid entries", e)
                }
                return CategoryPayload(
                    source,
                    json,
                    parsed.entries,
                    parsed.discardedEntryCount,
                    connection.getHeaderField("ETag"),
                    connection.getHeaderField("Last-Modified"),
                    false
                )
            } catch (e: InvalidDatabaseFileException) {
                throw e
            } catch (e: IOException) {
                if (attempt >= MAX_RETRIES) {
                    throw IOException("GitHub unreachable while downloading '${source.fileName}': ${e.message}", e)
                }
                try {
                    Thread.sleep(RETRY_BACKOFF_MS[attempt])
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Database update was cancelled", interrupted)
                }
                attempt++
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun readBoundedResponse(connection: HttpURLConnection): String {
        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_BYTES_PER_FILE) {
                    throw IOException("The response exceeded the 8 MiB limit")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun getResult(future: Future<CategoryPayload>): CategoryPayload {
        try {
            return future.get()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Database update was cancelled", e)
        } catch (e: ExecutionException) {
            val cause = e.cause
            when (cause) {
                is IOException -> throw cause
                is IllegalArgumentException -> throw cause
                else -> throw IOException("Unable to download a debloat list category", cause)
            }
        }
    }

    private fun readCachedCategory(source: CategorySource): CategoryPayload? {
        val file = File(cacheDir, source.fileName)
        if (!file.isFile) return null
        return try {
            val json = file.readText(Charsets.UTF_8)
            val parsed = UadDatabaseParser.parseCategoryDetailed(json, source.category)
            CategoryPayload(
                source,
                json,
                parsed.entries,
                parsed.discardedEntryCount,
                prefs.getString(etagKey(source.fileName), null),
                prefs.getString(lastModifiedKey(source.fileName), null),
                true
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun readCachedPayloads(): List<CategoryPayload> =
        SOURCES.map { source ->
            readCachedCategory(source)
                ?: throw IOException("The cached list file '${source.fileName}' is unavailable or invalid")
        }

    private fun readBundledPayloads(): List<CategoryPayload> =
        SOURCES.map { source ->
            val json = appContext.assets.open("debloat/${source.fileName}").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parsed = UadDatabaseParser.parseCategoryDetailed(json, source.category)
            CategoryPayload(
                source,
                json,
                parsed.entries,
                parsed.discardedEntryCount,
                null,
                null,
                true
            )
        }

    private fun installPayloads(payloads: List<CategoryPayload>) {
        if (!cacheParent.exists() && !cacheParent.mkdirs()) {
            throw IOException("Could not create debloat cache directory")
        }
        if (stagingDir.exists() && !stagingDir.deleteRecursively()) {
            throw IOException("Could not clear previous database staging files")
        }
        if (!stagingDir.mkdirs()) throw IOException("Could not create database staging directory")

        try {
            for (source in SOURCES) {
                val payload = payloads.firstOrNull { it.source.fileName == source.fileName }
                    ?: throw IOException("Missing downloaded data for ${source.fileName}")
                val output = File(stagingDir, source.fileName)
                output.writeText(payload.json, Charsets.UTF_8)
                // Re-read and validate the staged file before swapping the current cache.
                UadDatabaseParser.parseCategory(output.readText(Charsets.UTF_8), source.category)
            }

            if (previousDir.exists() && !previousDir.deleteRecursively()) {
                throw IOException("Could not clear previous cache backup")
            }
            val hadCurrent = cacheDir.exists()
            if (hadCurrent && !cacheDir.renameTo(previousDir)) {
                throw IOException("Could not preserve the previous debloat database")
            }
            if (!stagingDir.renameTo(cacheDir)) {
                if (hadCurrent) previousDir.renameTo(cacheDir)
                throw IOException("Could not commit the new debloat database; the previous list was retained")
            }
            if (previousDir.exists()) previousDir.deleteRecursively()
        } catch (e: Exception) {
            if (stagingDir.exists()) stagingDir.deleteRecursively()
            if (!cacheDir.exists() && previousDir.exists()) previousDir.renameTo(cacheDir)
            if (e is IOException) throw e
            throw IOException("Could not install the debloat database", e)
        }
    }

    fun lastSuccessfulUpdateMillis(): Long = prefs.getLong(PREF_FETCHED_AT, 0L)

    private class InvalidDatabaseFileException(message: String, cause: Throwable? = null) : IOException(message, cause)
}
