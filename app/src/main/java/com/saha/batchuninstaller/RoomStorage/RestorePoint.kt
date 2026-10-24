package com.saha.batchuninstaller.RoomStorage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A verified on-disk copy of an app's APKs. This row is created only after every
 * copied APK has passed SHA-256 verification and restore.json has been written.
 */
@Entity(
    tableName = "restore_points",
    indices = [
        Index(value = ["package_name"]),
        Index(value = ["removed_at"])
    ]
)
data class RestorePoint(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "label")
    val label: String,
    @ColumnInfo(name = "version_name")
    val versionName: String,
    @ColumnInfo(name = "version_code")
    val versionCode: Long,
    @ColumnInfo(name = "is_system_app")
    val isSystemApp: Boolean,
    /** SOFT_REMOVE for system apps; APK_BACKUP for user apps. */
    @ColumnInfo(name = "method")
    val method: String,
    @ColumnInfo(name = "backup_path")
    val backupPath: String,
    @ColumnInfo(name = "has_data")
    val hasData: Boolean,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "signing_digest")
    val signingDigest: String,
    @ColumnInfo(name = "installed_at")
    val installedAt: Long,
    @ColumnInfo(name = "removed_at")
    val removedAt: Long
)
