package com.cpacfly.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.content.ContentValues
import android.provider.MediaStore
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cpacfly.detection.MlKitStreamObjectDetector
import com.cpacfly.tracking.DetectionTracker
import com.cpacfly.vision.GlobalMotionEstimator
import com.cpacfly.tracking.DetectionBox
import com.cpacfly.tracking.DetectionCandidate
import com.cpacfly.records.FlightSessionStore
import com.cpacfly.records.PaperAircraftStore
import com.cpacfly.timing.OutOfViewStopController
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

@Composable
fun CameraScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("等待摄像头启动") }
    var latestDetections by remember { mutableStateOf("尚无检测结果") }
    var cameraMotionText by remember { mutableStateOf("等待相机运动分析") }
    var fps by remember { mutableStateOf(0f) }
    var flightActive by remember { mutableStateOf(false) }
    var flightStartMs by remember { mutableStateOf(0L) }
    var elapsedFlightMs by remember { mutableStateOf(0L) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingAvailable by remember { mutableStateOf(false) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var pendingSessionId by remember { mutableStateOf<String?>(null) }
    var finalizedVideoUri by remember { mutableStateOf<String?>(null) }
    var autoStopSeconds by remember { mutableFloatStateOf(context.getSharedPreferences("cpac_fly_settings", 0).getInt("out_of_view_seconds", 15).toFloat()) }
    var outOfViewCountdownMs by remember { mutableLongStateOf(0L) }
    val outOfViewController = remember { OutOfViewStopController(15_000L) }
    val flightActiveRef = remember { AtomicBoolean(false) }
    val flightStartMsRef = remember { AtomicLong(0L) }
    val autoStopSecondsRef = remember { AtomicInteger(autoStopSeconds.toInt()) }
    val recordingRef = remember { AtomicReference<Recording?>(null) }
    val finalizedVideoUriRef = remember { AtomicReference<String?>(null) }
    val pendingSessionIdRef = remember { AtomicReference<String?>(null) }
    val targetWasSeenRef = remember { AtomicBoolean(false) }
    val primaryTrackIdRef = remember { AtomicLong(-1L) }
    val sessionStore = remember { FlightSessionStore(context) }
    val aircraftStore = remember { PaperAircraftStore(context) }
    var aircraftProfiles by remember { mutableStateOf(aircraftStore.all()) }
    var selectedAircraftId by remember { mutableStateOf(context.getSharedPreferences("cpac_fly_settings", 0).getString("selected_aircraft_id", null)) }
    var aircraftMenuExpanded by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }
    val selectedAircraft = aircraftProfiles.firstOrNull { it.id == selectedAircraftId }
    val motionEstimator = remember { GlobalMotionEstimator(searchRadiusPx = 8, sampleStepPx = 6) }

    LaunchedEffect(flightActive, flightStartMs) {
        while (flightActive) {
            elapsedFlightMs = SystemClock.elapsedRealtime() - flightStartMs
            delay(100L)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("实时试飞画面", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onBack) { Text("返回") }
        }

        if (!hasPermission) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (permissionDenied) "需要摄像头权限才能进行试飞分析" else "正在请求摄像头权限")
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("授予摄像头权限")
                    }
                }
            }
        } else {
            val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
            val detectorRef = remember { AtomicReference<MlKitStreamObjectDetector?>(null) }
            val tracker = remember { DetectionTracker() }
            val previewView = remember {
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                }
            }


            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("CPAC FLY", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onBack) { Text("关闭") }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    Surface(
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopStart).padding(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            when { isRecording -> "● 正在录像"; flightActive -> "正在计时"; else -> "准备就绪" },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    if (flightActive && outOfViewCountdownMs > 0L) {
                        Surface(
                            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text("目标离开画面 %.1f / %d 秒".format(outOfViewCountdownMs / 1000.0, autoStopSeconds.toInt()), modifier = Modifier.padding(10.dp))
                        }
                    }
                }
                Text("%.2f 秒".format(elapsedFlightMs / 1000.0), modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.displaySmall)
                Button(
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    onClick = {
                        if (flightActive) {
                            elapsedFlightMs = SystemClock.elapsedRealtime() - flightStartMsRef.get()
                            flightActive = false
                            flightActiveRef.set(false)
                            outOfViewController.reset()
                            targetWasSeenRef.set(false)
                            primaryTrackIdRef.set(-1L)
                            outOfViewCountdownMs = 0L
                            val savedId = sessionStore.add(elapsedFlightMs, aircraftId = selectedAircraftId, aircraftName = selectedAircraft?.name)
                            pendingSessionId = savedId
                            pendingSessionIdRef.set(savedId)
                            finalizedVideoUriRef.get()?.let { sessionStore.attachVideo(savedId, it) }
                            recordingRef.getAndSet(null)?.stop()
                            recording = null
                        } else {
                            flightStartMs = SystemClock.elapsedRealtime()
                            flightStartMsRef.set(flightStartMs)
                            elapsedFlightMs = 0L
                            outOfViewController.setTimeoutMs(autoStopSeconds.toLong() * 1_000L)
                            outOfViewController.reset()
                            targetWasSeenRef.set(false)
                            primaryTrackIdRef.set(-1L)
                            flightActiveRef.set(true)
                            flightActive = true
                        }
                    }
                ) { Text(if (flightActive) "落地 · 结束计时" else "出手 · 开始计时", style = MaterialTheme.typography.titleMedium) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TextButton(enabled = recordingAvailable || isRecording, onClick = {
                        if (isRecording) {
                            recording?.stop()
                        } else {
                            val capture = videoCapture ?: return@TextButton
                            finalizedVideoUri = null
                            finalizedVideoUriRef.set(null)
                            val values = ContentValues().apply {
                                put(MediaStore.Video.Media.DISPLAY_NAME, "CPAC_FLY_${System.currentTimeMillis()}.mp4")
                                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CPAC FLY")
                            }
                            val output = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
                            recording = capture.output.prepareRecording(context, output).start(ContextCompat.getMainExecutor(context)) { event ->
                                if (event is VideoRecordEvent.Finalize) {
                                    isRecording = false
                                    if (!event.hasError()) {
                                        finalizedVideoUri = event.outputResults.outputUri.toString()
                                        finalizedVideoUriRef.set(finalizedVideoUri)
                                        pendingSessionIdRef.get()?.let { sessionStore.attachVideo(it, finalizedVideoUri!!) }
                                        status = "录像已保存到 Movies/CPAC FLY"
                                    } else status = "录像保存失败：${event.error}"
                                    recording = null
                                    recordingRef.set(null)
                                }
                            }
                            recordingRef.set(recording)
                            isRecording = true
                            status = "正在录制视频（无声音）"
                        }
                    }) { Text(if (isRecording) "停止录像" else "录制视频") }
                    TextButton(onClick = { settingsExpanded = true }) { Text("设置 · ${selectedAircraft?.name ?: "未选机型"}") }
                    if (!recordingAvailable) Text("设备不支持录像", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (settingsExpanded) {
                AlertDialog(
                    onDismissRequest = { settingsExpanded = false },
                    title = { Text("试飞设置") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("飞出画面自动停止：${autoStopSeconds.toInt()} 秒")
                            Slider(value = autoStopSeconds, onValueChange = {
                                autoStopSeconds = it
                                autoStopSecondsRef.set(it.toInt())
                                context.getSharedPreferences("cpac_fly_settings", 0).edit().putInt("out_of_view_seconds", it.toInt()).apply()
                                outOfViewController.setTimeoutMs(it.toLong() * 1_000L)
                            }, valueRange = 5f..120f, steps = 22, enabled = !flightActive)
                            Text("本次纸飞机")
                            Box {
                                OutlinedButton(onClick = { aircraftProfiles = aircraftStore.all(); aircraftMenuExpanded = true }) { Text(selectedAircraft?.name ?: "不指定机型") }
                                DropdownMenu(expanded = aircraftMenuExpanded, onDismissRequest = { aircraftMenuExpanded = false }) {
                                    DropdownMenuItem(text = { Text("不指定机型") }, onClick = {
                                        selectedAircraftId = null
                                        context.getSharedPreferences("cpac_fly_settings", 0).edit().remove("selected_aircraft_id").apply()
                                        aircraftMenuExpanded = false
                                    })
                                    aircraftProfiles.forEach { profile ->
                                        DropdownMenuItem(text = { Text(profile.name) }, onClick = {
                                            selectedAircraftId = profile.id
                                            context.getSharedPreferences("cpac_fly_settings", 0).edit().putString("selected_aircraft_id", profile.id).apply()
                                            aircraftMenuExpanded = false
                                        })
                                    }
                                }
                            }
                            Text("录像保存在系统相册 Movies/CPAC FLY，不录制声音。", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = { TextButton(onClick = { settingsExpanded = false }) { Text("完成") } }
                )
            }

            DisposableEffect(lifecycleOwner) {
                val providerFuture = ProcessCameraProvider.getInstance(context)
                val mainExecutor = ContextCompat.getMainExecutor(context)
                var boundProvider: ProcessCameraProvider? = null

                providerFuture.addListener({
                    try {
                        val provider = providerFuture.get()
                        boundProvider = provider
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val recorder = Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(
                                Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                            ))
                            .build()
                        val capture = VideoCapture.withOutput(recorder)
                        val analysis = ImageAnalysis.Builder()
                            .setTargetResolution(android.util.Size(640, 480))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()

                        var frameCount = 0
                        var fpsWindowStart = SystemClock.elapsedRealtime()
                        analysis.setAnalyzer(cameraExecutor) { image ->
                            var bitmap: Bitmap? = null
                            try {
                                val frameBitmap = image.toUprightBitmap()
                                bitmap = frameBitmap
                                val motionSmall = Bitmap.createScaledBitmap(frameBitmap, 128, 96, true)
                                val motionArgb = IntArray(128 * 96)
                                motionSmall.getPixels(motionArgb, 0, 128, 0, 0, 128, 96)
                                if (motionSmall !== frameBitmap) motionSmall.recycle()
                                val gray = IntArray(motionArgb.size) { index ->
                                    val pixel = motionArgb[index]
                                    (((pixel shr 16 and 0xff) * 299 + (pixel shr 8 and 0xff) * 587 + (pixel and 0xff) * 114) / 1000)
                                }
                                val motion = motionEstimator.estimate(gray, 128, 96)
                                val detector = detectorRef.get()
                                    ?: MlKitStreamObjectDetector(context.applicationContext).also { detectorRef.set(it) }
                                val timestampMs = image.imageInfo.timestamp / 1_000_000L
                                val detections = detector.detect(frameBitmap, timestampMs)
                                val tracked = tracker.update(detections.map { detection ->
                                    DetectionCandidate(
                                        box = DetectionBox(
                                            detection.box.left, detection.box.top,
                                            detection.box.right, detection.box.bottom
                                        ),
                                        label = detection.label,
                                        score = detection.score,
                                        categoryIndex = detection.categoryIndex,
                                        sourceTrackingId = detection.trackingId
                                    )
                                })

                                // ML Kit tracks generic moving objects, not paper-airplane semantics.
                                // Treat visible tracked objects as the current timing target.
                                val selectedTrackId = primaryTrackIdRef.get()
                                val primaryTarget = if (selectedTrackId >= 0L) {
                                    tracked.firstOrNull { it.trackId == selectedTrackId }
                                } else if (flightActiveRef.get()) {
                                    tracked.maxByOrNull { trackedItem ->
                                        val box = trackedItem.detection.box
                                        (box.right - box.left).coerceAtLeast(0f) * (box.bottom - box.top).coerceAtLeast(0f)
                                    }?.also { primaryTrackIdRef.compareAndSet(-1L, it.trackId) }
                                } else null
                                val targetVisible = primaryTarget != null
                                if (targetVisible) targetWasSeenRef.set(true)
                                if (flightActiveRef.get() && targetWasSeenRef.get()) {
                                    val shouldStop = outOfViewController.onFrame(!targetVisible, timestampMs)
                                    val missingMs = outOfViewController.missingDurationMs(timestampMs) ?: 0L
                                    mainExecutor.execute {
                                        outOfViewCountdownMs = missingMs
                                    }
                                    if (shouldStop) {
                                        mainExecutor.execute {
                                            if (flightActiveRef.compareAndSet(true, false)) {
                                                elapsedFlightMs = SystemClock.elapsedRealtime() - flightStartMsRef.get()
                                                flightActive = false
                                                targetWasSeenRef.set(false)
                                                primaryTrackIdRef.set(-1L)
                                                outOfViewCountdownMs = 0L
                                                val savedId = sessionStore.add(elapsedFlightMs, aircraftId = selectedAircraftId, aircraftName = selectedAircraft?.name)
                                                pendingSessionId = savedId
                                                pendingSessionIdRef.set(savedId)
                                                finalizedVideoUriRef.get()?.let { uri -> sessionStore.attachVideo(savedId, uri) }
                                                recordingRef.getAndSet(null)?.stop()
                                                recording = null
                                                status = "追踪目标连续离开画面达到 ${autoStopSecondsRef.get()} 秒，已自动停止并保存试飞"
                                            }
                                        }
                                    }
                                } else if (!flightActiveRef.get()) {
                                    outOfViewController.reset()
                                }

                                val detectionSummary = if (tracked.isEmpty()) {
                                    "当前帧未检测到可追踪目标"
                                } else {
                                    tracked.sortedByDescending { it.detection.score }
                                        .take(5)
                                        .joinToString("\n") {
                                            "目标 #${it.trackId} · ${it.detection.label} · ${(it.detection.score * 100).toInt()}%"
                                        }
                                }
                                frameCount++
                                val now = SystemClock.elapsedRealtime()
                                var measuredFps: Float? = null
                                if (now - fpsWindowStart >= 1000L) {
                                    measuredFps = frameCount * 1000f / (now - fpsWindowStart)
                                    frameCount = 0
                                    fpsWindowStart = now
                                }
                                mainExecutor.execute {
                                    latestDetections = detectionSummary
                                    cameraMotionText = if (motion.reliable) {
                                        "平移 Δx=%.3f、Δy=%.3f，置信度 %.2f".format(motion.shiftX, motion.shiftY, motion.confidence)
                                    } else {
                                        "画面纹理不足或估计不可靠（%.2f）".format(motion.confidence)
                                    }
                                    status = "摄像头运行中 · ML Kit 本地动态目标追踪"
                                    measuredFps?.let { fps = it }
                                }
                            } catch (error: Throwable) {
                                mainExecutor.execute {
                                    status = "检测暂不可用：${error.message ?: error.javaClass.simpleName}"
                                }
                            } finally {
                                bitmap?.recycle()
                                image.close()
                            }
                        }

                        provider.unbindAll()
                        try {
                            provider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                                preview, analysis, capture
                            )
                            videoCapture = capture
                            recordingAvailable = true
                            status = "后置摄像头已启动，录像功能可用"
                        } catch (videoError: Throwable) {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                            )
                            videoCapture = null
                            recordingAvailable = false
                            status = "预览与分析已启动；录像组合不受支持，已降级"
                        }
                    } catch (error: Throwable) {
                        status = "摄像头启动失败：${error.message ?: error.javaClass.simpleName}"
                    }
                }, mainExecutor)

                onDispose {
                    recording?.stop()
                    boundProvider?.unbindAll()
                    cameraExecutor.execute { detectorRef.getAndSet(null)?.close() }
                    cameraExecutor.shutdown()
                }
            }
        }
    }
}

private fun ImageProxy.toUprightBitmap(): Bitmap {
    require(planes.size == 1) { "CameraX RGBA output must contain one plane" }
    val plane = planes[0]
    val buffer = plane.buffer.duplicate()
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    val argb = IntArray(width * height)
    for (y in 0 until height) {
        val rowStart = y * plane.rowStride
        for (x in 0 until width) {
            val offset = rowStart + x * plane.pixelStride
            val red = bytes[offset].toInt() and 0xff
            val green = bytes[offset + 1].toInt() and 0xff
            val blue = bytes[offset + 2].toInt() and 0xff
            val alpha = bytes[offset + 3].toInt() and 0xff
            argb[y * width + x] =
                (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }

    val raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    raw.setPixels(argb, 0, width, 0, 0, width, height)
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return raw

    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(raw, 0, 0, width, height, matrix, true)
    raw.recycle()
    return rotated
}
