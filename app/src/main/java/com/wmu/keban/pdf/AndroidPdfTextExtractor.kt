package com.wmu.keban.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

/**
 * App 内 PDF 文本提取(pdfbox-android 2.0.27.0,与单测所用桌面版 pdfbox 2.0.37 同源)。
 * 与 [WmuTimetableParser] 通过 [ExtractionResult] 解耦:解析核心不含任何 PDF 库依赖。
 *
 * 坐标语义与桌面版一致:xDirAdj 向右、yDirAdj **向下**(已按页面旋转校正)。
 */
object AndroidPdfTextExtractor {

    @Volatile
    private var initialized = false

    /** 使用前必须初始化(加载 pdfbox 资源);幂等 */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                initialized = true
            }
        }
    }

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
