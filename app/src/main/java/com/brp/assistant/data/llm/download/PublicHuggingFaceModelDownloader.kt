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
        val maxRetry = 4
        var success = false
        var lastError: String? = null
        var dnsErrors = 0

        while (attempt < maxRetry && !success) {
            try {
                // Стратегия URL: hf-mirror.com ПЕРВЫМ.
                // В регионах где huggingface.co DNS блокируется/не резолвится
                // (часто в Казахстане, РФ, Китае), hf-mirror.com — прокси-CDN,
                // который надёжнее. Чередуем зеркало и прямой HF.
                val encodedFileName = Uri.encode(model.filename)
                val currentUrl = when (attempt % 2) {
                    0    -> "https://hf-mirror.com/${model.repoId}/resolve/main/$encodedFileName?download=true"
                    else -> model.downloadUrl
                        ?: "https://huggingface.co/${model.repoId}/resolve/main/$encodedFileName?download=true"
                }

                downloadFileInternalWithUrl(currentUrl, model, tempFile, outFile).collect { state ->
                    if (state is ModelDownloadState.Success) success = true
                    emit(state)
                }
                if (success) break
            } catch (e: Exception) {
                attempt++
                lastError = e.localizedMessage ?: e.message ?: "Неизвестная ошибка"
                // Детектируем DNS-ошибки для более понятного сообщения
                if (lastError.contains("Unable to resolve host") ||
                    lastError.contains("No address associated with hostname")) {
                    dnsErrors++
                }
                Log.e(TAG, "Attempt $attempt failed for ${model.id}: ${e.message}")
                if (attempt >= maxRetry) {
                    if (tempFile.exists()) {
                        val deleted = tempFile.delete()
                        Log.w(TAG, "Deleted corrupt .part file for ${model.id}: $deleted")
                    }
                    val userMessage = if (dnsErrors >= 2) {
                        "Нет связи с сервером моделей (DNS). Проверьте интернет-соединение " +
                        "и попробуйте снова. Если ошибка повторяется — возможно, " +
                        "huggingface.co недоступен в вашем регионе."
                    } else {
                        "Ошибка загрузки: $lastError. Проверьте соединение или попробуйте другую модель."
                    }
                    emit(ModelDownloadState.Error(model.id, userMessage, e))
                } else {
                    // Экспоненциальная задержка: 3s → 6s → 12s
                    delay(3000L * (1L shl (attempt - 1)))
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
            // Range resume для всех источников. hf-mirror.com поддерживает resume (206).
            // Если HF Xet-CDN вернёт 401 на Range — ниже ловим и повторяем без Range.
            requestBuilder.header("Range", "bytes=$partialBytes-")
        }

        val request = requestBuilder.build()
        var response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}")
            throw IOException("Ошибка сети: ${e.localizedMessage}")
        }

        try {
            // FIX: HF Xet-CDN может вернуть 401 на Range для неаутентифицированных.
            // В этом случае закрываем ответ, удаляем .part и повторяем без Range (с нуля).
            if (response.code == 401 && partialBytes > 0) {
                Log.w(TAG, "HF returned 401 on Range request — retrying from scratch (no resume)")
                response.close()
                if (tempFile.exists()) tempFile.delete()
                val noRangeRequest = request.newBuilder().removeHeader("Range").build()
                response = client.newCall(noRangeRequest).execute()
            }

            if (!response.isSuccessful && response.code != 206) {
                when (response.code) {
                    403, 401 -> throw IOException("Доступ запрещен (${response.code}). HF требует авторизации для этой модели.")
                    416      -> { tempFile.delete(); throw IOException("416 Range Not Satisfiable") }
                    else     -> throw IOException("HTTP ${response.code}: ${response.message}")
                }
            }

            val contentType = response.header("Content-Type")
            // Ослабленная проверка: выбрасываем HTML-ошибку ТОЛЬКО если ответ маленький
            // (значит это реально HTML-страница с ошибкой, а не бинарный файл).
            // Раньше CDN мог вернуть text/plain для .task/.litertlm и проверка ломала загрузку.
            val bodyLen = response.body?.contentLength() ?: -1
            if (contentType?.contains("text/html") == true && !model.isCustom && bodyLen in -1..65536) {
                throw IOException("Сервер вернул HTML-страницу вместо файла модели.")
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

                // FIX #4: явный flush + fsync перед rename.
                // На устройствах с eMMC 5.1 (Redmi 9, Realme C21) и некоторых
                // UFS 2.1 (Samsung A32) ядро может не сбросить page cache на диск
                // до вызова renameTo(). В результате после rename outFile существует,
                // но содержит нули или усечённые данные — модель крашится при загрузке.
                // flush() сбрасывает Java-буфер → FileDescriptor, fsync() гарантирует
                // физическую запись на носитель до переименования.
                output.flush()
                output.fd.sync()
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
