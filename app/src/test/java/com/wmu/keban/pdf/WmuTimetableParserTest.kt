package com.wmu.keban.pdf

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * 用**真实课表 PDF** 验证解析结果。
 * 样例路径来自本机 local.properties(samplePdf,不入 Git),文件缺失时跳过。
 * 断言全部依据真实文件的实测事实,同时含隐私回归断言。
 */
class WmuTimetableParserTest {

    private fun sample(): File? {
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

    @Test
    fun parsesRealWmuTimetable() {
        val pdf = sample()
        assumeTrue("样例 PDF 不存在,跳过", pdf != null && pdf.isFile)
        if (pdf == null) return

        val parsed = WmuTimetableParser.parse(DesktopPdfTextExtractor.extract(pdf))

        println("学期=${parsed.semester}  打印时间=${parsed.printTime}")
        println("解析条目数=${parsed.slots.size}  待确认=${parsed.slots.count { it.needsConfirm }}")
        println("实践课程=${parsed.practicalCourses}")
        parsed.slots.take(10).forEach {
            println("  ${it.dayOfWeek}周 ${it.startSection}-${it.endSection}节 ${it.courseName}${it.marker ?: ""} [${it.weeksCanonical}] 场地=${it.room} 教师=${it.teacher}")
        }

        // 1) 整体规模:独立核对(统计转储中 "(a-b节)" 注记)为 52 个格子,作为精确回归网
        assertTrue("应解析出 52 个上课时段,实得 ${parsed.slots.size}", parsed.slots.size == 52)

        // 2) 寄生虫学:周一 1-2 节,第 1 周,场地 7AJ101
        assertTrue(
            "寄生虫学 周一1-2节 第1周 7AJ101 未解析到",
            parsed.slots.any {
                it.courseName == "寄生虫学" && it.dayOfWeek == 1 &&
                    it.startSection == 1 && it.endSection == 2 &&
                    it.weeksExpanded == listOf(1) && it.room == "7AJ101"
            }
        )

        // 3) 医学遗传学:周六 1-2 节,10-12周(双) → 展开 [10,12],不含 11
        assertTrue(
            "医学遗传学 周六 10-12周(双) 解析不正确",
            parsed.slots.any {
                it.courseName == "医学遗传学" && it.dayOfWeek == 6 &&
                    it.startSection == 1 && it.endSection == 2 &&
                    it.weeksExpanded == listOf(10, 12) && it.parity == "双"
            }
        )

        // 4) 跨节 + 场地未排:生物化学▲(在线) 周四 18-20 节 第 9 周 → 应标记待确认
        assertTrue(
            "生物化学 周四 18-20节 第9周(场地未排) 解析不正确",
            parsed.slots.any {
                it.courseName == "生物化学" && it.dayOfWeek == 4 &&
                    it.startSection == 18 && it.endSection == 20 &&
                    it.weeksExpanded == listOf(9) && it.needsConfirm
            }
        )

        // 5) 一门课多个时段:寄生虫学应在多个不同(星期,节次)出现
        val parasiteSpans = parsed.slots.filter { it.courseName == "寄生虫学" }
            .map { it.dayOfWeek to it.startSection }.distinct()
        assertTrue("寄生虫学应有多个上课时段,实得 $parasiteSpans", parasiteSpans.size >= 2)

        // 6) 表外实践课程行
        assertTrue("未解析出实践课程行", parsed.practicalCourses.any { it.contains("全球精神健康") })

        // 7) 学期信息
        assertTrue("未解析出学期", parsed.semester?.contains("2026-2027") == true)

        // 8) 隐私回归:解析结果不得包含身份信息(学号)与标题行(姓名课表)
        assertTrue("解析结果不应包含学号信息", parsed.slots.none { it.rawCell.contains("学号") })
        assertTrue("解析结果不应包含标题行(姓名课表)", parsed.slots.none { it.rawCell.contains("课表") })
    }

    /** 跨学期样本冒烟:验证解析器不依赖某一学期的具体内容 */
    @Test
    fun parsesOtherSemesterSample() {
        val candidates = listOf("local.properties", "../local.properties", "../../local.properties")
        var second: File? = null
        for (c in candidates) {
            val f = File(c)
            if (f.isFile) {
                val props = Properties()
                f.reader(Charsets.UTF_8).use { props.load(it) }
                props.getProperty("samplePdf2")?.let { second = File(it) }
                break
            }
        }
        val pdf = second
        assumeTrue("第二个样例不存在,跳过", pdf != null && pdf.isFile)
        if (pdf == null) return

        val parsed = WmuTimetableParser.parse(DesktopPdfTextExtractor.extract(pdf))
        println("[跨学期样本] 学期=${parsed.semester} 条目数=${parsed.slots.size} 实践课程=${parsed.practicalCourses.size}")
        assertTrue("跨学期样本应解析出条目", parsed.slots.size >= 10)
        assertTrue("跨学期样本应识别学期", parsed.semester?.contains("2025-2026") == true)
        assertTrue("跨学期样本不应包含身份信息", parsed.slots.none { it.rawCell.contains("学号") })
    }
}
