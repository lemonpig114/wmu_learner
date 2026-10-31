package com.wmu.keban.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节次时间表:解析/序列化/时间区间。
 * 重点验证"未设置就不猜"这条原则 —— range() 在缺任一端时必须返回 null。
 */
class SectionTimesTest {

    @Test
    fun `parse 读取合法条目并丢弃非法片段`() {
        val t = SectionTimes.parse("1=08:00-08:45;2=08:55-09:40")
        assertEquals(2, t.size)
        assertEquals("08:00" to "08:45", t[1])
        assertEquals("08:55" to "09:40", t[2])
    }

    @Test
    fun `parse 丢弃越界节次与格式错误`() {
        val t = SectionTimes.parse("0=08:00-08:45;99=09:00-09:45;3=abc;4=08:00-08:45")
        assertEquals(setOf(4), t.keys)
    }

    @Test
    fun `parse 空值返回空表`() {
        assertTrue(SectionTimes.parse(null).isEmpty())
        assertTrue(SectionTimes.parse("").isEmpty())
    }

    @Test
    fun `serialize 与 parse 往返一致`() {
        val t = mapOf(2 to ("08:55" to "09:40"), 1 to ("08:00" to "08:45"))
        val round = SectionTimes.parse(SectionTimes.serialize(t))
        assertEquals(t, round)
        // 序列化按节次排序,便于人工查看与 diff
        assertTrue(SectionTimes.serialize(t).startsWith("1="))
    }

    @Test
    fun `fromLines 支持常见书写与注释行`() {
        val text = """
            # 上午
            1 08:00-08:45
            2	08:55-09:40
            3 10:00-10:45
            不是一行合法内容
        """.trimIndent()
        val t = SectionTimes.fromLines(text)
        assertEquals(setOf(1, 2, 3), t.keys)
        assertEquals("08:00" to "08:45", t[1])
        assertEquals("08:55" to "09:40", t[2])
    }

    @Test
    fun `fromLines 与 toLines 往返一致`() {
        val t = mapOf(1 to ("08:00" to "08:45"), 3 to ("10:00" to "10:45"))
        assertEquals(t, SectionTimes.fromLines(SectionTimes.toLines(t)))
    }

    @Test
    fun `range 两端都有才返回区间`() {
        val t = SectionTimes.parse("1=08:00-08:45;2=08:55-09:40;3=10:00-10:45")
        assertEquals("08:00-09:40", SectionTimes.range(t, 1, 2))
        assertEquals("08:00-08:45", SectionTimes.range(t, 1, 1))
    }

    @Test
    fun `range 缺任一端返回 null 不猜测`() {
        val t = SectionTimes.parse("1=08:00-08:45")
        assertNull("结束节未设置应返回 null", SectionTimes.range(t, 1, 2))
        assertNull("起始节未设置应返回 null", SectionTimes.range(t, 2, 2))
        assertNull("空表应返回 null", SectionTimes.range(emptyMap(), 1, 2))
    }

    @Test
    fun `sectionText 单节与区间`() {
        assertEquals("第1节", SectionTimes.sectionText(1, 1))
        assertEquals("第1-2节", SectionTimes.sectionText(1, 2))
        assertEquals("第18-20节", SectionTimes.sectionText(18, 20))
    }

    // ───────── 按参数生成(默认 08:00 开始 / 45 分钟一节 / 休息 5 分钟) ─────────

    @Test
    fun `generate 默认参数生成连续节次(08-00 开始 每节40分钟 休息5分钟)`() {
        val t = SectionTimes.generate(totalSections = 4)
        assertEquals("08:00" to "08:40", t[1])
        assertEquals("08:45" to "09:25", t[2]) // 40 + 5 休息
        assertEquals("09:30" to "10:10", t[3])
        assertEquals("10:15" to "10:55", t[4])
    }

    @Test
    fun `generate 是等间隔的通用算法 与 WMU_DEFAULT 的大课间不同`() {
        // 通用生成器按固定间隔推进;学校实际在第 2→3 节有 15 分钟大课间,
        // 因此 WMU_DEFAULT 才是默认值,生成器只作自定义参数的辅助。
        val t = SectionTimes.generate(totalSections = 6)
        assertEquals("等间隔生成:第3节应紧接 5 分钟休息", "09:30", t[3]!!.first)
        assertEquals("学校实际:第3节 09:40", "09:40", SectionTimes.WMU_DEFAULT[3]!!.first)
    }

    @Test
    fun `generate 生成 22 节(参数允许时)且序号连续`() {
        // 每节 30 分钟 + 休息 5 分钟,22 节不会跨午夜
        val t = SectionTimes.generate(durationMinutes = 30, breakMinutes = 5)
        assertEquals(SectionTimes.MAX_SECTION, t.size)
        assertEquals((1..SectionTimes.MAX_SECTION).toList(), t.keys.toList())
    }

