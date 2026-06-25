package com.brp.assistant.data.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MINUTES)
        .build()

    private val commonUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val initialUrl = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val fileName = inputData.getString(KEY_FILENAME) ?: return@withContext Result.failure()

        val outputDir = File(applicationContext.filesDir, MODEL_DIR)
        if (!outputDir.exists()) outputDir.mkdirs()

        val outputFile = File(outputDir, fileName)
        val tempFile = File(outputDir, "$fileName.part")

        Log.d("ModelDownloadWorker", "Starting download: $fileName")

        try {
            val directUrl = getDirectDownloadUrl(initialUrl)
            performDownload(directUrl, tempFile)

            if (tempFile.exists() && tempFile.length() > 1_000_000) {
                if (outputFile.exists()) outputFile.delete()
                if (tempFile.renameTo(outputFile)) {
                    Result.success(Data.Builder().putString(KEY_FILEPATH, outputFile.absolutePath).build())
                } else {
                    tempFile.copyTo(outputFile, overwrite = true)
                    tempFile.delete()
                    Result.success(Data.Builder().putString(KEY_FILEPATH, outputFile.absolutePath).build())
                }
            } else {
                Result.failure(Data.Builder().putString("error", "Файл поврежден или пуст").build())
            }
        } catch (e: Exception) {
            Log.e("ModelDownloadWorker", "Download failed: ${e.message}")
            if (e.message?.contains("401") == true || e.message?.contains("403") == true) {
                Result.failure(Data.Builder().putString("error", "Доступ ограничен (401/403). Попробуйте модель Qwen или перезагрузите сеть.").build())
            } else if (runAttemptCount < 2) {
                Result.retry()
            } else {
                Result.failure(Data.Builder().putString("error", e.message).build())
            }
        }
    }

    private fun getDirectDownloadUrl(initialUrl: String): String {
        val urlWithParam = if (initialUrl.contains("?")) "$initialUrl&download=true" else "$initialUrl?download=true"
        val request = Request.Builder()
            .url(urlWithParam)
            .head()
            .header("User-Agent", commonUserAgent)
            .header("Referer", "https://huggingface.co/")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code in 300..399) {
                return response.header("Location") ?: initialUrl
            }
            if (!response.isSuccessful) {
                val getRequest = Request.Builder()
                    .url(urlWithParam)
                    .header("User-Agent", commonUserAgent)
                    .header("Range", "bytes=0-0")
                    .build()
                client.newCall(getRequest).execute().use { gr ->
                    if (gr.code in 300..399) return gr.header("Location") ?: initialUrl
                }
            }
        }
        return initialUrl
    }

    private suspend fun performDownload(url: String, tempFile: File) {
        if (tempFile.exists() && tempFile.length() < 1024) tempFile.delete()
        val alreadyDownloaded = if (tempFile.exists()) tempFile.length() else 0L

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", commonUserAgent)
            .header("Referer", "https://huggingface.co/")
            .apply {
                if (alreadyDownloaded > 0) header("Range", "bytes=$alreadyDownloaded-")
            }
            .build()

        val downloadClient = client.newBuilder().followRedirects(true).build()
        downloadClient.newCall(request).execute().use { response ->
            if (response.code == 416) {
                tempFile.delete()
                return performDownload(url, tempFile)
            }
            if (response.code == 401 || response.code == 403) {
                if (alreadyDownloaded > 0) {
                    tempFile.delete()
                    return performDownload(url, tempFile)
                }
                throw IOException("Access Denied (${response.code})")
            }
            if (!response.isSuccessful && response.code != 206) throw IOException("Server Error ${response.code}")

            val body = response.body ?: throw IOException("Empty body")
            val total = if (response.code == 206) alreadyDownloaded + body.contentLength() else body.contentLength()
            val appendMode = response.code == 206

            // FIX #1: единственный поток записи — больше нет двойного FileOutputStream
            FileOutputStream(tempFile, appendMode).use { outputStream ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    var downloaded = if (appendMode) alreadyDownloaded else 0L
                    var read: Int
                    var lastUpdate = 0L

                    while (input.read(buffer).also { read = it } != -1) {
                        if (isStopped) throw IOException("Canceled")
                        outputStream.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 1000) {
                            val progress = if (total > 0) downloaded.toFloat() / total else -1f
                            setProgress(
                                Data.Builder()
                                    .putFloat(KEY_PROGRESS, progress)
                                    .putLong(KEY_DOWNLOADED, downloaded)
                                    .putLong(KEY_SIZE, total)
                                    .build()
                            )
                            lastUpdate = now
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val MODEL_DIR = "models"
        const val KEY_URL = "url"
        const val KEY_FILENAME = "fileName"
        const val KEY_SIZE = "size"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_FILEPATH = "filePath"

        fun buildWorkData(url: String, fileName: String, size: Long): Data {
            return Data.Builder()
                .putString(KEY_URL, url)
                .putString(KEY_FILENAME, fileName)
                .putLong(KEY_SIZE, size)
                .build()
        }
    }
}
