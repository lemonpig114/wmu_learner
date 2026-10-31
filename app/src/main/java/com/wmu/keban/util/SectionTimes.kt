package com.wmu.keban.util

/**
 * 节次时间表:第 n 节 → (开始, 结束),形如 "08:00" 与 "08:45"。
 *
 * 设计原则(与课表解析一致):**没有设置过的节次不猜测**。真实课表 PDF 里只给了节次区间
 * (如 1-2 节、18-20 节),没有给具体钟点,所以具体时间必须由用户自己填;
 * 未填写时节次只显示"第 x-y 节",不显示任何时间。
 *
 * 存储格式(settings 表的一个 key):
 *   `1=08:00-08:45;2=08:55-09:40`  —— 只存用户填过的节次。
 */
object SectionTimes {

    /** 与课表网格一致的最大节次(实测该校课表为 1~22 节) */
    const val MAX_SECTION = 22

    typealias Table = Map<Int, Pair<String, String>>

    private val TIME_RE = Regex("""\d{1,2}:\d{2}""")

    fun isTime(t: String): Boolean = TIME_RE.matches(t.trim())

    /** 解析 `1=08:00-08:45;2=08:55-09:40`;非法片段单条丢弃,不影响其余 */
    fun parse(raw: String?): Table {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = linkedMapOf<Int, Pair<String, String>>()
        raw.split(';').forEach { part ->
            val idx = part.substringBefore('=', "").trim().toIntOrNull() ?: return@forEach
            if (idx !in 1..MAX_SECTION) return@forEach
            val range = part.substringAfter('=', "")
            val s = range.substringBefore('-').trim()
            val e = range.substringAfter('-', "").trim()
            if (isTime(s) && isTime(e)) out[idx] = s to e
        }
        return out
    }

    fun serialize(table: Table): String =
        table.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value.first}-${it.value.second}" }

    /**
     * 多行文本 ⇄ 表。每行形如 `1 08:00-08:45`(节次与时间之间用空格或制表符分隔)。
     * 空行与 # 开头的注释行忽略。
     */
    fun fromLines(text: String): Table {
        val out = linkedMapOf<Int, Pair<String, String>>()
        text.lines().forEach { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@forEach
            val parts = t.split(Regex("\\s+"))
            if (parts.size < 2) return@forEach
            val idx = parts[0].trimEnd('节', '.').toIntOrNull() ?: return@forEach
            if (idx !in 1..MAX_SECTION) return@forEach
            val range = parts[1]
            val s = range.substringBefore('-').trim()
            val e = range.substringAfter('-', "").trim()
            if (isTime(s) && isTime(e)) out[idx] = s to e
        }
        return out
    }

    fun toLines(table: Table): String =
        table.toSortedMap().entries.joinToString("\n") { "${it.key} ${it.value.first}-${it.value.second}" }

    /**
     * 起止节次的时间区间文本,如 "08:00-09:40"。
     * 起始节与结束节**都必须**已设置,否则返回 null —— 不拿邻近节次的时间凑数。
     */
    fun range(table: Table, startSection: Int, endSection: Int): String? {
        val s = table[startSection]?.first ?: return null
        val e = table[endSection]?.second ?: return null
        return "$s-$e"
    }

    /** 节次文本:"第1-2节" / "第3节" */
    fun sectionText(startSection: Int, endSection: Int): String =
        if (startSection == endSection) "第${startSection}节" else "第${startSection}-${endSection}节"
}
