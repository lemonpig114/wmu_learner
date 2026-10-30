package com.wmu.keban.ui.timetable

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wmu.keban.data.CourseSlot
import com.wmu.keban.data.CourseWithSlots
import com.wmu.keban.data.Graph
import com.wmu.keban.data.ImportRecord
import com.wmu.keban.data.TimetableAssembler
import com.wmu.keban.pdf.AndroidPdfTextExtractor
import com.wmu.keban.pdf.ParsedSlot
import com.wmu.keban.pdf.ParsedTimetable
import com.wmu.keban.pdf.Weeks
import com.wmu.keban.pdf.WmuTimetableParser
import com.wmu.keban.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private val DAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private const val MAX_SECTION = 22
private val HEADER_H = 28.dp
private val ROW_H = 40.dp
private val CELL_W = 92.dp
private val SEC_W = 34.dp

/** 编辑中的课程信息(导入预览 / 手动新增 / 时段编辑 共用) */
private data class SlotEdit(
    val name: String,
    val day: Int,
    val startSection: Int,
    val endSection: Int,
    val weeksText: String,
    val room: String?,
    val teacher: String?
)

/** 一次导入会话(解析结果保存在内存,用户确认后才落库) */
private data class ImportSession(
    val fileName: String,
    val fileHash: String,
    val parsed: ParsedTimetable,
    val slots: List<ParsedSlot>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen() {
    val repo = Graph.timetableRepo()
    val kebanRepo = Graph.repo()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val courses by repo.coursesWithSlots.collectAsState(initial = emptyList())
    val semesterStart by kebanRepo.semesterStart.collectAsState(initial = null)

    var week by remember { mutableStateOf<Int?>(Dates.teachingWeek(semesterStart) ?: 1) }
    var session by remember { mutableStateOf<ImportSession?>(null) }
    var parsing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var duplicateOf by remember { mutableStateOf<ImportRecord?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    var detailCourse by remember { mutableStateOf<CourseWithSlots?>(null) }
    var editBasicCourse by remember { mutableStateOf<CourseWithSlots?>(null) }
    var addManual by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        parsing = true
        banner = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { parsePickedPdf(context, uri) }
            parsing = false
            outcome.fold(
                onSuccess = { out ->
                    if (out.parsed.slots.isEmpty()) {
                        banner = "未能从该文件识别出课表" +
                            (out.parsed.warnings.firstOrNull()?.let { ":$it" } ?: "。") +
                            " 现有课表未被改动。"
                    } else {
                        session = ImportSession(out.fileName, out.fileHash, out.parsed, out.parsed.slots)
                        duplicateOf = withContext(Dispatchers.IO) {
                            repo.findSameImport(TimetableAssembler.signature(out.parsed.slots))
                        }
                    }
                },
                onFailure = { e -> banner = "解析失败:${e.message ?: e.javaClass.simpleName} 现有课表未被改动。" }
            )
        }
    }

    LaunchedEffect(semesterStart) {
        week = Dates.teachingWeek(semesterStart) ?: week ?: 1
    }

    val active = session
    if (active != null) {
        ImportPreview(
            session = active,
            duplicateOf = duplicateOf,
            saving = saving,
            onEditSlot = { index, edit ->
                val old = active.slots[index]
                val parsedWeeks = Weeks.parse(edit.weeksText)
                val note = if (edit.weeksText.isNotBlank() && parsedWeeks == null)
                    listOf("周次未识别(\"${edit.weeksText}\")") else emptyList()
                val updated = old.copy(
                    courseName = edit.name.trim(),
                    dayOfWeek = edit.day,
                    startSection = edit.startSection,
                    endSection = edit.endSection,
                    weeksRaw = edit.weeksText.trim(),
                    weeksCanonical = parsedWeeks?.canonical ?: edit.weeksText.trim(),
                    weeksExpanded = parsedWeeks?.expanded ?: emptyList(),
                    parity = parsedWeeks?.parity ?: "未知",
                    room = edit.room?.ifBlank { null },
                    teacher = edit.teacher?.ifBlank { null },
                    needsConfirm = note.isNotEmpty() || old.needsConfirm,
                    confirmNotes = (old.confirmNotes + note).distinct()
                )
                session = active.copy(slots = active.slots.toMutableList().also { it[index] = updated })
            },
            onDeleteSlot = { index ->
                session = active.copy(slots = active.slots.toMutableList().also { it.removeAt(index) })
            },
            onDeleteCourse = { name ->
                session = active.copy(slots = active.slots.filterNot { it.courseName == name })
            },
            onCancel = {
                session = null
                duplicateOf = null
                banner = "已取消导入,现有课表未改动。"
            },
            onConfirm = {
                saving = true
                scope.launch {
                    try {
                        val result = repo.saveImport(
                            active.parsed.copy(slots = active.slots),
                            active.fileName,
                            active.fileHash
                        )
                        banner = "已保存 ${result.courseCount} 门课程、${result.slotCount} 个时段" +
                            if (result.needsConfirmCount > 0) "(其中 ${result.needsConfirmCount} 项标记待确认)" else ""
                        session = null
                        duplicateOf = null
                    } catch (e: Exception) {
                        banner = "保存失败:${e.message ?: e.javaClass.simpleName} 现有课表未被破坏。"
                    } finally {
                        saving = false
                    }
                }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课表") },
                actions = {
                    TextButton(
                        onClick = { picker.launch(arrayOf("application/pdf")) },
                        enabled = !parsing
                    ) { Text(if (parsing) "解析中…" else "导入") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { addManual = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("手动添加")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            banner?.let { msg ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { banner = null }) { Text("知道了") }
                    }
                }
            }

            if (courses.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("尚未导入课表", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "步骤:选择教务系统导出的课表 PDF → 解析 → 预览校正 → 确认保存。\n" +
                                    "解析结果中无法确定的信息会标记为「待确认」,不会自动猜测。",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { picker.launch(arrayOf("application/pdf")) },
                                enabled = !parsing
                            ) { Text(if (parsing) "正在解析…" else "选择课表 PDF") }
                            if (parsing) {
                                Text("首次解析需要几秒,请稍候", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                WeekSelector(
                    week = week,
                    onWeekChange = { week = it },
                    courseCount = courses.size,
                    onShowAll = { week = null }
                )
                WeekGrid(courses = courses, week = week, onCourseClick = { detailCourse = it })
            }
        }
    }

    detailCourse?.let { cws ->
        CourseDetailDialog(
            cws = cws,
            onDismiss = { detailCourse = null },
            onEditBasic = { editBasicCourse = cws; detailCourse = null },
            onEditSlot = { slotIndex, edit ->
                val slots = cws.slots.toMutableList()
                val old = slots[slotIndex]
                val parsedWeeks = Weeks.parse(edit.weeksText)
                slots[slotIndex] = old.copy(
                    dayOfWeek = edit.day,
                    startSection = edit.startSection,
                    endSection = edit.endSection,
                    weeksRaw = edit.weeksText.trim(),
                    weeksCanonical = parsedWeeks?.canonical ?: edit.weeksText.trim(),
                    weeksExpanded = (parsedWeeks?.expanded ?: emptyList()).joinToString(","),
                    parity = parsedWeeks?.parity ?: "未知",
                    room = edit.room?.ifBlank { null },
                    teacher = edit.teacher?.ifBlank { null }
                )
                scope.launch {
                    repo.replaceSlots(cws.course.id, slots)
                    banner = "已更新「${cws.course.name}」的时段"
                }
                detailCourse = null
            },
            onDeleteSlot = { slotIndex ->
                val slots = cws.slots.toMutableList().also { it.removeAt(slotIndex) }
                scope.launch {
                    if (slots.isEmpty()) repo.deleteCourse(cws.course.id)
                    else repo.replaceSlots(cws.course.id, slots)
                    banner = "已删除时段"
                }
                detailCourse = null
            },
            onDeleteCourse = {
                scope.launch {
                    repo.deleteCourse(cws.course.id)
                    banner = "已删除课程「${cws.course.name}」及其全部时段"
                }
                detailCourse = null
            }
        )
    }

    editBasicCourse?.let { cws ->
        var name by remember { mutableStateOf(cws.course.name) }
        var room by remember { mutableStateOf(cws.course.room ?: "") }
        var teacher by remember { mutableStateOf(cws.course.teacher ?: "") }
        AlertDialog(
            onDismissRequest = { editBasicCourse = null },
            title = { Text("编辑课程信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("课程名(必填)") }, singleLine = true)
                    OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("场地") }, singleLine = true)
                    OutlinedTextField(value = teacher, onValueChange = { teacher = it }, label = { Text("教师") }, singleLine = true)
                    Text(
                        "注:周课表中的场地/教师以各时段自身记录为准。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        scope.launch {
                            repo.updateCourseBasic(cws.course, name, room, teacher)
                            banner = "已更新课程信息"
                        }
                        editBasicCourse = null
                    }
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editBasicCourse = null }) { Text("取消") } }
        )
    }

    if (addManual) {
        SlotEditorDialog(
            initial = SlotEdit("", 1, 1, 2, "1-16周", "", ""),
            title = "手动添加课程",
            confirmLabel = "添加",
            onDismiss = { addManual = false },
            onSave = { edit ->
                val parsedWeeks = Weeks.parse(edit.weeksText)
                scope.launch {
                    repo.addManualCourse(
                        name = edit.name.trim(),
                        dayOfWeek = edit.day,
                        startSection = edit.startSection,
                        endSection = edit.endSection,
                        weeksRaw = edit.weeksText.trim(),
                        weeksCanonical = parsedWeeks?.canonical ?: edit.weeksText.trim(),
                        weeksExpanded = parsedWeeks?.expanded ?: emptyList(),
                        parity = parsedWeeks?.parity ?: "未知",
                        room = edit.room,
                        teacher = edit.teacher
                    )
                    banner = "已添加课程「${edit.name.trim()}」"
                }
                addManual = false
            }
        )
    }
}

