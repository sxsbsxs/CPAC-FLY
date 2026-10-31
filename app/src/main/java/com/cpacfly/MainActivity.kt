package com.cpacfly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.cpacfly.camera.CameraScreen
import com.cpacfly.records.FlightSession
import com.cpacfly.records.FlightSessionStore
import com.cpacfly.records.PaperAircraft
import com.cpacfly.records.PaperAircraftStore
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var showCamera by remember { mutableStateOf(false) }
                    var showRecords by remember { mutableStateOf(false) }
                    var showAircraft by remember { mutableStateOf(false) }
                    when {
                        showCamera -> CameraScreen(onBack = { showCamera = false })
                        showRecords -> RecordsScreen(onBack = { showRecords = false })
                        showAircraft -> AircraftScreen(onBack = { showAircraft = false })
                        else -> HomeScreen(
                            onStartFlight = { showCamera = true },
                            onOpenRecords = { showRecords = true },
                            onOpenAircraft = { showAircraft = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onStartFlight: () -> Unit, onOpenRecords: () -> Unit, onOpenAircraft: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("CPAC FLY", style = MaterialTheme.typography.headlineLarge)
            Text("纸飞机智能飞行计时与训练分析", style = MaterialTheme.typography.bodyLarge)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("准备好试飞了吗？", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("固定手机机位，让纸飞机完整进入镜头。首版使用手机内置摄像头。")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onStartFlight, modifier = Modifier.fillMaxWidth()) {
                        Text("打开摄像头")
                    }
                }
            }
        }
        item { Text("训练工具", style = MaterialTheme.typography.titleLarge) }
        item { FeatureCard("飞行分析", "录像保存、手动计时与后续轨迹分析") }
        item { OutlinedButton(onClick = onOpenAircraft) { Text("管理我的纸飞机") } }
        item { FeatureCard("训练记录", "本地保存试飞时长和关联视频") }
        item { OutlinedButton(onClick = onOpenRecords) { Text("查看训练记录") } }
        item {
            Text(
                "开发预览版 · 当前为通用物体检测实验，不代表纸飞机识别准确",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun RecordsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { FlightSessionStore(context) }
    var sessions by remember { mutableStateOf(store.all()) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("本地试飞记录", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("返回") }
        }
        TextButton(onClick = { sessions = store.all() }) { Text("刷新记录") }
        if (sessions.isEmpty()) {
            Text("还没有试飞记录。完成一次手动计时后，记录会显示在这里。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onSaveNote = { note -> store.updateNote(session.id, note); sessions = store.all() },
                        onDelete = { store.delete(session.id); sessions = store.all() }
                    )
                }
            }
        }
    }
}


@Composable
private fun AircraftScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PaperAircraftStore(context) }
    var profiles by remember { mutableStateOf(store.all()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var paperType by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var wingspan by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("我的纸飞机", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("返回") }
        }
        OutlinedTextField(name, { name = it; error = null }, label = { Text("飞机名称（必填）") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(paperType, { paperType = it }, label = { Text("纸张类型/克重") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(weight, { weight = it }, label = { Text("重量（克）") }, modifier = Modifier.weight(1f))
            OutlinedTextField(wingspan, { wingspan = it }, label = { Text("翼展（毫米）") }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(notes, { notes = it }, label = { Text("折叠参数与备注") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val w = weight.toDoubleOrNull()
                val span = wingspan.toDoubleOrNull()
                if (name.isBlank()) {
                    error = "请填写飞机名称"
                } else if ((weight.isNotBlank() && (w == null || w <= 0.0)) || (wingspan.isNotBlank() && (span == null || span <= 0.0))) {
                    error = "重量和翼展必须是大于 0 的数字"
                } else {
                    runCatching {
                        store.save(PaperAircraft(editingId ?: java.util.UUID.randomUUID().toString(), name, paperType, w, span, notes))
                    }.onSuccess {
                        profiles = store.all()
                        editingId = null; name = ""; paperType = ""; weight = ""; wingspan = ""; notes = ""; error = null
                    }.onFailure { error = it.message ?: "保存失败" }
                }
            }) { Text(if (editingId == null) "添加纸飞机" else "保存修改") }
            TextButton(onClick = { editingId = null; name = ""; paperType = ""; weight = ""; wingspan = ""; notes = ""; error = null }) { Text("清空") }
        }
        Divider(Modifier.padding(vertical = 8.dp))
        Text("已保存 ${profiles.size} 架", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles, key = { it.id }) { profile ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium)
                        Text(listOf(profile.paperType.takeIf { it.isNotBlank() }, profile.weightGrams?.let { "$it 克" }, profile.wingspanMm?.let { "翼展 $it 毫米" }).filterNotNull().joinToString(" · "))
                        if (profile.notes.isNotBlank()) Text(profile.notes)
                        Row {
                            TextButton(onClick = {
                                editingId = profile.id; name = profile.name; paperType = profile.paperType
                                weight = profile.weightGrams?.toString() ?: ""; wingspan = profile.wingspanMm?.toString() ?: ""; notes = profile.notes
                            }) { Text("编辑") }
                            TextButton(onClick = { store.delete(profile.id); profiles = store.all() }) { Text("删除") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: FlightSession,
    onSaveNote: (String) -> Unit,
    onDelete: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var note by remember(session.id, session.note) { mutableStateOf(session.note) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(DateFormat.getDateTimeInstance().format(Date(session.createdAt)), style = MaterialTheme.typography.titleSmall)
            Text("飞行时间：%.2f 秒".format(session.durationMs / 1000.0))
            Text(if (session.videoUri != null) "已关联录像" else "未关联录像", style = MaterialTheme.typography.bodySmall)
            session.videoUri?.let { uri ->
                TextButton(onClick = { uriHandler.openUri(uri) }) { Text("播放录像") }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 1000) note = it },
                label = { Text("试飞备注（纸型、折法、环境等）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSaveNote(note) }) { Text("保存备注") }
                TextButton(onClick = onDelete) { Text("删除记录") }
            }
        }
    }
}

@Composable
private fun FeatureCard(title: String, description: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
