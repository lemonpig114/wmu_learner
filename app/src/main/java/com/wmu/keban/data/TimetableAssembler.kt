package com.wmu.keban.data

import com.wmu.keban.pdf.ParsedSlot
import com.wmu.keban.pdf.ParsedTimetable
import java.security.MessageDigest

/**
 * 解析结果 → 数据库实体的装配(纯逻辑,可在 JVM 单测)
 *
 * 课程归并规则:同名课程归为一门课,其多个单元格成为多个上课时段;
 * 类型(讲课/实验/讨论…)、教师、场地按**时段**分别保存(真实文件中同一门课不同时段的
 * 教师/场地/形式确实可能不同,如"生物化学◆讲课(张伟)"与"生物化学◇实验(孙委委)")。
 */
object TimetableAssembler {

    /** 解析结果 → (课程, 时段) 列表;course.id 由数据库分配 */
    fun toCourses(parsed: ParsedTimetable): List<Pair<Course, List<CourseSlot>>> =
        toCourses(parsed.slots)

    fun toCourses(slots: List<ParsedSlot>): List<Pair<Course, List<CourseSlot>>> {
        val byName = slots.groupBy { it.courseName }.toSortedMap()
        return byName.map { (name, group) ->
            val first = group.first()
            val course = Course(
                name = name,
                typeLabel = group.mapNotNull { it.typeLabel }.firstOrNull(),
                campus = group.mapNotNull { it.campus }.firstOrNull(),
                room = group.mapNotNull { it.room }.firstOrNull(),
                teacher = group.mapNotNull { it.teacher }.firstOrNull(),
                classGroup = group.mapNotNull { it.classGroup }.firstOrNull(),
                assessment = group.mapNotNull { it.assessment }.firstOrNull(),
                manual = false,
                needsConfirm = group.any { it.needsConfirm }
            )
            val slotEntities = group
                .sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }, { it.weeksCanonical }))
                .map { s ->
                    CourseSlot(
                        courseId = 0,
                        dayOfWeek = s.dayOfWeek,
                        startSection = s.startSection,
                        endSection = s.endSection,
                        weeksRaw = s.weeksRaw,
                        weeksCanonical = s.weeksCanonical,
                        weeksExpanded = s.weeksExpanded.joinToString(","),
                        parity = s.parity,
                        marker = s.marker?.toString(),
                        typeLabel = s.typeLabel,
                        teacher = s.teacher,
                        room = s.room,
                        needsConfirm = s.needsConfirm,
                        confirmNotes = s.confirmNotes.joinToString(";").ifEmpty { null },
                        rawCell = s.rawCell
                    )
                }
            course to slotEntities
        }
    }

    /**
     * 语义指纹:与输入顺序无关(先排序再拼接),用于识别"内容相同的重复导入"。
     * 不含文件 hash(同一课表重复导出时 hash 会变)。
     */
    fun signature(slots: List<ParsedSlot>): String {
        val canonical = slots
            .sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }, { it.endSection }, { it.courseName }))
            .joinToString(";") {
                listOf(
                    it.courseName, it.dayOfWeek, it.startSection, it.endSection,
                    it.weeksCanonical, it.room ?: "", it.teacher ?: "", it.typeLabel ?: ""
                ).joinToString("|")
            }
        return sha256(canonical)
    }

    fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