// ─────────────────────────── 选文件 + 解析 ───────────────────────────

private data class ParseOutcome(val fileName: String, val fileHash: String, val parsed: ParsedTimetable)

/**
 * 所选 PDF → SHA-256 → pdfbox-android 提取 → 解析。
 * 文件先落 cacheDir 再解析,解析后立即删除(不在应用内留存个人课表副本)。
 */
private fun parsePickedPdf(context: android.content.Context, uri: Uri): Result<ParseOutcome> = runCatching {
    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "timetable.pdf"
    val tmp = File(context.cacheDir, "import_tmp.pdf")
    val digest = MessageDigest.getInstance("SHA-256")
    context.contentResolver.openInputStream(uri)?.use { input ->
        tmp.outputStream().use { out ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
                out.write(buf, 0, n)
            }
        }
    } ?: error("无法读取所选文件")
    try {
        val extraction = AndroidPdfTextExtractor.extract(tmp)
        val parsed = WmuTimetableParser.parse(extraction)
        ParseOutcome(name, digest.digest().joinToString("") { "%02x".format(it) }, parsed)
    } finally {
        tmp.delete()
    }
}

// ─────────────────────────── 周次选择 + 网格 ───────────────────────────

@Composable
private fun WeekSelector(
    week: Int?,
    onWeekChange: (Int?) -> Unit,
    courseCount: Int,
    onShowAll: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { onWeekChange(((week ?: 1) - 1).coerceAtLeast(1)) }) { Text("‹") }
        Text(
            if (week == null) "全部周次" else "第 $week 周",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        TextButton(onClick = { onWeekChange(((week ?: 1) + 1).coerceAtMost(30)) }) { Text("›") }
        TextButton(onClick = onShowAll) { Text(if (week == null) "共 $courseCount 门" else "全部") }
    }
}

