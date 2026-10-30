package com.wmu.keban.ui.todos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wmu.keban.data.Graph
import com.wmu.keban.data.TaskItem
import com.wmu.keban.ui.common.EmptyHint
import com.wmu.keban.ui.common.TodoRow
import com.wmu.keban.util.Dates
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodosScreen() {
    val repo = Graph.repo()
    val tasks by repo.allTasks.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // 编辑目标:id == 0 表示新增,否则为编辑已有任务
    var editTarget by remember { mutableStateOf<TaskItem?>(null) }
    var deleteTarget by remember { mutableStateOf<TaskItem?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("待办") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editTarget = TaskItem(title = "") }
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("新增待办")
            }
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EmptyHint("暂无待办\n点击右下角「新增待办」手动添加")
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(tasks.size) { index ->
                    val task = tasks[index]
                    TodoRow(
                        task = task,
                        onToggle = { scope.launch { repo.toggleDone(it) } },
                        onEdit = { editTarget = it },
                        onDelete = { deleteTarget = it }
                    )
                }
            }
        }
    }

    // 新增 / 编辑对话框
    editTarget?.let { target ->
        TaskEditDialog(
            initial = target,
            onDismiss = { editTarget = null },
            onSave = { title, course, note, dueAt ->
                scope.launch {
                    if (target.id == 0L) {
                        repo.addTask(title, course, note, dueAt)
                    } else {
                        repo.updateTaskContent(target, title, course, note, dueAt)
                    }
                }
                editTarget = null
            }
        )
    }

    // 删除确认
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除待办") },
            text = { Text("确定删除「${target.title}」?此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.deleteTask(target) }
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 任务编辑对话框
 * 截止时间可空:未设置时明确显示"未设置截止",不做任何猜测填充
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TaskEditDialog(
    initial: TaskItem,
    onDismiss: () -> Unit,
    onSave: (title: String, course: String?, note: String?, dueAt: Long?) -> Unit
) {
    var title by remember { mutableStateOf(initial.title) }
    var course by remember { mutableStateOf(initial.courseName ?: "") }
    var note by remember { mutableStateOf(initial.note ?: "") }
    var dueDate by remember { mutableStateOf(initial.dueAt?.let(Dates::toLocalDate)) }
    var dueTime by remember { mutableStateOf(initial.dueAt?.let(Dates::toLocalTime)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "新增待办" else "编辑待办") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题(必填)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = course,
                    onValueChange = { course = it },
                    label = { Text("课程(可选)") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(dueDate?.let { "截止:" + Dates.dateOnly(it.toEpochDay()) }
                            ?: "设置截止日期(可选)")
                    }
                    if (dueDate != null) {
                        TextButton(onClick = { dueDate = null; dueTime = null }) { Text("清除") }
                    }
                }
                if (dueDate != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showTimePicker = true }) {
                            Text(if (dueTime != null) "时间:" + dueTime.toString() else "全天(不设具体时间)")
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注(可选)") },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        title.trim(),
                        course.trim().ifEmpty { null },
                        note.trim().ifEmpty { null },
                        dueDate?.let { Dates.millisOf(it, dueTime) }
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dueDate?.let { Dates.startOfDayMillis(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        dueDate = Instant.ofEpochMilli(ms)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = dueTime?.hour ?: 18,
            initialMinute = dueTime?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("截止时间(可选)") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    dueTime = LocalTime.of(state.hour, state.minute)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        dueTime = null
                        showTimePicker = false
                    }) { Text("全天") }
                    TextButton(onClick = { showTimePicker = false }) { Text("取消") }
                }
            }
        )
    }
}
