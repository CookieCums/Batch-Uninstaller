package com.saha.batchuninstaller.debloat

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken

/** Strictly validates the root structure and package identifiers while tolerating optional fields. */
object UadDatabaseParser {
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, UadPackageRecord>>() {}.type
    private val packageId = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    @Throws(IllegalArgumentException::class)
    fun parse(json: String): Map<String, UadPackageRecord> {
        if (json.isBlank()) throw IllegalArgumentException("Database response is empty")
        val parsed = try {
            gson.fromJson<Map<String, UadPackageRecord>>(json, mapType)
        } catch (e: JsonParseException) {
            throw IllegalArgumentException("Database JSON is malformed", e)
        } catch (e: RuntimeException) {
            throw IllegalArgumentException("Database JSON is malformed", e)
        }
        if (parsed.isNullOrEmpty()) throw IllegalArgumentException("Database contains no package entries")

        val valid = LinkedHashMap<String, UadPackageRecord>()
        for ((id, record) in parsed) {
            if (!packageId.matches(id)) continue
            if (record == null) continue
            // Core values must have the expected JSON type. Gson handles incompatible types as parse errors.
            valid[id] = record.copy(
                description = record.description?.trim()?.takeIf { it.isNotEmpty() },
                dependencies = record.dependencies.orEmpty(),
                neededBy = record.neededBy.orEmpty(),
                labels = record.labels.orEmpty()
            )
        }
        if (valid.isEmpty()) throw IllegalArgumentException("Database contains no valid package identifiers")
        return valid
    }
}
