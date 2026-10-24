package com.saha.batchuninstaller.RoomStorage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Profile::class, RestorePoint::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun restorePointDao(): RestorePointDao

    companion object {
        /** Adds restore-point bookkeeping without deleting existing saved profiles. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `restore_points` (
                        `id` TEXT NOT NULL,
                        `package_name` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `version_name` TEXT NOT NULL,
                        `version_code` INTEGER NOT NULL,
                        `is_system_app` INTEGER NOT NULL,
                        `method` TEXT NOT NULL,
                        `backup_path` TEXT NOT NULL,
                        `has_data` INTEGER NOT NULL,
                        `size_bytes` INTEGER NOT NULL,
                        `signing_digest` TEXT NOT NULL,
                        `installed_at` INTEGER NOT NULL,
                        `removed_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )"""
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_restore_points_package_name` ON `restore_points` (`package_name`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_restore_points_removed_at` ON `restore_points` (`removed_at`)"
                )
            }
        }
    }
}