    @Test
    fun `generate 参数非法时返回空表不产出半成品`() {
        assertTrue(SectionTimes.generate(firstStart = "8点").isEmpty())
        assertTrue(SectionTimes.generate(firstStart = "25:00").isEmpty())
        assertTrue(SectionTimes.generate(durationMinutes = 0).isEmpty())
        assertTrue(SectionTimes.generate(breakMinutes = -1).isEmpty())
    }

    @Test
    fun `generate 结果可被 parse 往返`() {
        val t = SectionTimes.generate(totalSections = 6, firstStart = "13:30")
        assertEquals(t, SectionTimes.parse(SectionTimes.serialize(t)))
        assertEquals(t, SectionTimes.fromLines(SectionTimes.toLines(t)))
    }

    @Test
    fun `generate 不跨午夜 超出的节次不再生成`() {
        // 45+5=50 分钟一节:08:00 起最多 12 节就接近 24:00
        val t = SectionTimes.generate(totalSections = 22)
        assertTrue("不应生成到 22 节(会跨午夜),实得 ${t.size}", t.size < 22)
        assertTrue("至少应生成几节,实得 ${t.size}", t.size >= 10)
        val last = t[t.keys.max()]!!
        assertTrue("最后一节结束时间不应在凌晨:${last.second}", last.second <= "23:59")
    }

    // ───────── 温医大默认作息:用户给出的真实锚点必须精确复现 ─────────

    @Test
    fun `WMU_DEFAULT 精确复现用户给出的 9 条真实作息锚点`() {
        val t = SectionTimes.WMU_DEFAULT
        assertEquals("1-2 节", "08:00-09:25", SectionTimes.range(t, 1, 2))
        assertEquals("3-4 节", "09:40-11:05", SectionTimes.range(t, 3, 4))
        assertEquals("3-5 节", "09:40-11:50", SectionTimes.range(t, 3, 5))
        assertEquals("3-6 节", "09:40-12:35", SectionTimes.range(t, 3, 6))
        assertEquals("8-9 节", "13:30-14:55", SectionTimes.range(t, 8, 9))
        assertEquals("8-10 节", "13:30-15:40", SectionTimes.range(t, 8, 10))
        assertEquals("11-13 节", "15:45-17:55", SectionTimes.range(t, 11, 13))
        assertEquals("12-13 节", "16:30-17:55", SectionTimes.range(t, 12, 13))
        assertEquals("14-17 节", "18:20-20:55", SectionTimes.range(t, 14, 17))
    }

    @Test
    fun `WMU_DEFAULT 覆盖 1 到 17 节且序号连续`() {
        val t = SectionTimes.WMU_DEFAULT
        assertEquals((1..17).toList(), t.keys.toList())
        t.forEach { (_, se) ->
            assertTrue("时间格式应为 HH:mm:${se.first}", SectionTimes.isTime(se.first))
            assertTrue("每节开始应早于结束:${se.first}->${se.second}", se.first < se.second)
        }
    }

    @Test
    fun `WMU_DEFAULT 的节间休息符合实测规则`() {
        val t = SectionTimes.WMU_DEFAULT
        fun gap(a: Int, b: Int): Int {
            val endA = t[a]!!.second
            val startB = t[b]!!.first
            fun min(x: String) = x.substringBefore(':').toInt() * 60 + x.substringAfter(':').toInt()
            return min(startB) - min(endA)
        }
        assertEquals("1→2 应为 5 分钟", 5, gap(1, 2))
        assertEquals("2→3 应为 15 分钟大课间", 15, gap(2, 3))
        assertEquals("3→4 应为 5 分钟", 5, gap(3, 4))
        assertEquals("7→8 应为 10 分钟", 10, gap(7, 8))
        assertEquals("13→14 应为 25 分钟", 25, gap(13, 14))
    }

    @Test
    fun `WMU_DEFAULT 上午下午每节 40 分钟 晚间 35 分钟`() {
        val t = SectionTimes.WMU_DEFAULT
        fun len(s: Int): Int {
            fun min(x: String) = x.substringBefore(':').toInt() * 60 + x.substringAfter(':').toInt()
            return min(t[s]!!.second) - min(t[s]!!.first)
        }
        listOf(1, 2, 3, 8, 11, 13).forEach { assertEquals("第 $it 节应为 40 分钟", 40, len(it)) }
        listOf(14, 15, 16, 17).forEach { assertEquals("第 $it 节应为 35 分钟", 35, len(it)) }
    }

    @Test
    fun `WMU_DEFAULT 不包含未提供的 18 到 22 节`() {
        val t = SectionTimes.WMU_DEFAULT
        (18..SectionTimes.MAX_SECTION).forEach {
            assertNull("第 $it 节未提供,应保持未设置", t[it])
            assertNull("第 $it 节不应有区间", SectionTimes.range(t, it, it))
        }
    }
}
