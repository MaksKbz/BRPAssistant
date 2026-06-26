package com.brp.assistant.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
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

/**
 * Worker для загрузки LLM-моделей в фоновом режиме.
 *
 * Изменения:
 * - Добавлен вызов setForeground() с ForegroundInfo, чтобы Android 12+
 *   (API 31+) не бросал ForegroundServiceDidNotStartInTimeException.
 * - ForegroundInfo содержит прогресс-уведомление с процентами.
 * - Канал CHANNEL_DOWNLOAD создаётся автоматически при первом запуске.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    private val commonUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val initialUrl = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val fileName   = inputData.getString(KEY_FILENAME) ?: return@withContext Result.failure()
        val modelTitle = inputData.getString(KEY_TITLE) ?: fileName

        // Создаём канал уведомлений и запускаем foreground сразу,
        // чтобы Android 12+ не выбросил ForegroundServiceDidNotStartInTimeException.
        createNotificationChannel()
        setForeground(buildForegroundInfo(modelTitle, 0))

        val outputDir = File(applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir, MODEL_DIR)
        if (!outputDir.exists()) outputDir.mkdirs()

        val outputFile = File(outputDir, fileName)
        val tempFile   = File(outputDir, "$fileName.part")

        Log.d(TAG, "Starting download: $fileName from $initialUrl")

        try {
            val directUrl = getDirectDownloadUrl(initialUrl)
            performDownload(directUrl, tempFile, modelTitle)

            val minValidSize = 50L * 1024 * 1024  // 50 MB
            if (tempFile.exists() && tempFile.length() > minValidSize) {
                if (outputFile.exists()) outputFile.delete()
                if (!tempFile.renameTo(outputFile)) {
                    tempFile.copyTo(outputFile, overwrite = true)
                    tempFile.delete()
                }
                setForeground(buildForegroundInfo(modelTitle, 100))
                Result.success(
                    Data.Builder().putString(KEY_FILEPATH, outputFile.absolutePath).build()
                )
            } else {
                tempFile.delete()
                Result.failure(
                    Data.Builder().putString("error", "Файл повреждён или слишком мал (< 50 МБ)").build()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            when {
                e.message?.contains("401") == true || e.message?.contains("403") == true ->
                    Result.failure(
                        Data.Builder().putString("error", "Доступ ограничен (401/403). Попробуйте Qwen или перезагрузите сеть.").build()
                    )
                runAttemptCount < 2 -> Result.retry()
                else -> Result.failure(Data.Builder().putString("error", e.message).build())
            }
        }
    }

    // ── Notification helpers ────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_DOWNLOAD) != null) return
        val channel = NotificationChannel(
            CHANNEL_DOWNLOAD,
            "Загрузка моделей AI",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Загрузка оффлайн-моделей для BRP Assistant"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildForegroundInfo(modelTitle: String, progressPercent: Int): ForegroundInfo {
        // Интент для открытия приложения по нажатию на уведомление
        val openIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0,
            openIntent ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (progressPercent >= 100)
            "Загрузка завершена"
        else
            "Загрузка модели AI"

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_DOWNLOAD)
            .setContentTitle(title)
            .setContentText(modelTitle)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(progressPercent < 100)
            .setContentIntent(pendingIntent)
            .setProgress(100, progressPercent, progressPercent == 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    // ── Download logic ──────────────────────────────────────────────────────

    private suspend fun getDirectDownloadUrl(initialUrl: String): String =
        withContext(Dispatchers.IO) {
            val urlWithParam = if (initialUrl.contains("?"))
                "$initialUrl&download=true"
            else
                "$initialUrl?download=true"

            val request = Request.Builder()
                .url(urlWithParam).head()
                .header("User-Agent", commonUserAgent)
                .header("Referer", "https://huggingface.co/")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.code in 300..399)
                    return@withContext response.header("Location") ?: initialUrl
                if (!response.isSuccessful) {
                    val getRequest = Request.Builder()
                        .url(urlWithParam)
                        .header("User-Agent", commonUserAgent)
                        .header("Range", "bytes=0-0")
                        .build()
                    okHttpClient.newCall(getRequest).execute().use { gr ->
                        if (gr.code in 300..399)
                            return@withContext gr.header("Location") ?: initialUrl
                    }
                }
            }
            initialUrl
        }

    private suspend fun performDownload(url: String, tempFile: File, modelTitle: String) {
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

        val downloadClient = okHttpClient.newBuilder().followRedirects(true).build()
        downloadClient.newCall(request).execute().use { response ->
            if (response.code == 416) { tempFile.delete(); return performDownload(url, tempFile, modelTitle) }
            if (response.code == 401 || response.code == 403) {
                if (alreadyDownloaded > 0) { tempFile.delete(); return performDownload(url, tempFile, modelTitle) }
                throw IOException("Access Denied (${response.code})")
            }
            if (!response.isSuccessful && response.code != 206)
                throw IOException("Server Error ${response.code}")

            val body = response.body ?: throw IOException("Empty body")
            val total = if (response.code == 206)
                alreadyDownloaded + body.contentLength()
            else
                body.contentLength()
            val appendMode = response.code == 206

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
                            val progressPct = if (total > 0) ((downloaded * 100) / total).toInt() else 0

                            setForeground(buildForegroundInfo(modelTitle, progressPct))
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
        private const val TAG = "ModelDownloadWorker"
        const val MODEL_DIR       = "models"
        const val KEY_URL         = "url"
        const val KEY_FILENAME    = "fileName"
        const val KEY_TITLE       = "title"
        const val KEY_SIZE        = "size"
        const val KEY_PROGRESS    = "progress"
        const val KEY_DOWNLOADED  = "downloaded"
        const val KEY_FILEPATH    = "filePath"
        const val CHANNEL_DOWNLOAD  = "brp_model_download"
        const val NOTIFICATION_ID   = 7001

        fun buildWorkData(url: String, fileName: String, title: String, size: Long): Data =
            Data.Builder()
                .putString(KEY_URL, url)
                .putString(KEY_FILENAME, fileName)
                .putString(KEY_TITLE, title)
                .putLong(KEY_SIZE, size)
                .build()
    }
}
