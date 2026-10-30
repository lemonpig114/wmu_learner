package com.wmu.keban.pdf

import kotlin.math.abs

/**
 * 温州医科大学教务课表 PDF 解析器(纯 Kotlin:输入为坐标化文本片段,便于 JVM 单测)
 *
 * 版面组织(依据真实 PDF 的结构转储实测,非假设):
 * 1. 每页 MediaBox 为竖向 A4(595x842)且旋转 90°;pdfbox 的 xDirAdj/yDirAdj 已按旋转校正,
 *    可直接使用:x 向右、y 向下。
 * 2. 表头(时间段|节次|星期一..星期日)**仅在首页出现一次**,其余页不重复 → 所有页共用同一套列坐标,
 *    可把各页按星期列纵向拼接后统一切分单元格。
 * 3. 单元格文字 x 与表头标签 x 有固定小偏移(实测 -28.9pt),故按"最近表头锚点"归位星期列;
 *    最左控制列(时间段/节次)以 x < 首列锚点-45 排除。
 * 4. 单元格第 1 行为"课程名+类型符号",第 2 行以 "(a-b节)" 开头 → 节次直接取注记,
 *    不依赖行高(该校课表行高随内容自适应,不能按等距行推节次)。
 * 5. 行高自适应导致单元格可跨页续行 → 按列拼接行序列后切分,续行自动并入同一单元格。
 * 6. 表外另有"实践课程:"行;页脚为类型符号图例 + 打印时间,单独处理。
 *
 * 隐私:首页标题带(姓名/学号)位于表头之上,统一按 y 过滤丢弃,解析结果不含任何身份信息。
 *
 * 无法确定的信息一律计入 needsConfirm/confirmNotes 交给用户确认,**不猜测填充**。
 */
object WmuTimetableParser {

    private val DAY_NAMES = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

    /** 类型符号 → 教学形式(与 PDF 页脚图例一致) */
    private val MARKERS = mapOf(
        '◆' to "讲课", '◇' to "实验", '●' to "讨论", '▲' to "在线", '□' to "自主学习", '#' to "实践"
    )

    private val FIELD_RE = Regex(
        "/(校区|场地|教学班组成|教学班|教师|考核方式|选课备注|课程学时组成|周学时|总学时|学分):([^/]*)"
    )
    private val SPAN_RE = Regex("^\\((\\d{1,2})\\s*[-–~]\\s*(\\d{1,2})\\s*节\\)")

    private data class LineToken(val text: String, val x: Float)
    private data class TextLine(val page: Int, val y: Float, val tokens: List<LineToken>)
    private data class RawCell(val day: Int, val nameRow: String, val detail: String, val raw: String)

