package com.wmu.keban.data

import java.io.File

/**
 * 跨页面的导入交接(进程内)
 *
 * 用途:内置浏览器里下载到课表 PDF 后,把文件路径交给课表页,由课表页进入
 * 「解析结果预览」流程。避免为传一个文件而引入导航参数或全局数据库状态。
 */
object ImportHandoff {
    @Volatile
    var pendingPdf: File? = null

    fun take(): File? {
        val f = pendingPdf
        pendingPdf = null
        return f
    }
}
