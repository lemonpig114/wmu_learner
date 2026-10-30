package com.wmu.keban.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.File

/**
 * 测试专用提取器(桌面版 pdfbox 2.0.x)。
 *
 * App 内使用 com.tom-roush:pdfbox-android:2.0.27.0(同源 2.0.x API),
 * 两端都产出 [ExtractionResult](方向校正后的 xDirAdj/yDirAdj),
 * 因此可在 JVM 上用真实 PDF 验证解析核心。
 */
object DesktopPdfTextExtractor {

    fun extract(file: File): ExtractionResult {
        PDDocument.load(file).use { doc ->
            val runs = mutableListOf<TextRun>()

            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                    if (textPositions == null) return
                    val pageNo = currentPageNo
                    for (tp in textPositions) {
                        val u = tp.unicode ?: continue
                        if (u.isBlank()) continue
                        runs.add(
                            TextRun(
                                page = pageNo,
                                text = u,
                                x = tp.xDirAdj,
                                y = tp.yDirAdj,
                                width = tp.widthDirAdj,
                                height = tp.heightDir
                            )
                        )
                    }
                }
            }
            stripper.sortByPosition = false
            stripper.getText(doc) // 触发提取

            val geoms = (0 until doc.numberOfPages).map { i ->
                val p: PDPage = doc.getPage(i)
                PageGeometry(
                    page = i + 1,
                    width = p.mediaBox.width,
                    height = p.mediaBox.height,
                    rotation = p.rotation
                )
            }
            return ExtractionResult(pages = geoms, runs = runs)
        }
    }
}
