package com.wmu.keban.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 日期工具:温医大暂定默认学期起始日(9 月 7 日)与教学周推算。
 * 这些日期是把课表自动定位到"当前周"的依据,所以要锁死。
 */
class DatesTest {

    @Test
    fun `2026年9月7日确实是周一`() {
        assertEquals(DayOfWeek.MONDAY, LocalDate.of(2026, 9, 7).dayOfWeek)
    }

    @Test
    fun `默认学期起始日取距今最近的 9月7日`() {
        // 开学后:用本年 9/7
        assertEquals(LocalDate.of(2026, 9, 7), Dates.defaultSemesterStart(LocalDate.of(2026, 9, 7)))
        assertEquals(LocalDate.of(2026, 9, 7), Dates.defaultSemesterStart(LocalDate.of(2026, 10, 31)))
        assertEquals(LocalDate.of(2026, 9, 7), Dates.defaultSemesterStart(LocalDate.of(2027, 1, 20)))
    }

    @Test
    fun `开学前一天应指向即将开始的 9月7日 且教学周为 0`() {
        val start = Dates.defaultSemesterStart(LocalDate.of(2026, 9, 6))
        assertEquals(LocalDate.of(2026, 9, 7), start)
        // 尚未开学:teachingWeek 返回 0,界面显示"学期尚未开始",而不是负周次
        assertEquals(0, Dates.teachingWeek(start.toEpochDay(), LocalDate.of(2026, 9, 6)))
    }

    @Test
    fun `学年早已结束时改用下一学年 避免出现荒唐周次`() {
        // 2027-07-01:上一学年 2026-09-07 已过 297 天(>30 周)⇒ 改用 2027-09-07
        val start = Dates.defaultSemesterStart(LocalDate.of(2027, 7, 1))
        assertEquals(LocalDate.of(2027, 9, 7), start)
        assertEquals(0, Dates.teachingWeek(start.toEpochDay(), LocalDate.of(2027, 7, 1)))
    }

    @Test
    fun `从 9月7日 起教学周按周一递增`() {
        val start = LocalDate.of(2026, 9, 7).toEpochDay()
        assertEquals(1, Dates.teachingWeek(start, LocalDate.of(2026, 9, 7)))
        assertEquals(1, Dates.teachingWeek(start, LocalDate.of(2026, 9, 13))) // 周日仍属第 1 周
        assertEquals(2, Dates.teachingWeek(start, LocalDate.of(2026, 9, 14))) // 周一进入第 2 周
        assertEquals(8, Dates.teachingWeek(start, LocalDate.of(2026, 10, 31)))
        assertEquals(8, Dates.teachingWeek(start, LocalDate.of(2026, 11, 1)))
        assertEquals(9, Dates.teachingWeek(start, LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun `未设置学期起始日时教学周为 null`() {
        assertEquals(null, Dates.teachingWeek(null, LocalDate.of(2026, 10, 31)))
    }
}
