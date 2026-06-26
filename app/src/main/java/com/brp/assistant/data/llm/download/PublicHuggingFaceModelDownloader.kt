package com.brp.assistant.data.llm.download

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.util.Log
import com.brp.assistant.data.llm.OfflineModelInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class ModelDownloadState {
    data class Progress(
        val modelId: String,
        val fileName: String,
        val downloadedBytes: Long,
        val totalBytes: Long?,
        val percent: Int?
    ) : ModelDownloadState()

    data class Success(
        val modelId: String,
        val file: File
    ) : ModelDownloadState()

    data class Error(
        val modelId: String,
        val message: String,
        val throwable: Throwable? = null
    ) : ModelDownloadState()
}

@Singleton
class PublicHuggingFaceModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    baseClient: OkHttpClient
) {
    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client: OkHttpClient = baseClient.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val TAG = "ModelDownloader"

    fun downloadModel(model: OfflineModelInfo): Flow<ModelDownloadState> = flow {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val modelDir = File(baseDir, "models/${model.id}")
        if (!modelDir.exists() && !modelDir.mkdirs()) {
            emit(ModelDownloadState.Error(model.id, "Не удалось создать директорию для модели"))
            return@flow
        }

        val outFile = File(modelDir, model.filename)
        if (outFile.exists() && outFile.length() > 1024 * 1024) {
            emit(ModelDownloadState.Success(model.id, outFile))
            return@flow
        }
        val tempFile = File(outFile.absolutePath + ".part")

        val approxSize = if (model.approxSizeMb > 0) model.approxSizeMb else 1000
        val requiredBytes = (approxSize.toLong() * 1024 * 1024) + (200L * 1024 * 1024)
        if (!hasEnoughSpace(requiredBytes)) {
            emit(ModelDownloadState.Error(model.id, "Недостаточно места на диске. Нужно ~${approxSize + 200} MB."))
            return@flow
        }

        var attempt = 0
        val maxRetry = 3
        var success = false

        while (attempt < maxRetry && !success) {
            try {
                val currentUrl = if (model.downloadUrl != null) {
                    model.downloadUrl
                } else {
                    val encodedFileName = Uri.encode(model.filename)
                    when (attempt) {
                        0    -> "https://hf-mirror.com/${model.repoId}/resolve/main/${encodedFileName}?download=true"
                        1    -> "https://huggingface.co/${model.repoId}/resolve/main/${encodedFileName}?download=true"
                        else -> "https://huggingface.co/${model.repoId}/resolve/main/${encodedFileName}?download=true"
                    }
                }

                downloadFileInternalWithUrl(currentUrl, model, tempFile, outFile).collect { state ->
                    if (state is ModelDownloadState.Success) success = true
                    emit(state)
                }
                if (success) break
            } catch (e: Exception) {
                attempt++
                Log.e(TAG, "Attempt $attempt failed for ${model.id}: ${e.message}")
                if (attempt >= maxRetry) {
                    emit(ModelDownloadState.Error(model.id, "Ошибка загрузки: ${e.localizedMessage}. Проверьте соединение или попробуйте другую модель.", e))
                } else {
                    delay(2000L * attempt)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun downloadFileInternalWithUrl(
        url: String,
        model: OfflineModelInfo,
        tempFile: File,
        outFile: File
    ): Flow<ModelDownloadState> = flow {
        var finalUrl = url
        if ((finalUrl.contains("huggingface.co") || finalUrl.contains("hf-mirror.com"))
            && !finalUrl.contains("download=true")
        ) {
            finalUrl += if (finalUrl.contains("?")) "&download=true" else "?download=true"
        }

        /**
         * FIX #2: alreadyDownloaded вычисляется до запроса, чтобы отправить
         * Range-заголовок. НО: если сервер ответит 200 OK (а не 206 Partial),
         * значит возобновление не поддерживается — в handleStream() мы обнулим
         * смещение и удалим .part-файл. Ранее alreadyDownloaded передавался
         * в handleStream как «уже загружено», что приводило к дозаписи
         * байт поверх начала файла и его повреждению.
         */
        val partialBytes = if (tempFile.exists()) tempFile.length() else 0L

        Log.d(TAG, "Requesting model from: $finalUrl")

        val requestBuilder = Request.Builder()
            .url(finalUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Connection", "keep-alive")

        if (finalUrl.contains("huggingface.co")) {
            requestBuilder.header("Referer", "https://huggingface.co/")
        }
        if (partialBytes > 0) {
            requestBuilder.header("Range", "bytes=$partialBytes-")
        }

        val request = requestBuilder.build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}")
            throw IOException("Ошибка сети: ${e.localizedMessage}")
        }

        try {
            if (!response.isSuccessful && response.code != 206) {
                when (response.code) {
                    403, 401 -> throw IOException("Доступ запрещен (${response.code}). HF требует авторизации для этой модели.")
                    416      -> { tempFile.delete(); throw IOException("416 Range Not Satisfiable") }
                    else     -> throw IOException("HTTP ${response.code}: ${response.message}")
                }
            }

            val contentType = response.header("Content-Type")
            if (contentType?.contains("text/html") == true && !model.isCustom) {
                throw IOException("Сервер вернул HTML-страницу вместо файла.")
            }

            // FIX #2: передаём partialBytes только если сервер подтвердил resumable (206).
            // При 200 OK — передаём 0, handleStream удалит .part и начнёт с нуля.
            val resumeOffset = if (response.code == 206) partialBytes else 0L
            handleStream(model, response, tempFile, outFile, resumeOffset).collect { emit(it) }
        } finally {
            response.close()
        }
    }

    private fun handleStream(
        model: OfflineModelInfo,
        response: Response,
        tempFile: File,
        outFile: File,
        alreadyDownloaded: Long
    ): Flow<ModelDownloadState> = flow {
        val append = response.code == 206

        if (!append && tempFile.exists()) {
            tempFile.delete()
        }

        val body = response.body ?: throw IOException("Пустое тело ответа")
        val contentLength = body.contentLength()
        val totalBytes = if (contentLength >= 0) {
            if (append) alreadyDownloaded + contentLength else contentLength
        } else null

        FileOutputStream(tempFile, append).use { output ->
            body.byteStream().use { input ->
                val buffer = ByteArray(1024 * 64)
                var bytesRead: Int
                var currentDownloaded = if (append) alreadyDownloaded else 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    currentDownloaded += bytesRead

                    val percent = if (totalBytes != null && totalBytes > 0) {
                        ((currentDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                    } else null

                    emit(ModelDownloadState.Progress(
                        modelId          = model.id,
                        fileName         = model.filename,
                        downloadedBytes  = currentDownloaded,
                        totalBytes       = totalBytes,
                        percent          = percent
                    ))
                }
            }
        }

        if (tempFile.exists()) {
            if (outFile.exists()) outFile.delete()
            if (!tempFile.renameTo(outFile)) {
                throw IOException("Не удалось завершить загрузку файла")
            }
        }
        emit(ModelDownloadState.Success(model.id, outFile))
    }

    private fun hasEnoughSpace(requiredBytes: Long): Boolean {
        return try {
            val path = context.getExternalFilesDir(null) ?: context.filesDir
            val stat = StatFs(path.path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            availableBytes > requiredBytes
        } catch (e: Exception) {
            true
        }
    }

    fun isModelDownloaded(model: OfflineModelInfo): Boolean {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val modelDir = File(baseDir, "models/${model.id}")
        val file = File(modelDir, model.filename)
        return file.exists() && file.length() > 1024 * 1024
    }
}
