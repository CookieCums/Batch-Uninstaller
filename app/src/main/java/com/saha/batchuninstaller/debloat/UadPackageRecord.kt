package com.saha.batchuninstaller.debloat

/** Metadata supplied by the community-maintained Android debloat list. */
data class UadPackageRecord(
    val list: String? = null,
    val label: String? = null,
    val description: String? = null,
    val dependencies: List<String>? = emptyList(),
    val neededBy: List<String>? = emptyList(),
    val labels: List<String>? = emptyList(),
    val removal: String? = null,
    val warning: String? = null,
    val web: List<String>? = emptyList(),
    val suggestions: String? = null
)

enum class DebloatRisk(val displayName: String) {
    RECOMMENDED("Safe to remove"),
    REPLACE("Replace first"),
    CAUTION("Use caution"),
    HIGH_RISK("Unsafe to remove"),
    UNKNOWN("Unknown")
}

data class UadDatabaseSnapshot(
    val packages: Map<String, UadPackageRecord>,
    val revision: String?,
    val fetchedAtMillis: Long,
    /** True when the refresh was served from a bundled/local cache or every file returned HTTP 304. */
    val fromCache: Boolean,
    val discardedEntryCount: Int = 0
) {
    val entryCount: Int get() = packages.size
}

object UadRiskMapper {
    fun fromRemoval(removal: String?): DebloatRisk = when (removal?.trim()?.toLowerCase(java.util.Locale.ROOT)) {
        "delete", "recommended" -> DebloatRisk.RECOMMENDED
        "replace" -> DebloatRisk.REPLACE
        "caution", "advanced" -> DebloatRisk.CAUTION
        "unsafe", "expert" -> DebloatRisk.HIGH_RISK
        else -> DebloatRisk.UNKNOWN
    }
}
