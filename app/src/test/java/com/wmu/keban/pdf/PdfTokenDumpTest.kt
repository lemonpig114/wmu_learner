package com.wmu.keban.pdf

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * 结构转储测试:用真实课表 PDF 提取文本片段,按行聚合后转储坐标结构,
 * 用于**实测确认**版面组织方式(而非假设),为网格归位算法提供依据。
 *
 * 样例 PDF 路径来自本机 local.properties(samplePdf,不入 Git);
 * 文件缺失时跳过(Assume),不影响其他环境构建。
 */
class PdfTokenDumpTest {

    private fun samples(): Pair<File?, File?> {
        var p1: String? = null
        var p2: String? = null
        val candidates = listOf("local.properties", "../local.properties", "../../local.properties")
        for (c in candidates) {
            val f = File(c)
            if (f.isFile) {
                val props = Properties()
                // 注意:必须用 UTF-8 Reader,Properties.load(InputStream) 按 ISO-8859-1 解析会把中文路径读乱
                f.reader(Charsets.UTF_8).use { props.load(it) }
                p1 = props.getProperty("samplePdf")
                p2 = props.getProperty("samplePdf2")
                break
            }
        }
        System.getenv("KEBAN_SAMPLE_PDF")?.let { p1 = it }
        return Pair(p1?.let { File(it) }, p2?.let { File(it) })
    }

    /** 把片段按 (页, 行) 聚合成可读结构;相邻且间距很小的片段合并为一段 */
    private fun toLines(runs: List<TextRun>): List<Triple<Int, Float, List<TextRun>>> {
        val out = mutableListOf<Triple<Int, Float, List<TextRun>>>()
        runs.groupBy { it.page }.toSortedMap().forEach { (page, pageRuns) ->
            val byLine = mutableListOf<MutableList<TextRun>>()
            pageRuns.sortedWith(compareBy({ it.y }, { it.x })).forEach { r ->
                val cur = byLine.lastOrNull()
                if (cur != null && kotlin.math.abs(cur[0].y - r.y) <= maxOf(2.0f, r.height * 0.6f)) {
                    cur.add(r)
                } else {
                    byLine.add(mutableListOf(r))
                }
            }
            byLine.forEach { line ->
                val sorted = line.sortedBy { it.x }
                val merged = mutableListOf<TextRun>()
                var acc: StringBuilder? = null
                var accStart: TextRun? = null
                var accEndX = 0f
                fun flush() {
                    val s = acc?.toString()
                    val st = accStart
                    if (s != null && st != null && s.isNotBlank()) {
                        merged.add(st.copy(text = s, width = accEndX - st.x))
                    }
                    acc = null; accStart = null
                }
                for (r in sorted) {
                    val gap = if (accStart == null) 0f else r.x - accEndX
                    if (accStart != null && gap <= 1.5f) {
                        acc!!.append(r.text)
                    } else {
                        flush()
                        acc = StringBuilder(r.text); accStart = r
                    }
                    accEndX = r.x + r.width
                }
                flush()
                val y = sorted.first().y
                out.add(Triple(page, y, merged))
            }
        }
        return out
    }

    @Test
    fun dumpStructure() {
        val (pdfOpt, pdf2) = samples()
        assumeTrue("样例 PDF 不存在,跳过转储", pdfOpt != null && pdfOpt.isFile)
        if (pdfOpt == null) return
        val pdf = pdfOpt
        if (pdf2 != null && pdf2.isFile) println("(另一样例可选: ${pdf2.name})")

        val result = DesktopPdfTextExtractor.extract(pdf)
        val lines = toLines(result.runs)

        val outFile = File("../.analysis-local/token_dump.txt")
        outFile.parentFile?.mkdirs()
        outFile.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("# 提取片段总数: ${result.runs.size}\n")
            w.write("# 页面几何:\n")
            result.pages.forEach { g ->
                w.write("#   page=${g.page} w=${g.width} h=${g.height} rot=${g.rotation}\n")
            }
            lines.groupBy { it.first }.toSortedMap().forEach { (page, pageLines) ->
                w.write("\n########## PAGE $page (lines=${pageLines.size}) ##########\n")
                pageLines.forEach { (_, y, toks) ->
                    w.write("y=%7.1f | ".format(y))
                    w.write(toks.joinToString("|") { "%.1f:%s".format(it.x, it.text) })
                    w.write("\n")
                }
            }
        }

        // 控制台只打印结构摘要 + 第 1 页与前几行,便于快速判读
        println("=== 提取摘要 ===")
        println("片段数=${result.runs.size}; 页数=${result.pages.size}")
        result.pages.forEach { println("  page=${it.page} w=${it.width} h=${it.height} rot=${it.rotation}") }
        lines.groupBy { it.first }.toSortedMap().forEach { (p, l) ->
            println("  page $p: ${l.size} 行")
        }
        println("=== PAGE 1 前 25 行 ===")
        lines.filter { it.first == 1 }.take(25).forEach { (_, y, toks) ->
            val s = toks.joinToString("|") { "%.0f:%s".format(it.x, it.text) }
            println("y=%6.1f | %s".format(y, if (s.length > 260) s.take(260) + "…" else s))
        }

        assertTrue("应提取到文本片段", result.runs.isNotEmpty())
        assertTrue("转储文件应生成", outFile.isFile)
    }
}
