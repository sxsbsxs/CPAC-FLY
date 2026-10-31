package com.cpacfly.detection

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads the free generic MediaPipe COCO model once into app-private storage. */
object ModelInstaller {
    const val MODEL_URL = "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite"
    const val MODEL_SHA256 = "40338edf5ec70d43e318b0a716a84d4564cd1802759a7a07170c7e43796dbf58"
    private const val MODEL_FILE_NAME = "efficientdet_lite0.tflite"

    suspend fun ensureInstalled(context: Context, onProgress: (Long, Long) -> Unit = { _, _ -> }): File =
        withContext(Dispatchers.IO) {
            val directory = File(context.filesDir, "models").apply { mkdirs() }
            val destination = File(directory, MODEL_FILE_NAME)
            if (destination.isFile && sha256(destination) == MODEL_SHA256) return@withContext destination
            val temporary = File(directory, "$MODEL_FILE_NAME.download")
            temporary.delete()
            val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            try {
                connection.connect()
                if (connection.responseCode !in 200..299) throw IllegalStateException("模型服务器返回 HTTP ${connection.responseCode}")
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            onProgress(downloaded, total)
                        }
                    }
                }
                if (sha256(temporary) != MODEL_SHA256) throw IllegalStateException("模型校验失败，文件已丢弃，请重试")
                if (destination.exists() && !destination.delete()) throw IllegalStateException("无法替换旧模型文件")
                if (!temporary.renameTo(destination)) throw IllegalStateException("无法保存模型到本地存储")
                destination
            } catch (failure: Exception) {
                temporary.delete()
                throw failure
            } finally {
                connection.disconnect()
            }
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
