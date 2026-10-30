package com.wmu.keban.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wmu.keban.ui.common.SectionCard

/**
 * 课表页(第一阶段:空状态)
 * 第二阶段实现:PDF 导入 → 解析 → 预览校正 → 确认保存 → 周课表网格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("课表") }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("尚未导入课表", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "第二阶段将支持导入温州医科大学课表 PDF：\n" +
                            "选择 PDF → 解析 → 预览校正 → 确认保存 → 显示周课表。\n\n" +
                            "已完成：真实课表 PDF 的格式分析(文字型,可解析)。\n" +
                            "当前版本：第一阶段 0.1.0,暂不支持导入。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            SectionCard("第二阶段将支持") {
                listOf(
                    "课程名 / 星期 / 节次 / 周次(含单双周、不连续周次)",
                    "跨节课程(如 3-5 节)与一门课多时段",
                    "校区 / 场地 / 教师,无法确定的字段标记“待确认”",
                    "手动新增、编辑、删除课程;学期起始日与节次时间表设置"
                ).forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
