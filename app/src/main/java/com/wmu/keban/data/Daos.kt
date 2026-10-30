package com.wmu.keban.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 应用设置(键值对):学期起始日等
 */
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String
)

@Dao
interface TaskDao {
    /** 全部待办:未完成在前,有截止的按时间升序,未设截止的排在其后 */
    @Query("SELECT * FROM tasks ORDER BY done ASC, dueAt IS NULL ASC, dueAt ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskItem>>

    /** 最近待办(今天页用):仅未完成,前 5 条 */
    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY dueAt IS NULL ASC, dueAt ASC LIMIT 5")
    fun observeUpcoming(): Flow<List<TaskItem>>

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun count(): Int

    @Insert
    suspend fun insert(task: TaskItem): Long

    @Update
    suspend fun update(task: TaskItem)

    @Delete
    suspend fun delete(task: TaskItem)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings WHERE `key` = :key")
    fun observe(key: String): Flow<Setting?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: Setting)
}
