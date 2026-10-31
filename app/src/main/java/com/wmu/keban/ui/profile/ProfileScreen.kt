package com.wmu.keban.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wmu.keban.data.Graph
import com.wmu.keban.security.SecureStore
import com.wmu.keban.ui.common.SectionCard
import com.wmu.keban.util.Dates
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onOpenBrowser: () -> Unit) {
    val repo = Graph.repo()
    val semesterStart by repo.semesterStart.collectAsState(initial = null)
    val sectionTimes by repo.sectionTimes.collectAsState(initial = emptyMap())
    val courseCount by Graph.timetableRepo().coursesWithSlots
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var pickSemester by remember { mutableStateOf(false) }
    var pickSectionTimes by remember { mutableStateOf(false) }
    var clearImported by remember { mutableStateOf(false) }
    var clearAllConfirm by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    var account by remember { mutableStateOf(SecureStore.load(context, SecureStore.KIND_ACCOUNT)) }
    var showCredDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("我的") }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard("学期设置") {
                Text(
                    "学期起始日:" + (semesterStart?.let { Dates.dateOnly(it) } ?: "未设置"),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "设置后,「今天」页将显示当前教学周。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { pickSemester = true }) {
                    Text(if (semesterStart == null) "设置学期起始日" else "修改学期起始日")
                }
            }

            SectionCard("教务账号(自动获取课表)") {
                Text(
                    if (account.isNullOrBlank()) "未配置(仍可用内置浏览器登录)" else "已配置:学号 ****" + account!!.takeLast(4),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "配置后,课表页点「自动获取」会先尝试用账号密码自动登录统一身份认证;" +
                        "若本次登录被要求图形验证码,会自动改用「内置浏览器」的登录会话。\n" +
                        "密码经 Android Keystore 加密后仅存本机,不写日志、不上传,可随时清除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    TextButton(onClick = { showCredDialog = true }) {
                        Text(if (account.isNullOrBlank()) "配置账号" else "修改账号")
                    }
                    if (!account.isNullOrBlank()) {
                        TextButton(onClick = {
                            SecureStore.clear(context, SecureStore.KIND_ACCOUNT)
                            SecureStore.clear(context, SecureStore.KIND_PASSWORD)
                            account = null
                        }) { Text("清除") }
                    }
                }
            }

            SectionCard("教务登录(浏览器会话)") {
                Text(
                    "在「内置浏览器」中登录教务(可以人工输入验证码),登录一次即可;" +
                        "之后「自动获取」与网页内「下载」都会复用该会话。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    TextButton(onClick = onOpenBrowser) { Text("打开内置浏览器登录") }
                    TextButton(onClick = {
                        android.webkit.CookieManager.getInstance().removeAllCookies(null)
                        android.webkit.CookieManager.getInstance().flush()
                    }) { Text("清除登录会话") }
                }
            }

            SectionCard("节次时间表") {
                Text(
                    if (sectionTimes.isEmpty()) "未设置" else "已设置 ${sectionTimes.size} 个节次",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "课表 PDF 只给出节次(如 1-2 节),不含具体钟点;填写后「今天」页会显示上课时间。\n" +
                        "未填写的节次只显示节次,不会猜测时间。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { pickSectionTimes = true }) {
                    Text(if (sectionTimes.isEmpty()) "设置节次时间" else "修改节次时间")
                }
            }

            SectionCard("课表数据") {
                Text(
                    "清空课表只会删除课程与导入记录,**不影响「待办」**。清空后可以用「一键获取」重新导入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    TextButton(onClick = { clearImported = true }) { Text("清空导入的课表(保留手动添加)") }
                    TextButton(onClick = { clearAllConfirm = true }) { Text("清空全部课程") }
                }
            }

            SectionCard("工具") {
                Text("内置浏览器", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "在应用内打开网页,与系统浏览器隔离。\n" +
                        "第三阶段用于:教务入口登录 → 课表 PDF 下载。\n" +
                        "安全:不记录 Cookie、凭据与浏览历史。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onOpenBrowser) { Text("打开内置浏览器") }
            }

            SectionCard("关于课伴") {
                Text(
                    "版本 ${com.wmu.keban.BuildConfig.VERSION_NAME}(${com.wmu.keban.BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "面向温州医科大学学生的课程助手",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text("阶段进度", style = MaterialTheme.typography.titleSmall)
                listOf(
                    "✅ 四页面 + 本地待办 + 内置浏览器",
                    "✅ PDF 课表导入(解析→预览校正→保存→周课表)",
                    "🔄 教务自动获取(登录会话复用中,待真机验证)",
                    "○ 提醒与后续功能"
                ).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "进度按实际验证情况更新;未验证的功能不标注完成。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showCredDialog) {
        var user by remember { mutableStateOf(account ?: "") }
        var pwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCredDialog = false },
            title = { Text("教务账号") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = user, onValueChange = { user = it },
                        label = { Text("学号") }, singleLine = true
                    )
                    OutlinedTextField(
                        value = pwd, onValueChange = { pwd = it },
                        label = { Text("统一身份认证密码") }, singleLine = true
                    )
                    Text(
                        "密码经 Android Keystore 加密后仅存本机,仅用于登录学校统一身份认证;" +
                            "不写日志、不上传。若登录时被要求验证码,应用会自动改用浏览器会话。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = user.isNotBlank() && pwd.isNotBlank(),
                    onClick = {
                        SecureStore.save(context, SecureStore.KIND_ACCOUNT, user.trim())
                        SecureStore.save(context, SecureStore.KIND_PASSWORD, pwd)
                        account = user.trim()
                        showCredDialog = false
                    }
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showCredDialog = false }) { Text("取消") } }
        )
    }

    if (pickSemester) {
        SemesterDatePicker(
            initial = semesterStart,
            onDismiss = { pickSemester = false },
            onPick = { epochDay ->
                scope.launch { repo.setSemesterStart(epochDay) }
                pickSemester = false
            }
        )
    }

    if (pickSectionTimes) {
        SectionTimesDialog(
            initial = sectionTimes,
            onDismiss = { pickSectionTimes = false },
            onSave = { table ->
                scope.launch { repo.setSectionTimes(table) }
                pickSectionTimes = false
            }
        )
    }

    if (clearImported) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { clearImported = false },
            title = { Text("清空导入的课表?") },
            text = {
                Text(
                    "将删除从教务导入的课程与导入记录,**保留手动添加的课程**;「待办」不受影响。\n" +
                        "此操作不可撤销。",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        Graph.timetableRepo().clearImported()
                        toast = "已清空导入的课表"
                    }
                    clearImported = false
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { clearImported = false }) { Text("取消") } }
        )
    }

    if (clearAllConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { clearAllConfirm = false },
            title = { Text("清空全部课程?") },
            text = {
                Text(
                    "将删除**全部课程**(含手动添加的)与导入记录;「待办」不受影响。\n" +
                        "此操作不可撤销。",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        Graph.timetableRepo().clearAll()
                        toast = "已清空全部课程"
                    }
                    clearAllConfirm = false
                }) { Text("全部清空") }
            },
            dismissButton = { TextButton(onClick = { clearAllConfirm = false }) { Text("取消") } }
        )
    }

    toast?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { toast = null },
            title = { Text("提示") },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("知道了") } }
        )
    }
}

