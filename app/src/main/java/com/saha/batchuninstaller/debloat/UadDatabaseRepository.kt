package com.saha.batchuninstaller.debloat

import android.content.Context
import android.preference.PreferenceManager
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class UadDatabaseRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    private val cacheFile = File(File(appContext.filesDir, "uad"), "uad_lists.json")

    companion object {
        const val SOURCE_URL = "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
        private const val PREF_ETAG = "uad_etag"
        private const val PREF_LAST_MODIFIED = "uad_last_modified"
        private const val PREF_FETCHED_AT = "uad_fetched_at"
        private const val MAX_BYTES = 8 * 1024 * 1024
    }

    @Synchronized
    @Throws(IOException::class, IllegalArgumentException::class)
    fun refresh(): UadDatabaseSnapshot {
        val connection = (URL(SOURCE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AppSieve-Android")
            prefs.getString(PREF_ETAG, null)?.let { setRequestProperty("If-None-Match", it) }
            prefs.getString(PREF_LAST_MODIFIED, null)?.let { setRequestProperty("If-Modified-Since", it) }
        }
        try {
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                val cached = readValidCache()
                return UadDatabaseSnapshot(cached, prefs.getString(PREF_ETAG, null), prefs.getLong(PREF_FETCHED_AT, 0L), true)
            }
            if (status !in 200..299) throw IOException("Database server returned HTTP $status")

            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_BYTES) throw IOException("Database response exceeds 8 MiB limit")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val json = String(bytes, Charsets.UTF_8)
            val parsed = UadDatabaseParser.parse(json) // Validate before touching the current cache.
            val now = System.currentTimeMillis()
            cacheFile.parentFile?.mkdirs()
            val temp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            temp.writeText(json, Charsets.UTF_8)
            if (cacheFile.exists() && !cacheFile.delete()) {
                temp.delete()
                throw IOException("Could not replace previous database cache")
            }
            if (!temp.renameTo(cacheFile)) {
                temp.delete()
                throw IOException("Could not commit validated database cache")
            }
            prefs.edit()
                .putString(PREF_ETAG, connection.getHeaderField("ETag"))
                .putString(PREF_LAST_MODIFIED, connection.getHeaderField("Last-Modified"))
                .putLong(PREF_FETCHED_AT, now)
                .apply()
            return UadDatabaseSnapshot(parsed, connection.getHeaderField("ETag") ?: connection.getHeaderField("Last-Modified"), now, false)
        } finally {
            connection.disconnect()
        }
    }

    fun loadCached(): UadDatabaseSnapshot? {
        if (!cacheFile.isFile) return null
        return try {
            UadDatabaseSnapshot(
                UadDatabaseParser.parse(cacheFile.readText(Charsets.UTF_8)),
                prefs.getString(PREF_ETAG, null),
                prefs.getLong(PREF_FETCHED_AT, cacheFile.lastModified()),
                true
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun readValidCache(): Map<String, UadPackageRecord> {
        if (!cacheFile.isFile) throw IOException("Server returned not-modified but no valid cache exists")
        return try {
            UadDatabaseParser.parse(cacheFile.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            throw IOException("Cached database is invalid", e)
        }
    }

    fun lastSuccessfulUpdateMillis(): Long = prefs.getLong(PREF_FETCHED_AT, 0L)
}
