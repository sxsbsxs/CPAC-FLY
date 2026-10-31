package com.cpacfly.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
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
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

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
                    if (flightActive) {
                        Button(onClick = {
                            elapsedFlightMs = SystemClock.elapsedRealtime() - flightStartMs
                            flightActive = false
                        }) { Text("标记落地 · 停止计时") }
                    } else {
                        Button(onClick = {
                            flightStartMs = SystemClock.elapsedRealtime()
                            elapsedFlightMs = 0L
                            flightActive = true
                        }) { Text("标记出手 · 开始计时") }
                    }
                    Text("这是手动计时基线；自动识别出手和落地事件尚未接入。", style = MaterialTheme.typography.bodySmall)
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

                                latestDetections = if (detections.isEmpty()) {
                                    "当前帧没有检测到 COCO 目标"
                                } else {
                                    detections.sortedByDescending { it.score }
                                        .take(5)
                                        .joinToString("\n") {
                                            "${it.label} · ${(it.score * 100).toInt()}%"
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
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                        status = "后置摄像头已启动，正在加载检测模型"
                    } catch (error: Throwable) {
                        status = "摄像头启动失败：${error.message ?: error.javaClass.simpleName}"
                    }
                }, mainExecutor)

                onDispose {
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