/**
 * 节次时间表编辑:一行一节,`节次 开始-结束`(如 `1 08:00-08:45`)。
 * 用多行文本而不是 22×2 输入框:输入更少、键盘更好用,也便于整表复制粘贴。
 */
@Composable
private fun SectionTimesDialog(
    initial: com.wmu.keban.util.SectionTimes.Table,
    onDismiss: () -> Unit,
    onSave: (com.wmu.keban.util.SectionTimes.Table) -> Unit
) {
    var text by remember {
        mutableStateOf(
            com.wmu.keban.util.SectionTimes.toLines(initial).ifBlank { "" }
        )
    }
    val parsed = com.wmu.keban.util.SectionTimes.fromLines(text)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("节次时间表") },
        text = {
            Column {
                Text(
                    "每行一节,格式:`节次 开始-结束`,例如\n" +
                        "1 08:00-08:45\n2 08:55-09:40\n" +
                        "只需填你用得上的节次;空行与 # 开头的行会被忽略。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    placeholder = { Text("1 08:00-08:45") },
                    singleLine = false
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (parsed.isEmpty()) {
                        "当前可解析出 0 个节次(保存后为「未设置」)"
                    } else {
                        "当前可解析出 ${parsed.size} 个节次:第 ${parsed.keys.sorted().joinToString(",")} 节"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(parsed) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SemesterDatePicker(
    initial: Long?,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let { Dates.startOfDayMillis(LocalDate.ofEpochDay(it)) }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    onPick(
                        Instant.ofEpochMilli(ms)
                            .atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
                    )
                }
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        DatePicker(state = state)
    }
}
