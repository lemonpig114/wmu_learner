package com.wmu.keban.data

import com.wmu.keban.pdf.DesktopPdfTextExtractor
import com.wmu.keban.pdf.ParsedSlot
import com.wmu.keban.pdf.WmuTimetableParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * 装配器测试:解析结果 → 数据库实体(课程归并 + 语义指纹)
 */
class TimetableAssemblerTest {

    private fun slot(
        name: String,
        day: Int = 1,
        start: Int = 1,
        end: Int = 2,
        weeks: String = "1-16",
        room: String? = null,
        teacher: String? = null,
        type: String? = null
    ) = ParsedSlot(
        courseName = name,
        marker = null,
        typeLabel = type,
        dayOfWeek = day,
        startSection = start,
        endSection = end,
        weeksRaw = weeks,
        weeksCanonical = weeks,
        weeksExpanded = emptyList(),
        parity = "全",
        campus = null,
        room = room,
        teacher = teacher,
        classGroup = null,
        assessment = null,
        needsConfirm = false,
        confirmNotes = emptyList(),
        rawCell = ""
    )

    @Test
    fun groupsSlotsByNameKeepingPerSlotInfo() {
        val slots = listOf(
            slot("生物化学", day = 3, start = 3, end = 5, weeks = "1-4,6-8", room = "7AJ102", teacher = "张伟", type = "讲课"),
            slot("生物化学", day = 4, start = 18, end = 20, weeks = "9", room = "未排地点", teacher = "孙委委", type = "在线"),
            slot("寄生虫学", day = 1, start = 1, end = 2, weeks = "1", room = "7AJ101", teacher = "黄慧聪", type = "讲课")
        )
        val courses = TimetableAssembler.toCourses(slots)
        assertEquals(2, courses.size)
        val biochem = courses.first { it.first.name == "生物化学" }
        assertEquals("同名课程应归并为一门课(2 个时段)", 2, biochem.second.size)
        // 同名课程不同时段的类型/教师分别保留
        assertEquals(setOf("讲课", "在线"), biochem.second.map { it.typeLabel }.toSet())
        assertEquals(setOf("张伟", "孙委委"), biochem.second.map { it.teacher }.toSet())
        // 一门课多个时段的节次保持原样(跨节 3-5 / 18-20)
        assertTrue(biochem.second.any { it.startSection == 3 && it.endSection == 5 })
        assertTrue(biochem.second.any { it.startSection == 18 && it.endSection == 20 })
    }

    @Test
    fun signatureIsOrderIndependent() {
        val a = listOf(slot("A", day = 2), slot("B", day = 3, start = 3, end = 4))
        val b = a.reversed()
        assertEquals(TimetableAssembler.signature(a), TimetableAssembler.signature(b))
    }

    @Test
    fun signatureChangesWhenContentChanges() {
        val base = listOf(slot("A", day = 2), slot("B", day = 3, start = 3, end = 4))
        val moved = listOf(slot("A", day = 5), slot("B", day = 3, start = 3, end = 4))
        val weeksChanged = listOf(slot("A", day = 2, weeks = "1-16(双)"), slot("B", day = 3, start = 3, end = 4))
        assertNotEquals(TimetableAssembler.signature(base), TimetableAssembler.signature(moved))
        assertNotEquals(TimetableAssembler.signature(base), TimetableAssembler.signature(weeksChanged))
    }

    /** 端到端:真实 PDF → 解析 → 装配 */
    @Test
    fun assemblesRealPdfIntoCourses() {
        val pdf = samplePdf() ?: return
        val parsed = WmuTimetableParser.parse(DesktopPdfTextExtractor.extract(pdf))
        val courses = TimetableAssembler.toCourses(parsed)

        val slotTotal = courses.sumOf { it.second.size }
        println("[装配] 解析时段=${parsed.slots.size} → 课程=${courses.size} 门,时段合计=$slotTotal")
        courses.sortedByDescending { it.second.size }.take(5).forEach {
            println("  ${it.first.name}: ${it.second.size} 个时段")
        }

        assertEquals("装配不得丢失时段", parsed.slots.size, slotTotal)
        assertTrue("课程数应大于 5,实得 ${courses.size}", courses.size > 5)
        // 每门课的时段 courseId 由数据库分配,装配阶段应为 0
        assertTrue(courses.all { (_, slots) -> slots.all { s -> s.courseId == 0L } })
    }

    private fun samplePdf(): File? {
        val candidates = listOf("local.properties", "../local.properties", "../../local.properties")
        System.getenv("KEBAN_SAMPLE_PDF")?.let { return File(it) }
        for (c in candidates) {
            val f = File(c)
            if (f.isFile) {
                val props = Properties()
                f.reader(Charsets.UTF_8).use { props.load(it) }
                props.getProperty("samplePdf")?.let { return File(it) }
            }
        }
        return null
    }
}
