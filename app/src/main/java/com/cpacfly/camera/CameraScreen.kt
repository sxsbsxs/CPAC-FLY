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
import androidx.compose.foundation.background
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
fun CameraScreen() {
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
    var latestDetections by remember { mutableStateOf("等待图像分析") }
    var liveDetectionCount by remember { mutableIntStateOf(0) }
    var cameraMotionText by remember { mutableStateOf("等待相机运动分析") }
    var fps by remember { mutableStateOf(0f) }
    var selectedQualityLabel by remember { mutableStateOf(context.getSharedPreferences("cpac_fly_settings", 0).getString("video_quality", "HD 720p") ?: "HD 720p") }
    var selectedFps by remember { mutableIntStateOf(context.getSharedPreferences("cpac_fly_settings", 0).getInt("video_fps", 30)) }
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
        modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)
    ) {
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



            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("CPAC FLY", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                var qualityMenu by remember { mutableStateOf(false) }
                                var fpsMenu by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(enabled = !isRecording && !flightActive, onClick = { qualityMenu = true }, colors = ButtonDefaults.textButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)) { Text(selectedQualityLabel) }
                                    DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                                        listOf("UHD 4K", "FHD 1080p", "HD 720p", "SD 480p").forEach { label ->
                                            DropdownMenuItem(text = { Text(label) }, onClick = { selectedQualityLabel = label; context.getSharedPreferences("cpac_fly_settings", 0).edit().putString("video_quality", label).apply(); qualityMenu = false })
                                        }
                                    }
                                }
                                Box {
                                    TextButton(enabled = !isRecording && !flightActive, onClick = { fpsMenu = true }, colors = ButtonDefaults.textButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)) { Text("${selectedFps} fps") }
                                    DropdownMenu(expanded = fpsMenu, onDismissRequest = { fpsMenu = false }) {
                                        listOf(24, 30, 60).forEach { rate ->
                                            DropdownMenuItem(text = { Text("${rate} fps") }, onClick = { selectedFps = rate; context.getSharedPreferences("cpac_fly_settings", 0).edit().putInt("video_fps", rate).apply(); fpsMenu = false })
                                        }
                                    }
                                }
                            }
                        }
                        Surface(color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.62f), shape = MaterialTheme.shapes.small) {
                            Text(
                                when { isRecording -> "● 录像中"; flightActive -> "计时中"; else -> "准备就绪" },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.82f), shape = MaterialTheme.shapes.small) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("纸飞机识别状态", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelLarge)
                                Text("专用模型：待完成本地权重验证；当前使用通用目标检测", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodySmall)
                                Text(if (liveDetectionCount > 0) "实时画面：检测到 $liveDetectionCount 个动态目标（尚未确认是纸飞机）" else "实时画面：暂未检测到动态目标", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodySmall)
                                Text("图像分析：%.1f FPS".format(fps), color = androidx.compose.ui.graphics.Color.LightGray, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (flightActive && outOfViewCountdownMs > 0L) {
                            Surface(color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f), shape = MaterialTheme.shapes.small) {
                                Text("目标离开画面 · ${(outOfViewCountdownMs / 1000L).toInt()} 秒后停止", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = androidx.compose.ui.graphics.Color.White)
                            }
                        }
                        Surface(color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.62f), shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.padding(horizontal = 24.dp, vertical = 10.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text("飞行时间", color = androidx.compose.ui.graphics.Color.LightGray, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "%02d:%05.2f".format(elapsedFlightMs / 60_000L, (elapsedFlightMs % 60_000L) / 1000.0),
                                    color = androidx.compose.ui.graphics.Color.White,
                                    style = MaterialTheme.typography.displayLarge
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            TextButton(
                                enabled = recordingAvailable || isRecording,
                                colors = ButtonDefaults.textButtonColors(contentColor = androidx.compose.ui.graphics.Color.White),
                                onClick = {
                                    if (isRecording) recording?.stop() else {
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
                                }
                            ) { Text(if (isRecording) "停止录像" else "录像", style = MaterialTheme.typography.labelLarge) }
                            Button(
                                modifier = Modifier.size(width = 196.dp, height = 64.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (flightActive) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFFE5E5E5),
                                    contentColor = androidx.compose.ui.graphics.Color.Black
                                ),
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
                            ) {
                                Text(if (flightActive) "停止并保存" else "开始试飞", style = MaterialTheme.typography.titleMedium)
                            }
                            TextButton(onClick = { settingsExpanded = true }, colors = ButtonDefaults.textButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)) { Text("设置") }
                        }
                        Text("录像 ${selectedQualityLabel} · ${selectedFps} fps", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (settingsExpanded) {
                AlertDialog(
                    onDismissRequest = { settingsExpanded = false },
                    colors = AlertDialogDefaults.colors(
                        containerColor = androidx.compose.ui.graphics.Color.Black,
                        titleContentColor = androidx.compose.ui.graphics.Color.White,
                        textContentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    title = { Text("试飞设置") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("飞出画面自动停止：${autoStopSeconds.toInt()} 秒")
                            Slider(value = autoStopSeconds, colors = SliderDefaults.colors(
                                thumbColor = androidx.compose.ui.graphics.Color.White,
                                activeTrackColor = androidx.compose.ui.graphics.Color.White,
                                inactiveTrackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f)
                            ), onValueChange = {
                                autoStopSeconds = it
                                autoStopSecondsRef.set(it.toInt())
                                context.getSharedPreferences("cpac_fly_settings", 0).edit().putInt("out_of_view_seconds", it.toInt()).apply()
                                outOfViewController.setTimeoutMs(it.toLong() * 1_000L)
                            }, valueRange = 5f..120f, steps = 22, enabled = !flightActive)
                            Text("本次纸飞机")
                            Box {
                                OutlinedButton(onClick = { aircraftProfiles = aircraftStore.all(); aircraftMenuExpanded = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)) { Text(selectedAircraft?.name ?: "不指定机型") }
                                DropdownMenu(expanded = aircraftMenuExpanded, onDismissRequest = { aircraftMenuExpanded = false }, containerColor = androidx.compose.ui.graphics.Color.Black) {
                                    DropdownMenuItem(text = { Text("不指定机型", color = androidx.compose.ui.graphics.Color.White) }, colors = MenuDefaults.itemColors(textColor = androidx.compose.ui.graphics.Color.White), onClick = {
                                        selectedAircraftId = null
                                        context.getSharedPreferences("cpac_fly_settings", 0).edit().remove("selected_aircraft_id").apply()
                                        aircraftMenuExpanded = false
                                    })
                                    aircraftProfiles.forEach { profile ->
                                        DropdownMenuItem(text = { Text(profile.name, color = androidx.compose.ui.graphics.Color.White) }, colors = MenuDefaults.itemColors(textColor = androidx.compose.ui.graphics.Color.White), onClick = {
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
                    confirmButton = { TextButton(onClick = { settingsExpanded = false }, colors = ButtonDefaults.textButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)) { Text("完成") } }
                )
            }

            DisposableEffect(lifecycleOwner, selectedQualityLabel, selectedFps) {
                val providerFuture = ProcessCameraProvider.getInstance(context)
                val mainExecutor = ContextCompat.getMainExecutor(context)
                var boundProvider: ProcessCameraProvider? = null

                providerFuture.addListener({
                    try {
                        val provider = providerFuture.get()
                        boundProvider = provider
                        val previewBuilder = Preview.Builder()
                        androidx.camera.camera2.interop.Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(selectedFps, selectedFps))
                        val preview = previewBuilder.build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val requestedQuality = when (selectedQualityLabel) { "UHD 4K" -> Quality.UHD; "FHD 1080p" -> Quality.FHD; "SD 480p" -> Quality.SD; else -> Quality.HD }
                        val recorder = Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(
                                requestedQuality, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
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
                                    liveDetectionCount = tracked.size
                                    cameraMotionText = if (motion.reliable) {
                                        "平移 Δx=%.3f、Δy=%.3f，置信度 %.2f".format(motion.shiftX, motion.shiftY, motion.confidence)
                                    } else {
                                        "画面纹理不足或估计不可靠（%.2f）".format(motion.confidence)
                                    }
                                    status = "摄像头运行中 · 通用目标检测（非纸飞机专用模型）"
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