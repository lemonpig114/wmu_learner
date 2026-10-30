package com.wmu.keban.pdf

/** 一条课表条目(一门课的一个上课时段) */
data class ParsedSlot(
    val courseName: String,
    /** 类型符号:◆讲课 ◇实验 ●讨论 ▲在线 □自主学习 #实践 */
    val marker: Char?,
    val typeLabel: String?,
    /** 1=周一 .. 7=周日 */
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    /** 周次原文(便于核对) */
    val weeksRaw: String,
    /** 规范化周次,如 "1-4,6-10,12" */
    val weeksCanonical: String,
    /** 展开的周次列表 */
    val weeksExpanded: List<Int>,
    /** 单双周摘要:全/单/双/混合 */
    val parity: String,
    val campus: String?,
    val room: String?,
    val teacher: String?,
    val classGroup: String?,
    val assessment: String?,
    /** 是否存在需用户确认的信息 */
    val needsConfirm: Boolean,
    /** 待确认原因(人话) */
    val confirmNotes: List<String>,
    /** 单元格原文(核对与追溯用) */
    val rawCell: String
)

/** 一份课表的解析结果 */
data class ParsedTimetable(
    val slots: List<ParsedSlot>,
    /** 表外"实践课程"行原文(未排入格子) */
    val practicalCourses: List<String>,
    /** 学期,如 "2026-2027学年第1学期" */
    val semester: String?,
    /** 打印时间(页脚) */
    val printTime: String?,
    /** 解析过程中的提示(非致命) */
    val warnings: List<String>
)
