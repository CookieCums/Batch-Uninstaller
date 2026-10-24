package com.saha.batchuninstaller.debloat

/** Metadata supplied by the community-maintained UAD-ng list. */
data class UadPackageRecord(
    val list: String? = null,
    val description: String? = null,
    val dependencies: List<String>? = emptyList(),
    val neededBy: List<String>? = emptyList(),
    val labels: List<String>? = emptyList(),
    val removal: String? = null
)

enum class DebloatRisk(val displayName: String) {
    RECOMMENDED("Recommended"),
    CAUTION("Use caution"),
    HIGH_RISK("High risk"),
    UNKNOWN("Unknown")
}

data class UadDatabaseSnapshot(
    val packages: Map<String, UadPackageRecord>,
    val revision: String?,
    val fetchedAtMillis: Long,
    val fromCache: Boolean
)

object UadRiskMapper {
    fun fromRemoval(removal: String?): DebloatRisk = when (removal?.trim()) {
        "Recommended" -> DebloatRisk.RECOMMENDED
        "Advanced" -> DebloatRisk.CAUTION
        "Expert", "Unsafe" -> DebloatRisk.HIGH_RISK
        else -> DebloatRisk.UNKNOWN
    }
}
