package com.wmu.keban

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wmu.keban.data.Graph
import com.wmu.keban.ui.KebanApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Graph.init(applicationContext)
        setContent {
            KebanApp()
        }
    }
}
