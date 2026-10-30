package com.wmu.keban.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {

    // ── 查询 ──

    @Transaction
    @Query("SELECT * FROM courses ORDER BY name")
    fun observeCoursesWithSlots(): Flow<List<CourseWithSlots>>

    @Transaction
    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun courseWithSlots(id: Long): CourseWithSlots?

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun courseCount(): Int

    @Query("SELECT COUNT(*) FROM courses WHERE manual = 0")
    suspend fun importedCourseCount(): Int

    @Query("SELECT * FROM import_records ORDER BY importedAt DESC LIMIT 1")
    suspend fun lastImport(): ImportRecord?

    @Query("SELECT * FROM import_records WHERE contentSignature = :signature ORDER BY importedAt DESC LIMIT 1")
    suspend fun findImportBySignature(signature: String): ImportRecord?

    // ── 写入 ──

    @Insert
    suspend fun insertCourse(course: Course): Long

    @Insert
    suspend fun insertSlots(slots: List<CourseSlot>)

    @Update
    suspend fun updateCourse(course: Course)

    @Insert
    suspend fun insertImport(record: ImportRecord): Long

    /** 删除全部导入课程(保留手动添加的) —— 时段经外键级联删除 */
    @Query("DELETE FROM courses WHERE manual = 0")
    suspend fun deleteImportedCourses()

    @Query("DELETE FROM courses")
    suspend fun deleteAllCourses()

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourse(id: Long)

    @Query("DELETE FROM course_slots WHERE courseId = :id")
    suspend fun deleteSlotsOfCourse(id: Long)

    @Query("DELETE FROM import_records")
    suspend fun clearImportRecords()
}
