package com.wmu.keban.pdf

/**
 * 周次表达式解析。
 *
 * 真实样例(来自温州医科大学教务课表 PDF,均已实测出现):
 *   第1周 / 第13周 / 1-4周 / 1-4周,6-10周,12周 / 10-12周(双)
 *   13-15周(单),16-17周 / 1周,13周 / 4-6周(双),11周,16周
 *
 * 规则:逗号分隔的若干"周次分量";每个分量形如 第?N周?(单|双)? 或 A-B周(单|双)?
 * 无法解析时返回 null —— 绝不猜测。
 */
object Weeks {

    /** 单个周次分量 */
    data class Component(val start: Int, val end: Int, val oddOnly: Boolean, val evenOnly: Boolean)

    data class Result(
        /** 规范化文本,如 "1-4,6-10,12" 或 "10-12(双)" */
        val canonical: String,
        /** 展开后的周次列表(升序去重) */
        val expanded: List<Int>,
        /** 单双周摘要:"全" / "单" / "双" / "混合" */
        val parity: String
    )

    private val COMPONENT = Regex(
        "第?\\s*(\\d{1,2})\\s*(?:[-–~至]\\s*(\\d{1,2}))?\\s*周?\\s*(?:[（(]\\s*([单双])\\s*[）)])?"
    )

    fun parse(expr: String): Result? {
        val text = expr.trim()
        if (text.isEmpty()) return null

        val comps = mutableListOf<Component>()
        var consumed = 0
        // 逐段扫描,要求整串都能被分量+分隔符解释,出现无法解释的字符即判定失败(不猜测)
        for (part in text.split(',', '，')) {
            val p = part.trim()
            if (p.isEmpty()) return null
            val m = COMPONENT.find(p)
            if (m == null || m.range.first != 0 || m.range.last != p.length - 1) return null
            val a = m.groupValues[1].toIntOrNull() ?: return null
            val b = m.groupValues[2].toIntOrNull() ?: a
            if (a <= 0 || b < a || b > 40) return null
            val mark = m.groupValues[3]
            comps += Component(a, b, oddOnly = mark == "单", evenOnly = mark == "双")
            consumed++
        }
        if (consumed == 0) return null

        val expanded = mutableListOf<Int>()
        comps.forEach { c ->
            for (w in c.start..c.end) {
                if (c.oddOnly && w % 2 == 0) continue
                if (c.evenOnly && w % 2 == 1) continue
                expanded += w
            }
        }
        val distinctSorted = expanded.distinct().sorted()
        if (distinctSorted.isEmpty()) return null

        val canonical = comps.joinToString(",") { c ->
            val base = if (c.start == c.end) "${c.start}" else "${c.start}-${c.end}"
            when {
                c.oddOnly -> "$base(单)"
                c.evenOnly -> "$base(双)"
                else -> base
            }
        }

        val hasOdd = comps.any { it.oddOnly }
        val hasEven = comps.any { it.evenOnly }
        val hasPlain = comps.any { !it.oddOnly && !it.evenOnly }
        // 摘要语义:
        //   全  = 没有任何单双周标记
        //   单  = 仅"单周"分量
        //   双  = 仅"双周"分量
        //   混合 = 单周/双周分量与普通分量共存,或单双周共存(如 "13-15周(单),16-17周")
        val parity = when {
            hasOdd && (hasEven || hasPlain) -> "混合"
            hasEven && hasPlain -> "混合"
            hasOdd -> "单"
            hasEven -> "双"
            else -> "全"
        }
        return Result(canonical, distinctSorted, parity)
    }
}
