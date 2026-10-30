package com.wmu.keban.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wmu.keban.data.Graph
import com.wmu.keban.ui.common.SampleBadge
import com.wmu.keban.ui.common.SectionCard
import com.wmu.keban.util.Dates
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen() {
    val repo = Graph.repo()
    val upcoming by repo.upcomingTasks.collectAsState(initial = emptyList())
    val semesterStart by repo.semesterStart.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    // 首次启动且库为空时写入示例数据(明确标注,可删除)
    LaunchedEffect(Unit) { repo.seedSampleIfNeeded() }

    val today = remember { LocalDate.now() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("今天") }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 日期与教学周
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(Dates.fullDate(today), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    val week = Dates.teachingWeek(semesterStart, today)
                    val weekText = when {
                        semesterStart == null -> "未设置学期起始日(可在“我的”页设置后显示教学周)"
                        week == 0 -> "学期尚未开始"
                        else -> "第 $week 教学周"
                    }
                    Text(
                        weekText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 今日课程(第二阶段:PDF 课表导入后显示)
            SectionCard("今日课程") {
                com.wmu.keban.ui.common.EmptyHint(
                    "课表导入后,这里显示今天的课程安排。\n(第二阶段支持 PDF 课表导入)"
                )
            }

            // 近期待办
            SectionCard("近期待办") {
                if (upcoming.isEmpty()) {
                    com.wmu.keban.ui.common.EmptyHint("暂无待办,在“待办”页新增")
                } else {
                    upcoming.forEach { task ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.done,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        if (checked) repo.toggleDone(task)
                                    }
                                }
                            )
                            Column(Modifier.padding(start = 4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(task.title, style = MaterialTheme.typography.bodyLarge)
                                    if (task.isSample) SampleBadge()
                                }
                                val meta = listOfNotNull(
                                    task.courseName?.takeIf { it.isNotBlank() },
                                    task.dueAt?.let { Dates.dueText(it) }
                                ).joinToString(" · ")
                                if (meta.isNotEmpty()) {
                                    Text(
                                        meta,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
