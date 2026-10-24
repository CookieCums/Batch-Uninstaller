package com.saha.batchuninstaller.debloat

/** Describes whether an uninstall should retain a verified backup or intentionally keep none. */
enum class UninstallMode {
    RESTORABLE,
    PERMANENT;

    companion object {
        const val PREFERENCE_KEY = "default_uninstall_mode"

        fun fromPreference(value: String?): UninstallMode =
            values().firstOrNull { it.name == value } ?: RESTORABLE
    }
}
