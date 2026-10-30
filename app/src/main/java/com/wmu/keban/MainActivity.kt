package com.wmu.keban

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wmu.keban.data.Graph
import com.wmu.keban.pdf.AndroidPdfTextExtractor
import com.wmu.keban.ui.KebanApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Graph.init(applicationContext)
        // pdfbox-android 资源初始化(第二阶段:PDF 课表解析)
        AndroidPdfTextExtractor.init(applicationContext)
        setContent {
            KebanApp()
        }
    }
}