    fun parse(result: ExtractionResult): ParsedTimetable {
        val lines = groupLines(result.runs)
        val warnings = mutableListOf<String>()

        // ── 表头:定位星期列锚点 ──
        val header = lines.firstOrNull { l -> DAY_NAMES.all { d -> l.tokens.any { it.text == d } } }
            ?: return ParsedTimetable(
                emptyList(), emptyList(), null, null,
                listOf("未找到表头(星期一~星期日),可能不是教务课表 PDF")
            )
        val anchors: List<Pair<Int, Float>> = DAY_NAMES.mapIndexed { i, d ->
            (i + 1) to header.tokens.first { it.text == d }.x
        }
        val leftBound = anchors.first().second - 45f
        val spacing = if (anchors.size > 1) abs(anchors[1].second - anchors[0].second) else 104f

        var semester: String? = null
        var printTime: String? = null
        val practical = mutableListOf<String>()

        // day -> [(page, y, text)]
        val byDay = mutableMapOf<Int, MutableList<Triple<Int, Float, String>>>()

        for (line in lines) {
            // 1) 表头行本身
            if (line === header) continue
            // 2) 首页表头之上为标题区(学期 + 姓名 + 学号):
            //    仅取"学期"token,其余一律丢弃 —— 隐私隔离,解析结果不含身份信息
            if (line.page == header.page && line.y < header.y) {
                line.tokens.firstOrNull { Regex("^\\d{4}-\\d{4}学年").containsMatchIn(it.text) }
                    ?.let { semester = it.text.trim() }
                continue
            }
            // 3) 页脚(图例 + 打印时间)
            if (line.tokens.any { it.text.contains("打印时间") }) {
                printTime = line.tokens.first { it.text.contains("打印时间") }
                    .text.substringAfter("打印时间:").trim().trimEnd()
                continue
            }
            val whole = line.tokens.joinToString("") { it.text }
            // 4) 表外"实践课程"行
            if (whole.contains("实践课程")) {
                practical += whole.trim()
                continue
            }
            // 5) 图例行(□: 自主学习…)
            if (Regex("^[□▲◆◇●#]\\s*:").containsMatchIn(whole.trim())) continue

            // 7) 其余按星期列归位
            for (tok in line.tokens) {
                if (tok.x < leftBound) continue // 时间段/节次控制列
                val day = nearestDay(anchors, spacing, tok.x) ?: continue
                byDay.getOrPut(day) { mutableListOf() }.add(Triple(line.page, line.y, tok.text))
            }
        }

        // ── 按列纵向拼接(跨页续行自动合并),再切分单元格 ──
        val cells = mutableListOf<RawCell>()
        for (day in 1..7) {
            val rows = byDay[day]
                ?.sortedWith(compareBy({ it.first }, { it.second }))
                ?.map { it.third }
                ?.filter { it.isNotBlank() }
                ?: continue
            if (rows.isEmpty()) continue
            cells += segmentCells(day, rows, warnings)
        }

        return ParsedTimetable(
            slots = cells.map { buildSlot(it, warnings) },
            practicalCourses = practical,
            semester = semester,
            printTime = printTime,
            warnings = warnings
        )
    }

    // ───────────────────────── 内部实现 ─────────────────────────

    /** 把字符级片段按 (页 → y → x) 聚合成行,行内相邻片段(间距<=2pt)合并 */
    private fun groupLines(runs: List<TextRun>): List<TextLine> {
        val out = mutableListOf<TextLine>()
        runs.groupBy { it.page }.toSortedMap().forEach { (page, pageRuns) ->
            val sorted = pageRuns.sortedWith(compareBy({ it.y }, { it.x }))
            val buckets = mutableListOf<MutableList<TextRun>>()
            for (r in sorted) {
                val cur = buckets.lastOrNull()
                if (cur != null && abs(cur.first().y - r.y) <= maxOf(2f, r.height * 0.6f)) cur.add(r)
                else buckets.add(mutableListOf(r))
            }
            buckets.forEach { b ->
                val rs = b.sortedBy { it.x }
                val toks = mutableListOf<LineToken>()
                var acc: StringBuilder? = null
                var accStartX = 0f
                var accEndX = 0f
                fun flush() {
                    val s = acc?.toString()
                    if (s != null && s.isNotBlank()) toks += LineToken(s, accStartX)
                    acc = null
                }
                for (r in rs) {
                    val gap = if (acc == null) 0f else r.x - accEndX
                    if (acc != null && gap <= 2.0f) {
                        acc!!.append(r.text)
                    } else {
                        flush()
                        acc = StringBuilder(r.text)
                        accStartX = r.x
                    }
                    accEndX = r.x + r.width
                }
                flush()
                if (toks.isNotEmpty()) out += TextLine(page, b.first().y, toks)
            }
        }
        return out
    }

    /** 按"最近表头锚点"把 x 归到星期列(容差约半个列宽) */
    private fun nearestDay(anchors: List<Pair<Int, Float>>, spacing: Float, x: Float): Int? {
        val best = anchors.minByOrNull { abs(it.second - x) } ?: return null
        return if (abs(best.second - x) <= spacing * 0.62f) best.first else null
    }

