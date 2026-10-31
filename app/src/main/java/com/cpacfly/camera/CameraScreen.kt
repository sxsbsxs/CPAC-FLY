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
import com.cpacfly.detection.MediaPipeObjectDetector
import com.cpacfly.tracking.DetectionTracker
import com.cpacfly.tracking.DetectionBox
import com.cpacfly.tracking.DetectionCandidate
import com.cpacfly.records.FlightSessionStore
import com.cpacfly.timing.OutOfViewStopController
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

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
    val targetWasSeenRef = remember { AtomicBoolean(false) }
    val sessionStore = remember { FlightSessionStore(context) }

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
            val detectorRef = remember { AtomicReference<MediaPipeObjectDetector?>(null) }
            val tracker = remember { DetectionTracker() }
            val previewView = remember {
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                }
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Text("分析帧率：${"%.1f".format(fps)} FPS", style = MaterialTheme.typography.bodySmall)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("飞行计时", style = MaterialTheme.typography.titleMedium)
                    Text("计时：%.2f 秒".format(elapsedFlightMs / 1000.0), style = MaterialTheme.typography.headlineSmall)
                    Text("飞出镜头自动停止：${autoStopSeconds.toInt()} 秒")
                    Slider(
                        value = autoStopSeconds,
                        onValueChange = {
                            autoStopSeconds = it
                            context.getSharedPreferences("cpac_fly_settings", 0).edit().putInt("out_of_view_seconds", it.toInt()).apply()
                            outOfViewController.setTimeoutMs(it.toLong() * 1_000L)
                        },
                        valueRange = 5f..120f,
                        steps = 22,
                        enabled = !flightActive
                    )
                    Text("可选 5 秒至 2 分钟；仅在识别到纸飞机后开始监测连续消失。", style = MaterialTheme.typography.bodySmall)
                    if (flightActive && outOfViewCountdownMs > 0L) {
                        Text("目标已离开画面：%.1f / %d 秒".format(
                            outOfViewCountdownMs / 1000.0, autoStopSeconds.toInt()
                        ), color = MaterialTheme.colorScheme.secondary)
                    }
                    if (flightActive) {
                        Button(onClick = {
                            elapsedFlightMs = SystemClock.elapsedRealtime() - flightStartMs
                            flightActive = false
                            flightActiveRef.set(false)
                            outOfViewController.reset()
                            targetWasSeenRef.set(false)
                            outOfViewCountdownMs = 0L
                            pendingSessionId = sessionStore.add(elapsedFlightMs)
                            finalizedVideoUri?.let { sessionStore.attachVideo(pendingSessionId!!, it) }
                            recording?.stop()
                        }) { Text("标记落地 · 保存本次试飞") }
                    } else {
                        Button(onClick = {
                            flightStartMs = SystemClock.elapsedRealtime()
                            elapsedFlightMs = 0L
                            outOfViewController.setTimeoutMs(autoStopSeconds.toLong() * 1_000L)
                            outOfViewController.reset()
                            targetWasSeenRef.set(false)
                            flightActiveRef.set(true)
                            flightActive = true
                        }) { Text("标记出手 · 开始计时") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = recordingAvailable && !isRecording, onClick = {
                            val capture = videoCapture ?: return@Button
                            finalizedVideoUri = null
                            val values = ContentValues().apply {
                                put(MediaStore.Video.Media.DISPLAY_NAME, "CPAC_FLY_${System.currentTimeMillis()}.mp4")
                                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CPAC FLY")
                            }
                            val output = MediaStoreOutputOptions.Builder(
                                context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            ).setContentValues(values).build()
                            recording = capture.output.prepareRecording(context, output)
                                .start(ContextCompat.getMainExecutor(context)) { event ->
                                    if (event is VideoRecordEvent.Finalize) {
                                        isRecording = false
                                        if (!event.hasError()) {
                                            finalizedVideoUri = event.outputResults.outputUri.toString()
                                            pendingSessionId?.let { sessionStore.attachVideo(it, finalizedVideoUri!!) }
                                            status = "录像已保存到 Movies/CPAC FLY"
                                        } else {
                                            status = "录像保存失败：${event.error}"
                                        }
                                        recording = null
                                    }
                                }
                            isRecording = true
                            status = "正在录制视频（无声音）"
                        }) { Text("开始录像") }
                        OutlinedButton(enabled = isRecording, onClick = { recording?.stop() }) { Text("停止录像") }
                    }
                    Text(
                        if (recordingAvailable) "录像保存到系统媒体库 Movies/CPAC FLY；默认不录音。"
                        else "设备不支持预览、分析和录像同时运行。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("飞出镜头自动停止依赖当前通用模型的 airplane 检测，正式纸飞机识别与真机验证尚未完成。", style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("通用模型检测结果", style = MaterialTheme.typography.titleMedium)
                    Text(latestDetections, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "当前模型识别 COCO 通用类别，并非纸飞机专用模型。即使出现 airplane 标签，也不能视为已可靠识别纸飞机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
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
                                val detector = detectorRef.updateAndGet { existing ->
                                    existing ?: MediaPipeObjectDetector(context.applicationContext)
                                }!!
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
                                        categoryIndex = detection.categoryIndex
                                    )
                                })

                                val airplaneVisible = tracked.any {
                                    it.detection.label.equals("airplane", ignoreCase = true)
                                }
                                if (airplaneVisible) targetWasSeenRef.set(true)
                                if (flightActiveRef.get() && targetWasSeenRef.get()) {
                                    val shouldStop = outOfViewController.onFrame(!airplaneVisible, timestampMs)
                                    val missingMs = outOfViewController.missingDurationMs(timestampMs) ?: 0L
                                    mainExecutor.execute {
                                        outOfViewCountdownMs = missingMs
                                    }
                                    if (shouldStop) {
                                        mainExecutor.execute {
                                            if (flightActive) {
                                                elapsedFlightMs = SystemClock.elapsedRealtime() - flightStartMs
                                                flightActive = false
                                                flightActiveRef.set(false)
                                                targetWasSeenRef.set(false)
                                                outOfViewCountdownMs = 0L
                                                pendingSessionId = sessionStore.add(elapsedFlightMs)
                                                finalizedVideoUri?.let { uri ->
                                                    pendingSessionId?.let { id -> sessionStore.attachVideo(id, uri) }
                                                }
                                                recording?.stop()
                                                status = "纸飞机连续离开画面达到 ${autoStopSeconds.toInt()} 秒，已自动停止并保存试飞"
                                            }
                                        }
                                    }
                                } else if (!flightActiveRef.get()) {
                                    outOfViewController.reset()
                                }

                                latestDetections = if (tracked.isEmpty()) {
                                    "当前帧没有检测到 COCO 目标"
                                } else {
                                    tracked.sortedByDescending { it.detection.score }
                                        .take(5)
                                        .joinToString("\n") {
                                            "目标 #${it.trackId} · ${it.detection.label} · ${(it.detection.score * 100).toInt()}%"
                                        }
                                }
                                status = "摄像头运行中 · 本地 AI 推理"
                                frameCount++
                                val now = SystemClock.elapsedRealtime()
                                if (now - fpsWindowStart >= 1000L) {
                                    fps = frameCount * 1000f / (now - fpsWindowStart)
                                    frameCount = 0
                                    fpsWindowStart = now
                                }
                            } catch (error: Throwable) {
                                status = "检测暂不可用：${error.message ?: error.javaClass.simpleName}"
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
