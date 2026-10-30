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
     * 首次启动且库为空时写入一条示例数据。
     * 示例数据 isSample = true,UI 明确标注,用户可删除;
     * 一旦用户产生了自己的数据(count > 0)则不再写入。
     */
    suspend fun seedSampleIfNeeded() {
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
    }
}

/**
 * 简单服务定位器:单模块小应用,不引入 DI 框架
 */
object Graph {
    @Volatile
    private var repo: KebanRepo? = null

    fun init(context: Context): KebanRepo =
        repo ?: synchronized(this) {
            KebanRepo(AppDatabase.get(context)).also { repo = it }
        }

    fun repo(): KebanRepo =
        repo ?: error("Graph 未初始化,请先在 MainActivity 调用 init")
}
