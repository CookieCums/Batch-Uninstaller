package com.saha.batchuninstaller.RoomStorage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RestorePointDao {
    @Query("SELECT * FROM restore_points ORDER BY removed_at DESC")
    fun getAll(): List<RestorePoint>

    @Query("SELECT * FROM restore_points WHERE id = :id LIMIT 1")
    fun getById(id: String): RestorePoint?

    @Query("SELECT * FROM restore_points WHERE package_name = :packageName ORDER BY removed_at DESC")
    fun getForPackage(packageName: String): List<RestorePoint>

    @Query("SELECT COUNT(*) FROM restore_points")
    fun count(): Int

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM restore_points")
    fun totalSizeBytes(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(point: RestorePoint)

    @Delete
    fun delete(point: RestorePoint)
}