@Composable
private fun WeekGrid(
    courses: List<CourseWithSlots>,
    week: Int?,
    onCourseClick: (CourseWithSlots) -> Unit
) {
    data class CellItem(val cws: CourseWithSlots, val slot: CourseSlot)

    val visible = remember(courses, week) {
        courses.flatMap { cws ->
            cws.slots.filter { s ->
                val weeks = s.weeksExpanded.split(',').mapNotNull { it.trim().toIntOrNull() }
                // 周次未知(未设置/未识别)时始终显示,并在详情中标注
                week == null || weeks.isEmpty() || weeks.contains(week)
            }.map { CellItem(cws, it) }
        }
    }
    val maxSection = (visible.maxOfOrNull { it.slot.endSection } ?: 12).coerceAtMost(MAX_SECTION)

    val palette = listOf(
        Color(0xFFD3E4FF), Color(0xFFCDE8D8), Color(0xFFFFE1B8), Color(0xFFF5D2E0),
        Color(0xFFE0D8FF), Color(0xFFD7EEF7), Color(0xFFF0E1C2)
    )
    val colorOf = remember(courses) {
        courses.mapIndexed { i, c -> c.course.id to palette[i % palette.size] }.toMap()
    }

    Row(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
    ) {
        // 节次列(顶部留出与星期表头等高的空位,保证横向对齐)
        Column(Modifier.width(SEC_W)) {
            Box(Modifier.height(HEADER_H))
            (1..maxSection).forEach { s ->
                Box(Modifier.height(ROW_H), contentAlignment = Alignment.Center) {
                    Text("$s", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        // 星期列
        (1..7).forEach { day ->
            Column(Modifier.width(CELL_W)) {
                Box(Modifier.height(HEADER_H), contentAlignment = Alignment.Center) {
                    Text(
                        DAY_LABELS[day - 1],
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                (1..maxSection).forEach { s ->
                    val covering = visible.filter {
                        it.slot.dayOfWeek == day && s in it.slot.startSection..it.slot.endSection
                    }
                    val starts = covering.filter { it.slot.startSection == s }
                    val bg = covering.firstOrNull()?.let { colorOf[it.cws.course.id] }
                    Box(
                        Modifier
                            .height(ROW_H)
                            .fillMaxWidth()
                            .padding(1.dp)
                            .background(
                                if (covering.isEmpty()) Color.Transparent
                                else (bg ?: Color(0xFFE0E0E0)).copy(alpha = 0.6f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable(enabled = starts.isNotEmpty()) {
                                starts.firstOrNull()?.let { onCourseClick(it.cws) }
                            }
                    ) {
                        if (starts.isNotEmpty()) {
                            Column(Modifier.fillMaxSize()) {
                                starts.forEach { item ->
                                    Text(
                                        item.cws.course.name,
                                        fontSize = 9.sp,
                                        lineHeight = 11.sp,
                                        maxLines = 3,
                                        modifier = Modifier.padding(horizontal = 3.dp)
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

// ─────────────────────────── 导入预览 ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportPreview(
    session: ImportSession,
    duplicateOf: ImportRecord?,
    saving: Boolean,
    onEditSlot: (Int, SlotEdit) -> Unit,
    onDeleteSlot: (Int) -> Unit,
    onDeleteCourse: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    var editing by remember { mutableStateOf<Pair<Int, ParsedSlot>?>(null) }
    val grouped = session.slots.mapIndexed { i, s -> i to s }.groupBy { it.second.courseName }

    Scaffold(
        topBar = { TopAppBar(title = { Text("解析结果预览") }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("文件:${session.fileName}", style = MaterialTheme.typography.bodySmall)
                    Text("学期:${session.parsed.semester ?: "未识别"}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "共 ${grouped.size} 门课程、${session.slots.size} 个时段 · 待确认 ${session.slots.count { it.needsConfirm }} 项",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "标「待确认」的条目表示文件中没有给出确定信息,可点开修正或删除。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (duplicateOf != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "该课表内容与上次导入相同(${Dates.toLocalDate(duplicateOf.importedAt)}):" +
                                "继续保存会替换上次导入的课程,手动添加的课程保留。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                grouped.forEach { (courseName, entries) ->
                    item(key = "course-$courseName") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                courseName.ifBlank { "(未识别课程名)" },
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDeleteCourse(courseName) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除该课程")
                            }
                        }
                    }
                    entries.forEach { (index, slot) ->
                        item(key = "slot-$index") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "${DAY_LABELS[slot.dayOfWeek - 1]} 第${slot.startSection}-${slot.endSection}节 · ${slot.weeksCanonical}周",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        val meta = listOfNotNull(
                                            slot.typeLabel,
                                            slot.room?.let { "场地:$it" },
                                            slot.teacher?.let { "教师:$it" }
                                        ).joinToString(" · ")
                                        if (meta.isNotEmpty()) {
                                            Text(meta, fontSize = 10.sp, style = MaterialTheme.typography.labelSmall)
                                        }
                                        if (slot.needsConfirm) {
                                            Text(
                                                "待确认:" + slot.confirmNotes.joinToString("、"),
                                                fontSize = 10.sp,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    IconButton(onClick = { editing = index to slot }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "编辑")
                                    }
                                    IconButton(onClick = { onDeleteSlot(index) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "删除时段")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f), enabled = !saving) {
                    Text("取消导入")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = !saving && session.slots.isNotEmpty()
                ) { Text(if (saving) "保存中…" else "确认保存") }
            }
        }
    }

    editing?.let { pair ->
        val slot = pair.second
        SlotEditorDialog(
            initial = SlotEdit(
                name = slot.courseName,
                day = slot.dayOfWeek,
                startSection = slot.startSection,
                endSection = slot.endSection,
                weeksText = slot.weeksRaw,
                room = slot.room,
                teacher = slot.teacher
            ),
            title = "校对该时段",
            confirmLabel = "确定",
            onDismiss = { editing = null },
            onSave = { edit ->
                onEditSlot(pair.first, edit)
                editing = null
            }
        )
    }
}

// ─────────────────────────── 时段编辑器(共用) ───────────────────────────

@Composable
private fun SlotEditorDialog(
    initial: SlotEdit,
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSave: (SlotEdit) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var day by remember { mutableStateOf(initial.day) }
    var startText by remember { mutableStateOf(initial.startSection.toString()) }
    var endText by remember { mutableStateOf(initial.endSection.toString()) }
    var weeksText by remember { mutableStateOf(initial.weeksText) }
    var room by remember { mutableStateOf(initial.room ?: "") }
    var teacher by remember { mutableStateOf(initial.teacher ?: "") }

    val weeksParsed = Weeks.parse(weeksText)
    val start = startText.toIntOrNull()
    val end = endText.toIntOrNull()
    val valid = name.isNotBlank() && start != null && end != null &&
        start in 1..MAX_SECTION && end >= start && end <= MAX_SECTION

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("课程名(必填)") }, singleLine = true
                )
                Text("星期", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { d ->
                        Surface(
                            color = if (d == day) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { day = d }
                        ) {
                            Text(
                                DAY_LABELS[d - 1],
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("起始节") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("结束节") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = weeksText, onValueChange = { weeksText = it },
                    label = { Text("周次(如 1-16周 / 10-12周(双))") }, singleLine = true
                )
                Text(
                    when {
                        weeksText.isBlank() -> "周次未设置:该时段只在「全部周次」视图显示"
                        weeksParsed != null -> "解析:共 ${weeksParsed.expanded.size} 周(${weeksParsed.canonical})"
                        else -> "未能识别周次:将原样保存并标记待确认"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (weeksText.isNotBlank() && weeksParsed == null)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("场地") }, singleLine = true)
                OutlinedTextField(value = teacher, onValueChange = { teacher = it }, label = { Text("教师") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        SlotEdit(
                            name = name,
                            day = day,
                            startSection = start ?: 1,
                            endSection = end ?: (start ?: 1),
                            weeksText = weeksText,
                            room = room,
                            teacher = teacher
                        )
                    )
                }
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ─────────────────────────── 课程详情 ───────────────────────────

@Composable
private fun CourseDetailDialog(
    cws: CourseWithSlots,
    onDismiss: () -> Unit,
    onEditBasic: () -> Unit,
    onEditSlot: (Int, SlotEdit) -> Unit,
    onDeleteSlot: (Int) -> Unit,
    onDeleteCourse: () -> Unit
) {
    var editing by remember { mutableStateOf<Pair<Int, CourseSlot>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(cws.course.name) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    listOfNotNull(
                        cws.course.typeLabel,
                        if (cws.course.manual) "手动添加" else "自课表导入",
                        cws.course.campus
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall
                )
                cws.slots.forEachIndexed { i, s ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${DAY_LABELS[s.dayOfWeek - 1]} 第${s.startSection}-${s.endSection}节 · ${s.weeksCanonical}周",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                val meta = listOfNotNull(
                                    s.typeLabel,
                                    s.room?.let { "场地:$it" },
                                    s.teacher?.let { "教师:$it" }
                                ).joinToString(" · ")
                                if (meta.isNotEmpty()) {
                                    Text(meta, fontSize = 10.sp, style = MaterialTheme.typography.labelSmall)
                                }
                                if (s.needsConfirm && !s.confirmNotes.isNullOrBlank()) {
                                    Text(
                                        "待确认:" + s.confirmNotes,
                                        fontSize = 10.sp,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            IconButton(onClick = { editing = i to s }) {
                                Icon(Icons.Filled.Edit, contentDescription = "编辑时段")
                            }
                            IconButton(onClick = { onDeleteSlot(i) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除时段")
                            }
                        }
                    }
                }
                if (cws.slots.isEmpty()) {
                    Text("该课程暂无时段", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onEditBasic) { Text("编辑课程信息") } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) { Text("删除课程") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )

    editing?.let { pair ->
        val slot = pair.second
        SlotEditorDialog(
            initial = SlotEdit(
                name = cws.course.name,
                day = slot.dayOfWeek,
                startSection = slot.startSection,
                endSection = slot.endSection,
                weeksText = slot.weeksRaw,
                room = slot.room,
                teacher = slot.teacher
            ),
            title = "编辑时段",
            confirmLabel = "保存",
            onDismiss = { editing = null },
            onSave = { edit ->
                onEditSlot(pair.first, edit)
                editing = null
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除课程") },
            text = { Text("确定删除「${cws.course.name}」及其全部 ${cws.slots.size} 个时段?此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteCourse()
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}
