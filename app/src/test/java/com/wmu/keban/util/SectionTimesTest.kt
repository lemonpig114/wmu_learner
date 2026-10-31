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
}
