package com.wmu.keban.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
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
    val semesterStartCustomized by repo.semesterStartCustomized.collectAsState(initial = false)
    val sectionTimes by repo.sectionTimes.collectAsState(initial = emptyMap())
    val sectionTimesCustomized by repo.sectionTimesCustomized.collectAsState(initial = false)
    val autoFillLogin by repo.autoFillLogin.collectAsState(initial = true)
    val autoSubmitLogin by repo.autoSubmitLogin.collectAsState(initial = true)
    val courseCount by Graph.timetableRepo().coursesWithSlots
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var pickSemester by remember { mutableStateOf(false) }
    var pickCurrentWeek by remember { mutableStateOf(false) }
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
                    "学期起始日(第 1 周周一):" + (semesterStart?.let { Dates.dateOnly(it) } ?: "未设置") +
                        if (!semesterStartCustomized) "(温医大默认 9 月 7 日)" else "(已自定义)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "默认值 9 月 7 日开箱即用(不需要先点任何按钮);「今天」和「课表」页会按今天的日期" +
                        "自动定位到当前教学周。若与实际情况不符,可在这里改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (semesterStart != null) {
                    val w = Dates.teachingWeek(semesterStart)
                    Text(
                        "按今天推算:现在是第 ${w ?: 0} 周",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row {
                    TextButton(onClick = { pickSemester = true }) { Text("修改起始日") }
                    TextButton(onClick = { pickCurrentWeek = true }) { Text("按“现在是第几周”校准") }
                }
            }

            SectionCard("教务账号(自动登录)") {
                Text(
                    if (account.isNullOrBlank()) "未配置" else "已配置:学号 ****" + account!!.takeLast(4),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "配置后,内置浏览器一打开学校统一身份认证登录页,就会**自动填入**学号与密码" +
                        "(可另开自动提交,一次登录到底)。\n" +
                        "密码经 Android Keystore 加密后仅存本机,不写日志、不上传,可随时清除。\n" +
                        "注意:自动提交在同一个登录页只提交一次,失败不重试 —— 学校规定连续失败 5 次会锁定账号。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(
                        checked = autoFillLogin,
                        onCheckedChange = { scope.launch { repo.setAutoFillLogin(it) } }
                    )
                    Text("自动填入账号密码", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(
                        checked = autoSubmitLogin,
                        onCheckedChange = { scope.launch { repo.setAutoSubmitLogin(it) } }
                    )
                    Text("自动提交登录(需先填好账号密码)", style = MaterialTheme.typography.bodyMedium)
                }
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
                    when {
                        !sectionTimesCustomized ->
                            "温医大默认作息:已设置 ${sectionTimes.size} 个节次(开箱即用,未自定义)"
                        sectionTimes.isEmpty() -> "未设置(已清空自定义值)"
                        else -> "已自定义 ${sectionTimes.size} 个节次"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "默认作息:上午/下午每节 40 分钟、节间休息 5 分钟(第 2→3 节之间 15 分钟,第 7→8 节间隔 10 分钟);" +
                        "晚间每节 35 分钟、休息 5 分钟。课表 PDF 只给节次区间,这里的钟点用于在课表与「今天」页显示时间。\n" +
                        "第 18~22 节默认未设置(未提供,不臆测),需要时可自行补上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { pickSectionTimes = true }) {
                    Text(if (sectionTimesCustomized) "修改节次时间" else "查看/修改节次时间")
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

    if (pickCurrentWeek) {
        CurrentWeekDialog(
            onDismiss = { pickCurrentWeek = false },
            onPick = { n ->
                scope.launch { repo.calibrateByCurrentWeek(n) }
                pickCurrentWeek = false
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
 * 按"现在是第几周"校准学期起始日。
 * 用户通常只知道"这周是第 9 周",比起让他回忆开学那天,这样输入更省事且同样准确。
 */
@Composable
private fun CurrentWeekDialog(
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    var text by remember { mutableStateOf("1") }
    val n = text.trim().toIntOrNull()?.takeIf { it in 1..30 }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按“现在是第几周”校准") },
        text = {
            Column {
                Text(
                    "填本周是第几教学周,应用会反推第 1 周周一并据此自动定位周次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("本周是第几周") },
                    singleLine = true,
                    isError = n == null
                )
                if (n == null) {
                    Text(
                        "请输入 1~30 之间的整数",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = n != null, onClick = { n?.let(onPick) }) { Text("校准") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 节次时间表编辑:一行一节,`节次 开始-结束`(如 `1 08:00-08:45`)。
 * 支持按"首节开始 / 单节时长 / 课间休息"一键生成初稿,生成结果仍可逐节修改。
 */
@Composable
private fun SectionTimesDialog(
    initial: com.wmu.keban.util.SectionTimes.Table,
    onDismiss: () -> Unit,
    onSave: (com.wmu.keban.util.SectionTimes.Table) -> Unit
) {
    var text by remember {
        mutableStateOf(
            com.wmu.keban.util.SectionTimes.toLines(initial).ifBlank {
                // 未设置过就先给一份按默认参数的初稿,方便直接改;保存前都只是文本框内容
                com.wmu.keban.util.SectionTimes.toLines(
                    com.wmu.keban.util.SectionTimes.generate()
                )
            }
        )
    }
    var firstStart by remember {
        mutableStateOf(com.wmu.keban.util.SectionTimes.DEFAULT_FIRST_START)
    }
    var duration by remember {
        mutableStateOf(com.wmu.keban.util.SectionTimes.DEFAULT_DURATION_MIN.toString())
    }
    var breakMin by remember {
        mutableStateOf(com.wmu.keban.util.SectionTimes.DEFAULT_BREAK_MIN.toString())
    }
    val parsed = com.wmu.keban.util.SectionTimes.fromLines(text)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("节次时间表") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "一键生成:按“第 1 节开始时间 + 单节时长 + 课间休息”推算(默认 08:00 / 45 分钟 / 5 分钟)。\n" +
                        "生成结果是可编辑的初稿,请按学校实际作息修正(如上午/下午大课间的差异)。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(
                        value = firstStart,
                        onValueChange = { firstStart = it.take(5) },
                        label = { Text("第1节开始") },
                        singleLine = true,
                        modifier = Modifier.width(108.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("分钟") },
                        singleLine = true,
                        modifier = Modifier.width(76.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = breakMin,
                        onValueChange = { breakMin = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("休息") },
                        singleLine = true,
                        modifier = Modifier.width(76.dp)
                    )
                }
                TextButton(onClick = {
                    val table = com.wmu.keban.util.SectionTimes.generate(
                        firstStart = firstStart,
                        durationMinutes = duration.toIntOrNull() ?: 0,
                        breakMinutes = breakMin.toIntOrNull() ?: 0
                    )
                    if (table.isNotEmpty()) text = com.wmu.keban.util.SectionTimes.toLines(table)
                }) { Text("按此生成到文本框") }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    "每行一节,格式:`节次 开始-结束`,空行与 # 开头的行会被忽略。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(190.dp),
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
