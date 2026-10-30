package com.wmu.keban.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 待办任务实体(第一阶段:手动添加;后续阶段扩展同步来源)
 */
@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val courseName: String? = null,
    val note: String? = null,
    /** 截止时间(epoch millis);null = 未设置,绝不猜测填充 */
    val dueAt: Long? = null,
    val done: Boolean = false,
    /** 示例数据标记:UI 中明确标注"示例",与真实数据区分 */
    val isSample: Boolean = false,
    /** 来源:manual(手动) / pdf(课表解析) / 未来:chaoxing、zhidao、zzaixue */
    val source: String = "manual",
    /** 来源链接(如教务/平台作业地址),手动添加时可为空 */
    val sourceUrl: String? = null,
    /** 最后同步时间(epoch millis),手动任务为 null */
    val lastSyncAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
