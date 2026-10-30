package com.wmu.keban.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
    val scope = rememberCoroutineScope()
    var pickSemester by remember { mutableStateOf(false) }

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
                Text("版本 0.1.0(第一阶段)", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "面向温州医科大学学生的课程助手",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text("阶段进度", style = MaterialTheme.typography.titleSmall)
                listOf(
                    "✅ 第一阶段:工程骨架、四页面、本地待办、内置浏览器",
                    "○ 第二阶段:PDF 课表导入(格式分析已完成,待实现)",
                    "○ 第三阶段:教务入口登录与课表 PDF 下载",
                    "○ 第四阶段:真机验证与交付"
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