    /**
     * 切分单元格:名称行的判定标准 = 其下一行以 "(a-b节)" 开头。
     * 名称行与其后所有续行(直到下一个名称行)同属一个单元格。
     */
    private fun segmentCells(day: Int, rows: List<String>, warnings: MutableList<String>): List<RawCell> {
        val out = mutableListOf<RawCell>()
        var nameRow: String? = null
        var detail = mutableListOf<String>()

        fun close() {
            val n = nameRow
            if (n != null) {
                val d = detail.joinToString("")
                out += RawCell(day, n, d, "$n $d".trim())
            }
            nameRow = null
            detail = mutableListOf()
        }

        var i = 0
        while (i < rows.size) {
            val row = rows[i]
            val next = rows.getOrNull(i + 1)
            if (next != null && SPAN_RE.containsMatchIn(next.trim())) {
                close()
                nameRow = row.trim()
                detail = mutableListOf(next.trim())
                i += 2
            } else if (nameRow == null) {
                warnings += "第${day}列有无法归属的文本,已跳过:\"${row.take(20)}\""
                i += 1
            } else {
                detail += row
                i += 1
            }
        }
        close()
        return out
    }

    /** 解析单元格 → 课表条目 */
    private fun buildSlot(cell: RawCell, warnings: MutableList<String>): ParsedSlot {
        var name = cell.nameRow.trim()
        var marker: Char? = null
        if (name.isNotEmpty() && MARKERS.containsKey(name.last())) {
            marker = name.last()
            name = name.dropLast(1).trim()
        }

        val spanM = SPAN_RE.find(cell.detail)
        val startSection = spanM?.groupValues?.get(1)?.toIntOrNull() ?: 0
        var endSection = spanM?.groupValues?.get(2)?.toIntOrNull() ?: startSection
        if (endSection < startSection) endSection = startSection

        val afterSpan = if (spanM != null) cell.detail.substring(spanM.range.last + 1) else cell.detail
        val weeksPart = afterSpan.substringBefore('/').trim()
        val weeks = Weeks.parse(weeksPart)

        val fields = mutableMapOf<String, String>()
        FIELD_RE.findAll(afterSpan).forEach { m -> fields[m.groupValues[1]] = m.groupValues[2].trim() }

        val notes = mutableListOf<String>()
        if (spanM == null) notes += "未能识别节次"
        if (weeks == null) notes += "未能识别周次(\"$weeksPart\")"

        val room = fields["场地"]?.takeIf { it.isNotEmpty() }
        when {
            room == null -> notes += "未提供场地"
            room.contains("未排") || room.contains("未安排") -> notes += "场地未排定(以教务为准)"
        }
        val assessment = fields["考核方式"]?.takeIf { it.isNotEmpty() }
        if (assessment != null && assessment.contains("未安排")) notes += "考核方式:未安排"

        if (name.isEmpty()) {
            notes += "未能识别课程名"
            warnings += "存在未识别课程名的单元格"
        }

        return ParsedSlot(
            courseName = name,
            marker = marker,
            typeLabel = marker?.let { MARKERS[it] },
            dayOfWeek = cell.day,
            startSection = startSection,
            endSection = endSection,
            weeksRaw = weeksPart,
            weeksCanonical = weeks?.canonical ?: weeksPart,
            weeksExpanded = weeks?.expanded ?: emptyList(),
            parity = weeks?.parity ?: "未知",
            campus = fields["校区"]?.takeIf { it.isNotEmpty() },
            room = room,
            teacher = fields["教师"]?.takeIf { it.isNotEmpty() },
            classGroup = fields["教学班组成"]?.takeIf { it.isNotEmpty() },
            assessment = assessment,
            needsConfirm = notes.isNotEmpty(),
            confirmNotes = notes,
            rawCell = cell.raw
        )
    }
}
