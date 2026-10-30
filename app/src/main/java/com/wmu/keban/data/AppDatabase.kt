package com.wmu.keban.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wmu.keban.pdf.ParsedTimetable
import kotlinx.coroutines.flow.Flow

/**
 * 应用数据库(版本 2:新增课表相关表)
 *
 * 迁移策略:仅**增量建表**,不改动 tasks/settings —— 升级不破坏用户已有待办数据。
 * v1 → v2 的建表语句与 Room 导出的 schema JSON 严格一致(位于 app/schemas 目录)。
 */
@Database(
    entities = [TaskItem::class, Setting::class, Course::class, CourseSlot::class, ImportRecord::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun settingDao(): SettingDao
    abstract fun timetableDao(): TimetableDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `courses` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `typeLabel` TEXT, `campus` TEXT, `room` TEXT, " +
                        "`teacher` TEXT, `classGroup` TEXT, `assessment` TEXT, " +
                        "`manual` INTEGER NOT NULL, `needsConfirm` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `course_slots` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`courseId` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL, " +
                        "`startSection` INTEGER NOT NULL, `endSection` INTEGER NOT NULL, " +
                        "`weeksRaw` TEXT NOT NULL, `weeksCanonical` TEXT NOT NULL, " +
                        "`weeksExpanded` TEXT NOT NULL, `parity` TEXT NOT NULL, " +
                        "`marker` TEXT, `typeLabel` TEXT, `teacher` TEXT, `room` TEXT, " +
                        "`needsConfirm` INTEGER NOT NULL, `confirmNotes` TEXT, `rawCell` TEXT, " +
                        "FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_course_slots_courseId` " +
                        "ON `course_slots` (`courseId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `import_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`fileName` TEXT NOT NULL, `fileHash` TEXT NOT NULL, `semester` TEXT, " +
                        "`printTime` TEXT, `slotCount` INTEGER NOT NULL, " +
                        "`courseCount` INTEGER NOT NULL, `contentSignature` TEXT NOT NULL, " +
                        "`importedAt` INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "keban.db"
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}

data class ImportResult(val courseCount: Int, val slotCount: Int, val needsConfirmCount: Int)

/**
 * 课表仓库(课程 / 时段 / 导入记录)
 */
class TimetableRepo(private val db: AppDatabase) {

    val coursesWithSlots: Flow<List<CourseWithSlots>> = db.timetableDao().observeCoursesWithSlots()

    suspend fun courseCount(): Int = db.timetableDao().courseCount()

    suspend fun lastImport(): ImportRecord? = db.timetableDao().lastImport()

    /** 同一内容是否已导入过(用于重复导入提示) */
    suspend fun findSameImport(signature: String): ImportRecord? =
        db.timetableDao().findImportBySignature(signature)

    /**
     * 保存导入结果(事务):
     * ① 删除上次导入的课程(手动添加的保留)② 写入本次结果 ③ 记录导入信息。
     * 任一步失败整体回滚 —— 导入失败不会破坏已有课表。
     */
    suspend fun saveImport(
        parsed: ParsedTimetable,
        fileName: String,
        fileHash: String
    ): ImportResult = db.withTransaction {
        val dao = db.timetableDao()
        val assembled = TimetableAssembler.toCourses(parsed)
        dao.deleteImportedCourses()
        assembled.forEach { (course, slots) ->
            val courseId = dao.insertCourse(course)
            if (slots.isNotEmpty()) dao.insertSlots(slots.map { it.copy(courseId = courseId) })
        }
        dao.insertImport(
            ImportRecord(
                fileName = fileName,
                fileHash = fileHash,
                semester = parsed.semester,
                printTime = parsed.printTime,
                slotCount = parsed.slots.size,
                courseCount = assembled.size,
                contentSignature = TimetableAssembler.signature(parsed.slots)
            )
        )
        ImportResult(
            courseCount = assembled.size,
            slotCount = parsed.slots.size,
            needsConfirmCount = parsed.slots.count { it.needsConfirm }
        )
    }

    /** 手动新增课程(含一个上课时段) */
    suspend fun addManualCourse(
        name: String,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        weeksRaw: String,
        weeksCanonical: String,
        weeksExpanded: List<Int>,
        parity: String,
        room: String?,
        teacher: String?,
        typeLabel: String? = null
    ): Long = db.withTransaction {
        val courseId = db.timetableDao().insertCourse(
            Course(
                name = name,
                typeLabel = typeLabel,
                room = room?.ifBlank { null },
                teacher = teacher?.ifBlank { null },
                manual = true
            )
        )
        db.timetableDao().insertSlots(
            listOf(
                CourseSlot(
                    courseId = courseId,
                    dayOfWeek = dayOfWeek,
                    startSection = startSection,
                    endSection = endSection,
                    weeksRaw = weeksRaw,
                    weeksCanonical = weeksCanonical,
                    weeksExpanded = weeksExpanded.joinToString(","),
                    parity = parity,
                    typeLabel = typeLabel,
                    teacher = teacher?.ifBlank { null },
                    room = room?.ifBlank { null }
                )
            )
        )
        courseId
    }

    /** 编辑课程基础信息 */
    suspend fun updateCourseBasic(course: Course, name: String, room: String?, teacher: String?) {
        db.timetableDao().updateCourse(
            course.copy(
                name = name.trim(),
                room = room?.trim()?.ifBlank { null },
                teacher = teacher?.trim()?.ifBlank { null },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 整体替换某课程的时段(编辑/删除时段后重写,避免残留脏数据) */
    suspend fun replaceSlots(courseId: Long, slots: List<CourseSlot>) = db.withTransaction {
        db.timetableDao().deleteSlotsOfCourse(courseId)
        if (slots.isNotEmpty()) {
            db.timetableDao().insertSlots(slots.map { it.copy(id = 0, courseId = courseId) })
        }
    }

    suspend fun deleteCourse(courseId: Long) = db.withTransaction {
        db.timetableDao().deleteCourse(courseId)
    }

    /** 清空课表:仅清导入部分(保留手动课程) */
    suspend fun clearImported() = db.withTransaction {
        db.timetableDao().deleteImportedCourses()
        db.timetableDao().clearImportRecords()
    }

    /** 清空全部课程(含手动添加) */
    suspend fun clearAll() = db.withTransaction {
        db.timetableDao().deleteAllCourses()
        db.timetableDao().clearImportRecords()
    }
}
