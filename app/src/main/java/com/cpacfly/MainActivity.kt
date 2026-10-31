package com.cpacfly

import android.os.Bundle
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.cpacfly.camera.CameraScreen
import com.cpacfly.records.FlightSession
import com.cpacfly.records.FlightSessionStore
import com.cpacfly.records.FlightMeasurementStore
import com.cpacfly.records.PaperAircraft
import com.cpacfly.records.PaperAircraftStore
import com.cpacfly.video.VideoFrameTimeline
import java.text.DateFormat
import java.util.Date
import java.io.File
import com.cpacfly.detection.ModelInstaller


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppBootstrap()
                }
            }
        }
    }
}

@Composable
private fun AppBootstrap() {
    val context = LocalContext.current
    var modelFile by remember { mutableStateOf<File?>(null) }
    var resolved by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf("首次启动正在准备本地 AI 模型…") }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(retryKey) {
        loading = true; resolved = false; error = null; progress = 0f
        message = "正在检查本地模型…"
        try {
            modelFile = ModelInstaller.ensureInstalled(context) { downloaded, total ->
                progress = if (total > 0L) downloaded.toFloat() / total else 0f
                message = if (total > 0L) "正在下载模型：${downloaded / 1_048_576} / ${total / 1_048_576} MB" else "正在下载本地模型…"
            }
            message = "本地 AI 模型已就绪"
            resolved = true
        } catch (failure: Exception) { error = failure.message ?: "模型下载失败" }
        finally { loading = false }
    }
    if (resolved) {
        var showCamera by remember { mutableStateOf(false) }
        var showRecords by remember { mutableStateOf(false) }
        var showAircraft by remember { mutableStateOf(false) }
        var reviewSessionId by remember { mutableStateOf<String?>(null) }
        when {
            showCamera -> CameraScreen(onBack = { showCamera = false }, modelFile = modelFile)
            reviewSessionId != null -> ReviewScreen(sessionId = reviewSessionId!!, onBack = { reviewSessionId = null })
            showRecords -> RecordsScreen(onBack = { showRecords = false }, onOpenReview = { reviewSessionId = it })
            showAircraft -> AircraftScreen(onBack = { showAircraft = false })
            else -> HomeScreen(onStartFlight = { showCamera = true }, onOpenRecords = { showRecords = true }, onOpenAircraft = { showAircraft = true })
        }
    } else {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("CPAC FLY", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Text("首次启动下载一次免费模型并保存在手机本地。之后无需重复下载，推理不需要服务器或付费 API。")
            Spacer(Modifier.height(20.dp)); Text(message)
            if (loading) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) }
            error?.let {
                Spacer(Modifier.height(12.dp)); Text("模型准备失败：$it", color = MaterialTheme.colorScheme.error)
                Text("检查网络后重试；也可以先进入手动模式，之后再下载模型。")
                Button(onClick = { retryKey++ }) { Text("重试下载") }
                OutlinedButton(onClick = { modelFile = null; resolved = true }) { Text("进入手动模式") }
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
private fun RecordsScreen(onBack: () -> Unit, onOpenReview: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { FlightSessionStore(context) }
    val measurementStore = remember { FlightMeasurementStore(context) }
    var sessions by remember { mutableStateOf(store.all()) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("本地试飞记录", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("返回") }
        }
        TextButton(onClick = { sessions = store.all() }) { Text("刷新记录") }
        val measuredSessions = sessions.filter { it.durationMs > 0L }
        if (measuredSessions.isNotEmpty()) {
            val best = measuredSessions.minOf { it.durationMs }
            val average = measuredSessions.map { it.durationMs }.average()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("训练概览", style = MaterialTheme.typography.titleMedium)
                    Text("试飞 ${sessions.size} 次 · 平均 %.2f 秒 · 最佳 %.2f 秒".format(average / 1000.0, best / 1000.0))
                }
            }
        }
        if (sessions.isEmpty()) {
            Text("还没有试飞记录。完成一次手动计时后，记录会显示在这里。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onSaveNote = { note -> store.updateNote(session.id, note); sessions = store.all() },
                                                onDelete = {
                            session.videoUri?.let { uri -> runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) } }
                            store.delete(session.id)
                            measurementStore.deleteSessionData(session.id)
                            sessions = store.all()
                        },
                        onReview = { onOpenReview(session.id) }
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
private fun ReviewScreen(sessionId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { FlightSessionStore(context) }
    val session = remember(sessionId) { store.all().firstOrNull { it.id == sessionId } }
    var videoDurationMs by remember(sessionId) { mutableLongStateOf(0L) }
    var frameTimesMs by remember(sessionId) { mutableStateOf<List<Long>>(emptyList()) }
    var positionMs by remember(sessionId) { mutableLongStateOf(0L) }
    var releaseMs by remember(sessionId) { mutableStateOf(session?.releaseTimeMs) }
    var landingMs by remember(sessionId) { mutableStateOf(session?.landingTimeMs) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session?.videoUri) {
        val uri = session?.videoUri ?: return@LaunchedEffect
        val index = withContext(Dispatchers.IO) {
            val duration = MediaMetadataRetriever().let { retriever ->
                try {
                    retriever.setDataSource(context, Uri.parse(uri))
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally {
                    retriever.release()
                }
            }
            val timestamps = mutableListOf<Long>()
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, Uri.parse(uri), null)
                val videoTrack = (0 until extractor.trackCount).firstOrNull { track ->
                    extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                }
                if (videoTrack != null) {
                    extractor.selectTrack(videoTrack)
                    var previousPts = -1L
                    while (extractor.sampleTime >= 0L) {
                        val ptsMs = extractor.sampleTime / 1000L
                        if (ptsMs != previousPts) timestamps += ptsMs
                        previousPts = ptsMs
                        if (!extractor.advance()) break
                    }
                }
            } finally {
                extractor.release()
            }
            duration to VideoFrameTimeline.normalize(timestamps)
        }
        videoDurationMs = index.first
        frameTimesMs = index.second
        positionMs = frameTimesMs.firstOrNull() ?: 0L
    }

    val frame = produceState<android.graphics.Bitmap?>(null, session?.videoUri, positionMs) {
        val uri = session?.videoUri ?: return@produceState
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(uri))
                retriever.getFrameAtTime(positionMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
            } catch (_: Exception) {
                null
            } finally {
                retriever.release()
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("录像逐帧复核", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("返回") }
        }
        if (session == null || session.videoUri == null) {
            Text("该记录没有可用录像。")
        } else {
            Text("拖动时间线定位画面，或按视频实际 sample PTS 逐帧前后移动。标记出手帧和落地帧后，按两个视频时间戳之差计算飞行时长。")
            frame.value?.let { bitmap ->
                Image(bitmap.asImageBitmap(), "录像当前帧", Modifier.fillMaxWidth().heightIn(max = 320.dp), contentScale = ContentScale.Fit)
            } ?: Text("正在读取视频帧，或当前录像无法解码。")
            Text("当前位置：%.3f 秒 / %.3f 秒".format(positionMs / 1000.0, videoDurationMs / 1000.0))
            val currentFrameIndex = VideoFrameTimeline.nearestFrameIndex(frameTimesMs, positionMs)
            Text(if (currentFrameIndex >= 0) "视频帧：${currentFrameIndex + 1} / ${frameTimesMs.size}" else "未能读取视频帧时间戳")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = currentFrameIndex > 0,
                    onClick = { positionMs = frameTimesMs[currentFrameIndex - 1] }
                ) { Text("前一帧") }
                OutlinedButton(
                    enabled = currentFrameIndex >= 0 && currentFrameIndex < frameTimesMs.lastIndex,
                    onClick = { positionMs = frameTimesMs[currentFrameIndex + 1] }
                ) { Text("后一帧") }
            }
            Slider(
                value = positionMs.toFloat().coerceIn(0f, videoDurationMs.coerceAtLeast(1L).toFloat()),
                onValueChange = { positionMs = it.toLong() },
                onValueChangeFinished = {
                    VideoFrameTimeline.nearestFrameIndex(frameTimesMs, positionMs).takeIf { it >= 0 }?.let { positionMs = frameTimesMs[it] }
                },
                valueRange = 0f..videoDurationMs.coerceAtLeast(1L).toFloat(),
                enabled = videoDurationMs > 0L
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { releaseMs = positionMs; message = "已标记出手帧" }) { Text("标记出手帧") }
                OutlinedButton(onClick = { landingMs = positionMs; message = "已标记落地帧" }) { Text("标记落地帧") }
            }
            Text("出手：${releaseMs?.let { "%.3f 秒".format(it / 1000.0) } ?: "未标记"}")
            Text("落地：${landingMs?.let { "%.3f 秒".format(it / 1000.0) } ?: "未标记"}")
            if (releaseMs != null && landingMs != null && landingMs!! > releaseMs!!) {
                Text("按视频时间戳计算：%.3f 秒".format((landingMs!! - releaseMs!!) / 1000.0))
            } else {
                Text("请确保落地帧时间晚于出手帧。", color = MaterialTheme.colorScheme.error)
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            Button(
                enabled = releaseMs != null && landingMs != null && landingMs!! > releaseMs!!,
                onClick = {
                    val saved = store.updateTiming(sessionId, releaseMs!!, landingMs!!)
                    message = if (saved) "已保存人工复核时间戳和修正后的飞行时长" else "保存失败，请检查时间戳"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存复核结果") }
            Text(
                "注意：帧时间线来自 MediaExtractor 的视频 sample PTS；预览帧由解码器按最近时间戳提取。不同容器/编码可能存在 sample 与显示帧差异；保存的是人工选定的时间戳，并非自动识别出的真实离手或触地时刻。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: FlightSession,
    onSaveNote: (String) -> Unit,
    onDelete: () -> Unit,
    onReview: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var note by remember(session.id, session.note) { mutableStateOf(session.note) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(DateFormat.getDateTimeInstance().format(Date(session.createdAt)), style = MaterialTheme.typography.titleSmall)
            Text("飞行时间：%.2f 秒".format(session.durationMs / 1000.0))
            session.aircraftName?.let { Text("纸飞机：$it", style = MaterialTheme.typography.bodySmall) }
            if (session.releaseTimeMs != null && session.landingTimeMs != null) {
                Text("已人工复核视频时间戳 · 出手 %.3f 秒 / 落地 %.3f 秒".format(
                    session.releaseTimeMs / 1000.0, session.landingTimeMs / 1000.0
                ), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Text(if (session.videoUri != null) "已关联录像" else "未关联录像", style = MaterialTheme.typography.bodySmall)
            session.videoUri?.let { uri ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { uriHandler.openUri(uri) }) { Text("播放录像") }
                    TextButton(onClick = onReview) { Text("逐帧复核计时") }
                }
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
