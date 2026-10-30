package com.wmu.keban.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * 课程(一门课一条;上课时段见 [CourseSlot],一门课可有多个时段)
 */
@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 类型符号聚合值:◆讲课 ◇实验 ●讨论 ▲在线 □自主学习 #实践 */
    val typeLabel: String? = null,
    val campus: String? = null,
    val room: String? = null,
    val teacher: String? = null,
    val classGroup: String? = null,
    val assessment: String? = null,
    /** true = 手动添加(重新导入课表时保留) */
    val manual: Boolean = false,
    val needsConfirm: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 上课时段(课表单元格:星期 + 起止节次 + 周次)
 */
@Entity(
    tableName = "course_slots",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("courseId")]
)
data class CourseSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    /** 1=周一 .. 7=周日 */
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    /** 周次原文(可追溯) */
    val weeksRaw: String,
    /** 规范化周次,如 "1-4,6-10,12" */
    val weeksCanonical: String,
    /** 展开周次 CSV,如 "1,2,3,4,6" */
    val weeksExpanded: String,
    /** 全/单/双/混合 */
    val parity: String,
    /** 该时段类型符号与形式(同一门课不同时段可能不同,如 讲课/实验) */
    val marker: String? = null,
    val typeLabel: String? = null,
    val teacher: String? = null,
    val room: String? = null,
    val needsConfirm: Boolean = false,
    val confirmNotes: String? = null,
    /** 单元格原文(核对与追溯) */
    val rawCell: String? = null
)

/**
 * 导入记录:用于"重复导入"识别。
 * 注意:同一课表多次导出时文件 MD5 会不同(打印时间差异),因此除文件 hash 外
 * 另存**解析结果语义指纹** contentSignature,内容相同即视为重复导入。
 */
@Entity(tableName = "import_records")
data class ImportRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    /** SHA-256(仅作提示) */
    val fileHash: String,
    val semester: String? = null,
    val printTime: String? = null,
    val slotCount: Int,
    val courseCount: Int,
    /** 解析结果语义指纹 */
    val contentSignature: String,
    val importedAt: Long = System.currentTimeMillis()
)

/** 课程 + 其全部时段(查询用) */
data class CourseWithSlots(
    @Embedded val course: Course,
    @Relation(parentColumn = "id", entityColumn = "courseId")
    val slots: List<CourseSlot>
)
