package com.saha.batchuninstaller.debloat

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.util.Locale

data class UadCategoryParseResult(
    val entries: Map<String, UadPackageRecord>,
    val discardedEntryCount: Int
)

/**
 * Parses the current source's category arrays and the old single-object cache for migration.
 * Optional fields are lenient, but retained records require a valid package ID and removal value.
 */
object UadDatabaseParser {
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, UadPackageRecord>>() {}.type
    private val packageId = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)*$""")

    @Throws(IllegalArgumentException::class)
    fun parse(json: String): Map<String, UadPackageRecord> {
        val root = parseRoot(json)
        return if (root.isJsonArray) parseCategoryRoot(root, "Misc").entries
        else parseLegacyObject(json, root)
    }

    @Throws(IllegalArgumentException::class)
    fun parseCategory(json: String, category: String): Map<String, UadPackageRecord> =
        parseCategoryDetailed(json, category).entries

    @Throws(IllegalArgumentException::class)
    fun parseCategoryDetailed(json: String, category: String): UadCategoryParseResult =
        parseCategoryRoot(parseRoot(json), category)

    private fun parseRoot(json: String): JsonElement {
        if (json.isBlank()) throw IllegalArgumentException("Database response is empty")
        return try {
            JsonParser.parseString(json)
        } catch (e: JsonParseException) {
            throw IllegalArgumentException("Database JSON is malformed", e)
        } catch (e: RuntimeException) {
            throw IllegalArgumentException("Database JSON is malformed", e)
        }
    }

    private fun parseCategoryRoot(root: JsonElement, category: String): UadCategoryParseResult {
        if (!root.isJsonArray) throw IllegalArgumentException("The $category list must be a JSON array")
        if (root.asJsonArray.size() == 0) throw IllegalArgumentException("The $category list contains no package entries")

        val entries = LinkedHashMap<String, UadPackageRecord>()
        var discarded = 0
        for (element in root.asJsonArray) {
            if (!element.isJsonObject) {
                discarded++
                continue
            }
            val obj = element.asJsonObject
            val id = stringValue(obj, "id")?.trim()
            val rawRemoval = stringValue(obj, "removal")?.trim()
            if (id.isNullOrEmpty() || !packageId.matches(id) || rawRemoval.isNullOrEmpty()) {
                discarded++
                continue
            }

            val removal = when (rawRemoval.toLowerCase(Locale.ROOT)) {
                "delete", "replace", "caution", "unsafe" -> rawRemoval.toLowerCase(Locale.ROOT)
                else -> "caution"
            }
            entries[id] = UadPackageRecord(
                list = category,
                label = stringValue(obj, "label")?.trim()?.takeIf { it.isNotEmpty() },
                description = stringValue(obj, "description")?.trim()?.takeIf { it.isNotEmpty() },
                dependencies = stringArray(obj, "dependencies"),
                neededBy = stringArray(obj, "required_by"),
                labels = stringArray(obj, "tags"),
                removal = removal,
                warning = stringValue(obj, "warning")?.trim()?.takeIf { it.isNotEmpty() },
                web = stringArray(obj, "web"),
                suggestions = stringValue(obj, "suggestions")?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
        if (entries.isEmpty()) throw IllegalArgumentException("The $category list contains no valid package entries")
        return UadCategoryParseResult(entries, discarded)
    }

    private fun parseLegacyObject(json: String, root: JsonElement): Map<String, UadPackageRecord> {
        if (!root.isJsonObject) throw IllegalArgumentException("Database root must be an object or category array")
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
            if (!packageId.matches(id) || record == null) continue
            valid[id] = record.copy(
                description = record.description?.trim()?.takeIf { it.isNotEmpty() },
                dependencies = record.dependencies.orEmpty(),
                neededBy = record.neededBy.orEmpty(),
                labels = record.labels.orEmpty(),
                web = record.web.orEmpty()
            )
        }
        if (valid.isEmpty()) throw IllegalArgumentException("Database contains no valid package identifiers")
        return valid
    }

    /** Duplicate IDs retain the most restrictive classification to avoid understating risk. */
    fun mergeCategories(categories: Iterable<Map<String, UadPackageRecord>>): Map<String, UadPackageRecord> {
        val merged = LinkedHashMap<String, UadPackageRecord>()
        for (category in categories) {
            for ((id, record) in category) {
                val current = merged[id]
                if (current == null || severity(record.removal) > severity(current.removal)) merged[id] = record
            }
        }
        return merged
    }

    private fun severity(removal: String?): Int = when (removal?.trim()?.toLowerCase(Locale.ROOT)) {
        "delete", "recommended" -> 0
        "replace" -> 1
        "caution", "advanced" -> 2
        "unsafe", "expert" -> 3
        else -> 2
    }

    private fun stringValue(obj: JsonObject, key: String): String? {
        val value = obj.get(key) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
    }

    private fun stringArray(obj: JsonObject, key: String): List<String> {
        val value = obj.get(key) ?: return emptyList()
        if (!value.isJsonArray) return emptyList()
        return value.asJsonArray.mapNotNull { item ->
            if (item.isJsonPrimitive && item.asJsonPrimitive.isString) item.asString else null
        }
    }
}
