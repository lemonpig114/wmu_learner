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
import androidx.compose.foundation.layout.width
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
import com.wmu.keban.data.CourseWithSlots
import com.wmu.keban.data.CourseSlot
import com.wmu.keban.ui.common.SampleBadge
import com.wmu.keban.ui.common.SectionCard
import com.wmu.keban.util.Dates
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 某一时段(带所属课程) */
data class TodaySlot(val course: com.wmu.keban.data.Course, val slot: CourseSlot)

/**
 * 今天要上的课:星期匹配今天,且当前教学周在该时段的周次内。
 * 未设置学期起始日 ⇒ 无法判断教学周,此时不做周次过滤(只按星期列出,避免"什么都看不到"),
 * 但界面会提示设置学期起始日。
 */
fun todaySlots(
    courses: List<CourseWithSlots>,
    semesterStart: Long?,
    today: LocalDate
): List<TodaySlot> {
    val dayOfWeek = today.dayOfWeek.value // 1=周一 .. 7=周日
    val week = semesterStart?.let { Dates.teachingWeek(it, today) }
    return courses.flatMap { cws ->
        cws.slots
            .filter { it.dayOfWeek == dayOfWeek }
            .filter { slot ->
                if (week == null || week <= 0) true
                else slot.weeksExpanded.split(',').any { it.trim().toIntOrNull() == week }
            }
            .map { TodaySlot(cws.course, it) }
    }.sortedBy { it.slot.startSection }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen() {
    val repo = Graph.repo()
    val timetableRepo = Graph.timetableRepo()
    val upcoming by repo.upcomingTasks.collectAsState(initial = emptyList())
    val semesterStart by repo.semesterStart.collectAsState(initial = null)
    val sectionTimes by repo.sectionTimes.collectAsState(initial = emptyMap())
    val courses by timetableRepo.coursesWithSlots.collectAsState(initial = emptyList())
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

            // 今日课程:按"今天星期 + 当前教学周"筛选课表时段
            SectionCard("今日课程") {
                val todays = com.wmu.keban.ui.today.todaySlots(courses, semesterStart, today)
                if (todays.isEmpty()) {
                    com.wmu.keban.ui.common.EmptyHint(
                        if (courses.isEmpty()) {
                            "还没有课表。用「课表」页的「一键获取」导入,或自己导出 PDF 后导入。"
                        } else if (semesterStart == null) {
                            "今天是周日、或本周没有课。\n若教学周显示不正确,可在“我的”页设置学期起始日。"
                        } else {
                            "今天是周日、或本周没有课。"
                        }
                    )
                } else {
                    todays.forEach { item ->
                        val timeText = com.wmu.keban.util.SectionTimes
                            .range(sectionTimes, item.slot.startSection, item.slot.endSection)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                com.wmu.keban.util.SectionTimes
                                    .sectionText(item.slot.startSection, item.slot.endSection),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(64.dp)
                            )
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(
                                    item.course.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                val meta = listOfNotNull(
                                    timeText,
                                    item.slot.room ?: item.course.room,
                                    item.slot.teacher ?: item.course.teacher
                                ).filter { !it.isNullOrBlank() }.joinToString(" · ")
                                Text(
                                    if (meta.isBlank()) "时间/场地未设置" else meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
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
