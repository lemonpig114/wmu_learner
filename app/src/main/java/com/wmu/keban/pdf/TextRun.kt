package com.wmu.keban.pdf

/**
 * PDF 页面上的一段文本片段。
 *
 * 坐标使用 pdfbox 的 "方向校正后" 坐标(xDirAdj / yDirAdj),其语义:
 * - x 向右递增
 * - y **向下**递增(以页面顶部为 0)
 * - 已由 pdfbox 依据页面旋转与文字方向做过校正,故不同旋转页可直接比较
 *
 * 该类型是解析核心的输入,不依赖 pdfbox 类,便于在 JVM 上单测。
 */
data class TextRun(
    val page: Int,
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

/** 一次 PDF 提取的完整结果 */
data class ExtractionResult(
    /** 每页的几何信息(宽、高、旋转角) */
    val pages: List<PageGeometry>,
    val runs: List<TextRun>
)

data class PageGeometry(
    val page: Int,
    val width: Float,
    val height: Float,
    val rotation: Int
)
