package com.wmu.keban.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 日期工具
 * 原则:缺失的时间信息一律显示"未设置",不做猜测填充
 */
object Dates {
    private val zh = Locale.CHINA
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private val md = DateTimeFormatter.ofPattern("M月d日")
    private val ymd = DateTimeFormatter.ofPattern("yyyy年M月d日")
    private val full = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", zh)

    fun fullDate(d: LocalDate): String = d.format(full)

    /** 截止时间展示;null = 未设置 */
    fun dueText(dueAt: Long?): String {
        if (dueAt == null) return "未设置截止"
        val dt = toLocalDateTime(dueAt)
        val today = LocalDate.now()
        return when (dt.toLocalDate()) {
            today -> "今天 ${dt.format(hm)}"
            today.plusDays(1) -> "明天 ${dt.format(hm)}"
            today.minusDays(1) -> "昨天 ${dt.format(hm)}"
            else -> "${if (dt.year == today.year) dt.format(md) else dt.format(ymd)} ${dt.format(hm)}"
        }
    }

    /** 全天截止(未设具体时间,存的是当日 00:00) */
    fun dueTextDayOnly(dueAt: Long): String {
        val d = toLocalDate(dueAt)
        val today = LocalDate.now()
        return when (d) {
            today -> "今天截止"
            today.plusDays(1) -> "明天截止"
            today.minusDays(1) -> "昨天截止"
            else -> if (d.year == today.year) "${d.format(md)}截止" else "${d.format(ymd)}截止"
        }
    }

    fun isOverdue(dueAt: Long?): Boolean =
        dueAt != null && dueAt < System.currentTimeMillis()

    /** epochDay -> "2026年10月30日 星期五" */
    fun dateOnly(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(full)

    /**
     * 教学周:第几周。
     * @return null = 学期未设置;0 = 尚未开学;>=1 当前教学周
     */
    fun teachingWeek(semesterStartEpochDay: Long?, today: LocalDate = LocalDate.now()): Int? {
        if (semesterStartEpochDay == null) return null
        val start = LocalDate.ofEpochDay(semesterStartEpochDay)
        val days = ChronoUnit.DAYS.between(start, today)
        return if (days < 0) 0 else (days / 7 + 1).toInt()
    }

    fun toLocalDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()

    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun toLocalTime(millis: Long): LocalTime = toLocalDateTime(millis).toLocalTime()

    /** date + time -> epoch millis;time 为 null 视为全天(存当日 00:00) */
    fun millisOf(date: LocalDate, time: LocalTime?): Long =
        date.atTime(time ?: LocalTime.MIDNIGHT)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun startOfDayMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
