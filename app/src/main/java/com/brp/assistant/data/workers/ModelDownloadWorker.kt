package com.brp.assistant.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
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

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val commonUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    // ──────────────────────────────────────────────────────────────────────────
    // getForegroundInfo() — ОБЯЗАТЕЛЕН для Android 12+ (API 31+).
    // WorkManager вызывает его ДО doWork() при запуске Expedited/Long-running
    // задачи. Без него — ForegroundServiceDidNotStartInTimeException.
    // ──────────────────────────────────────────────────────────────────────────
    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = buildNotification(
            title = inputData.getString(KEY_FILENAME) ?: "Загрузка модели",
            progress = 0,
            indeterminate = true
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val initialUrl   = inputData.getString(KEY_URL)      ?: return@withContext Result.failure()
        val fileName     = inputData.getString(KEY_FILENAME)  ?: return@withContext Result.failure()
        val approxSizeMb = inputData.getLong(KEY_SIZE, 0L) / (1024 * 1024)

        // Показываем foreground уведомление — без этого на Android 12+ краш
        setForeground(getForegroundInfo())

        // FIX: проверка доступности сети до начала загрузки.
        // Без этой проверки при отсутствии интернета падало с непонятной
        // IOException «failed to connect to huggingface.co» без совета.
        // Теперь пользователь видит явное сообщение + Result.retry() если
        // попыток ещё мало — WorkManager повторит с exponential backoff.
        if (!isNetworkAvailable()) {
            val noNetMsg = "Нет интернета. Проверьте подключение к Wi-Fi или мобильным данным."
            return@withContext if (runAttemptCount < 3) {
                Log.w(TAG, "No network, will retry (attempt $runAttemptCount)")
                Result.retry()
            } else {
                Result.failure(Data.Builder().putString("error", noNetMsg).build())
            }
        }

        // FIX: исправлен путь хранения модели.
        // Было: context.filesDir — не совпадал с путём в LlmInferenceEngine
        //       и PublicHuggingFaceModelDownloader, которые используют
        //       getExternalFilesDir(null) ?: filesDir.
        // Стало: тот же приоритет хранилища что и в LlmInferenceEngine.getModelsBaseDir()
        val outputDir = File(
            applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir,
            MODEL_DIR
        )
        if (!outputDir.exists()) outputDir.mkdirs()

        // FIX: проверка свободного места до начала загрузки.
        // ModelDownloadWorker не проверял место в отличие от PublicHuggingFaceModelDownloader.
        // На устройствах с заполненным хранилищем (32 ГБ, занято 90%) загрузка
        // 5.7 ГБ модели падала с IOException в середине процесса без понятного сообщения.
        if (approxSizeMb > 0 && !hasEnoughSpace(outputDir, approxSizeMb)) {
            return@withContext Result.failure(
                Data.Builder().putString(
                    "error",
                    "Недостаточно места на диске. Нужно ~${approxSizeMb + 200} МБ свободного места."
                ).build()
            )
        }

        val outputFile = File(outputDir, fileName)
        val tempFile   = File(outputDir, "$fileName.part")

        Log.d(TAG, "Starting download: $fileName to ${outputDir.absolutePath}")

        try {
            val directUrl = getDirectDownloadUrl(initialUrl)
            performDownload(directUrl, tempFile, fileName)

            val minValidSize = 50L * 1024 * 1024  // 50 MB
            if (tempFile.exists() && tempFile.length() > minValidSize) {
                if (outputFile.exists()) outputFile.delete()
                if (!tempFile.renameTo(outputFile)) {
                    tempFile.copyTo(outputFile, overwrite = true)
                    tempFile.delete()
                }
                notifyDone(fileName)
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
            val msg = e.message ?: ""
            when {
                msg.contains("401") || msg.contains("403") ->
                    Result.failure(
                        Data.Builder().putString("error",
                            "Доступ ограничен (401/403). Попробуйте другую модель.").build()
                    )
                // FIX: timeout и connect-ошибки явно отделены от серверных —
                // пользователь видит совет проверить сеть, а не просто «ошибка».
                msg.contains("timeout", ignoreCase = true) ||
                msg.contains("connect", ignoreCase = true) ||
                msg.contains("reset",   ignoreCase = true) -> {
                    if (runAttemptCount < 3) {
                        Log.w(TAG, "Network error, will retry (attempt $runAttemptCount): $msg")
                        Result.retry()
                    } else {
                        Result.failure(
                            Data.Builder().putString(
                                "error",
                                "Нет соединения с сервером после 3 попыток. Проверьте интернет."
                            ).build()
                        )
                    }
                }
                // FIX: прочие IOException — даём 3 попытки вместо 2.
                // WorkManager использует exponential backoff по умолчанию (30с → 60с → 120с),
                // что разумнее для нестабильного мобильного соединения при загрузке 5+ ГБ.
                runAttemptCount < 3 -> Result.retry()
                else -> Result.failure(Data.Builder().putString("error", e.message).build())
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Network check
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * FIX: проверка активного сетевого соединения.
     * NetworkCapabilities используется вместо устаревшего NetworkInfo (deprecated API 29).
     * Проверяем NET_CAPABILITY_INTERNET + NET_CAPABILITY_VALIDATED — последний гарантирует,
     * что сеть не только есть, но и реально имеет доступ в интернет (captive portal = false).
     */
    private fun isNetworkAvailable(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notification helpers
    // ──────────────────────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Загрузка моделей ИИ",
                NotificationManager.IMPORTANCE_LOW   // LOW — без звука, просто прогресс
            ).apply {
                description = "Фоновая загрузка LLM-моделей"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        progress: Int,
        indeterminate: Boolean = false
    ) = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(
            if (indeterminate) "Подготовка загрузки…"
            else "Загрузка: $progress%"
        )
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(100, progress, indeterminate)
        .build()

    private suspend fun notifyDone(fileName: String) {
        val doneNotification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Модель загружена")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID + 1, doneNotification)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Download logic
    // ──────────────────────────────────────────────────────────────────────────
    private suspend fun getDirectDownloadUrl(initialUrl: String): String =
        withContext(Dispatchers.IO) {
            val urlWithParam = if (initialUrl.contains("?"))
                "$initialUrl&download=true"
            else
                "$initialUrl?download=true"

            val request = Request.Builder()
                .url(urlWithParam)
                .head()
                .header("User-Agent", commonUserAgent)
                .header("Referer", "https://huggingface.co/")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.code in 300..399) {
                    return@withContext response.header("Location") ?: initialUrl
                }
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

    /**
     * FIX: устранена бесконечная рекурсия при HTTP 416 (Range Not Satisfiable).
     * Было: performDownload() рекурсивно вызывал сам себя при 416 — если сервер
     *       стабильно возвращает 416 (CDN-кэш, edge-случай) это приводило к
     *       StackOverflowError без возможности восстановления.
     * Стало: параметр retryCount ограничивает повторную попытку одним разом.
     *        При повторном 416 выбрасывается IOException с понятным сообщением.
     */
    private suspend fun performDownload(
        url: String,
        tempFile: File,
        fileName: String,
        retryCount: Int = 0
    ) {
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
            when (response.code) {
                416 -> {
                    // Range Not Satisfiable: локальный .part файл некорректен
                    tempFile.delete()
                    if (retryCount < 1) {
                        return performDownload(url, tempFile, fileName, retryCount + 1)
                    } else {
                        throw IOException("HTTP 416: Range Not Satisfiable после повтора — возможно повреждён .part файл")
                    }
                }
                401, 403 -> {
                    if (alreadyDownloaded > 0) {
                        tempFile.delete()
                        if (retryCount < 1) {
                            return performDownload(url, tempFile, fileName, retryCount + 1)
                        }
                    }
                    throw IOException("Access Denied (${response.code})")
                }
                else -> {
                    if (!response.isSuccessful && response.code != 206)
                        throw IOException("Server Error ${response.code}")
                }
            }

            val body = response.body ?: throw IOException("Empty body")
            val total = if (response.code == 206)
                alreadyDownloaded + body.contentLength()
            else
                body.contentLength()
            val appendMode = response.code == 206

            FileOutputStream(tempFile, appendMode).use { outputStream ->
                body.byteStream().use { input ->
                    val buffer     = ByteArray(128 * 1024)
                    var downloaded = if (appendMode) alreadyDownloaded else 0L
                    var read: Int
                    var lastUpdate = 0L

                    while (input.read(buffer).also { read = it } != -1) {
                        if (isStopped) throw IOException("Canceled")
                        outputStream.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 1000) {
                            val progress = if (total > 0)
                                ((downloaded.toFloat() / total) * 100).toInt().coerceIn(0, 100)
                            else -1

                            setProgress(
                                Data.Builder()
                                    .putFloat(KEY_PROGRESS, downloaded.toFloat() / total.coerceAtLeast(1))
                                    .putLong(KEY_DOWNLOADED, downloaded)
                                    .putLong(KEY_SIZE, total)
                                    .build()
                            )

                            if (progress >= 0) {
                                setForeground(
                                    ForegroundInfo(
                                        NOTIFICATION_ID,
                                        buildNotification(fileName, progress)
                                    )
                                )
                            }
                            lastUpdate = now
                        }
                    }
                }
            }
        }
    }

    /**
     * Проверяет наличие достаточного места на диске.
     * В отличие от PublicHuggingFaceModelDownloader, возвращает false при
     * исключении StatFs — безопаснее для устройств с 32 ГБ заполненным хранилищем.
     */
    private fun hasEnoughSpace(dir: File, approxSizeMb: Long): Boolean {
        return try {
            val stat = StatFs(dir.path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val requiredBytes = (approxSizeMb + 200L) * 1024 * 1024
            availableBytes > requiredBytes
        } catch (e: Exception) {
            Log.w(TAG, "hasEnoughSpace check failed: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG             = "ModelDownloadWorker"
        private const val CHANNEL_ID      = "brp_model_download"
        private const val NOTIFICATION_ID = 1001

        const val MODEL_DIR      = "models"
        const val KEY_URL        = "url"
        const val KEY_FILENAME   = "fileName"
        const val KEY_SIZE       = "size"
        const val KEY_PROGRESS   = "progress"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_FILEPATH   = "filePath"

        fun buildWorkData(url: String, fileName: String, size: Long): Data =
            Data.Builder()
                .putString(KEY_URL, url)
                .putString(KEY_FILENAME, fileName)
                .putLong(KEY_SIZE, size)
                .build()
    }
}
