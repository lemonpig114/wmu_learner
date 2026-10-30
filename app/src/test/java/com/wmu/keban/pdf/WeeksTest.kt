package com.wmu.keban.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 周次表达式解析测试(纯逻辑,使用真实 PDF 中实测出现的表达式)
 */
class WeeksTest {

    @Test
    fun singleWeek() {
        val r = Weeks.parse("第1周")!!
        assertEquals(listOf(1), r.expanded)
        assertEquals("1", r.canonical)
        assertEquals("全", r.parity)
        assertEquals(listOf(13), Weeks.parse("第13周")!!.expanded)
    }

    @Test
    fun rangesWithGaps() {
        val r = Weeks.parse("1-4周,6-10周,12周")!!
        assertEquals(listOf(1, 2, 3, 4, 6, 7, 8, 9, 10, 12), r.expanded)
        assertEquals("1-4,6-10,12", r.canonical)
        assertEquals("全", r.parity)
    }

    @Test
    fun evenWeeks() {
        val r = Weeks.parse("10-12周(双)")!!
        assertEquals(listOf(10, 12), r.expanded)
        assertEquals("10-12(双)", r.canonical)
        assertEquals("双", r.parity)
    }

    @Test
    fun mixedOddEvenList() {
        val r = Weeks.parse("13-15周(单),16-17周")!!
        assertEquals(listOf(13, 15, 16, 17), r.expanded)
        assertEquals("13-15(单),16-17", r.canonical)
        assertEquals("混合", r.parity)
    }

    @Test
    fun otherRealForms() {
        assertEquals(listOf(1, 13), Weeks.parse("1周,13周")!!.expanded)
        // "4-6周(双)" = 第4~6周中的双周 → 4、6(实测表达式,注意不含 5)
        assertEquals(listOf(4, 6, 11, 16), Weeks.parse("4-6周(双),11周,16周")!!.expanded)
        assertEquals(listOf(5, 7, 16), Weeks.parse("5-7周(单),16周")!!.expanded)
        assertEquals(listOf(2, 13), Weeks.parse("2周,13周")!!.expanded)
    }

    @Test
    fun unparseableReturnsNullInsteadOfGuessing() {
        assertNull(Weeks.parse("周次待定"))
        assertNull(Weeks.parse(""))
        assertNull(Weeks.parse("待安排"))
        assertNull(Weeks.parse("1-4周,待定"))
    }
}
