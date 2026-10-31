package com.wmu.keban.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/**
 * 数据仓库:封装 DAO,供 UI 层调用
 */
class KebanRepo internal constructor(private val db: AppDatabase) {

    val allTasks: Flow<List<TaskItem>> = db.taskDao().observeAll()
    val upcomingTasks: Flow<List<TaskItem>> = db.taskDao().observeUpcoming()

    suspend fun addTask(
        title: String,
        courseName: String?,
        note: String?,
        dueAt: Long?
    ): Long = db.taskDao().insert(
        TaskItem(
            title = title,
            courseName = courseName?.trim()?.ifEmpty { null },
            note = note?.trim()?.ifEmpty { null },
            dueAt = dueAt,
            source = "manual"
        )
    )

    /** 编辑已有任务(保留原 done/isSample/source 等字段) */
    suspend fun updateTaskContent(
        task: TaskItem,
        title: String,
        courseName: String?,
        note: String?,
        dueAt: Long?
    ) {
        db.taskDao().update(
            task.copy(
                title = title.trim(),
                courseName = courseName?.trim()?.ifEmpty { null },
                note = note?.trim()?.ifEmpty { null },
                dueAt = dueAt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun toggleDone(task: TaskItem) =
        db.taskDao().update(task.copy(done = !task.done, updatedAt = System.currentTimeMillis()))

    suspend fun deleteTask(task: TaskItem) = db.taskDao().delete(task)

    /** 学期起始日(epochDay);null = 未设置 */
    val semesterStart: Flow<Long?> =
        db.settingDao().observe(KEY_SEMESTER_START).map { it?.value?.toLongOrNull() }

    suspend fun setSemesterStart(epochDay: Long) {
        db.settingDao().put(Setting(KEY_SEMESTER_START, epochDay.toString()))
    }

    /**
     * 节次时间表(第 n 节 → 开始/结束钟点)。
     * 未设置过即为空表 —— 界面据此显示"时间未设置",不猜测具体钟点。
     */
    val sectionTimes: Flow<com.wmu.keban.util.SectionTimes.Table> =
        db.settingDao().observe(KEY_SECTION_TIMES)
            .map { com.wmu.keban.util.SectionTimes.parse(it?.value) }

    suspend fun setSectionTimes(table: com.wmu.keban.util.SectionTimes.Table) {
        db.settingDao().put(
            Setting(KEY_SECTION_TIMES, com.wmu.keban.util.SectionTimes.serialize(table))
        )
    }

    /**
     * 示例数据只在首次启动写入一次(用 settings 里的标记位记录),
     * 避免"用户删掉示例后重启又被写回来"的行为缺陷。
     * 示例数据 isSample = true,UI 明确标注,可删除;已有真实数据时不再写入。
     */
    suspend fun seedSampleIfNeeded() {
        if (db.settingDao().get(KEY_SAMPLE_SEEDED) != null) return
        // 先记标记,再决定是否插入,保证只执行一次
        db.settingDao().put(Setting(KEY_SAMPLE_SEEDED, "1"))
        if (db.taskDao().count() > 0) return
        val due = LocalDate.now().plusDays(2).atTime(18, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        db.taskDao().insert(
            TaskItem(
                title = "预习寄生虫学第一讲",
                courseName = "寄生虫学",
                note = "示例数据:仅用于演示界面,可随时删除。",
                dueAt = due,
                isSample = true
            )
        )
    }

    companion object {
        const val KEY_SEMESTER_START = "semester_start_epoch_day"
        const val KEY_SAMPLE_SEEDED = "sample_seeded"
        /** 节次时间表,格式 `1=08:00-08:45;2=08:55-09:40` */
        const val KEY_SECTION_TIMES = "section_times"
    }
}

/**
 * 简单服务定位器:单模块小应用,不引入 DI 框架
 */
object Graph {
    @Volatile
    private var repo: KebanRepo? = null

    @Volatile
    private var timetable: TimetableRepo? = null

    fun init(context: Context): KebanRepo =
        repo ?: synchronized(this) {
            val db = AppDatabase.get(context)
            timetable = TimetableRepo(db)
            KebanRepo(db).also { repo = it }
        }

    fun repo(): KebanRepo =
        repo ?: error("Graph 未初始化,请先在 MainActivity 调用 init")

    fun timetableRepo(): TimetableRepo =
        timetable ?: error("Graph 未初始化,请先在 MainActivity 调用 init")
}
