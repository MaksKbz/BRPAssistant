package com.brp.assistant.data.llm

import android.content.Context
import android.net.Uri
import com.brp.assistant.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getCustomModels(): List<OfflineModelInfo> {
        val jsonString = settingsRepository.customModelsJson.first() ?: return emptyList()
        return try {
            json.decodeFromString<List<OfflineModelInfo>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addCustomModel(model: OfflineModelInfo) {
        val current = getCustomModels().toMutableList()
        current.add(model)
        saveCustomModels(current)
    }

    suspend fun removeCustomModel(modelId: String) {
        val current = getCustomModels().filter { it.id != modelId }
        saveCustomModels(current)
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val modelDir = File(baseDir, "models/$modelId")
        if (modelDir.exists()) modelDir.deleteRecursively()
    }

    private suspend fun saveCustomModels(models: List<OfflineModelInfo>) {
        val jsonString = json.encodeToString(models)
        settingsRepository.setCustomModelsJson(jsonString)
    }

    suspend fun importFile(uri: Uri, fileName: String): Result<OfflineModelInfo> =
        withContext(Dispatchers.IO) {
            // FIX: поддерживаем все форматы, которые умеют движки приложения.
            val lowerName = fileName.lowercase()
            val supportedExt = listOf(".task", ".tflite", ".litertlm", ".gguf", ".bin")
            val ext = supportedExt.find { lowerName.endsWith(it) }
            if (ext == null) {
                return@withContext Result.failure(
                    Exception("Неподдерживаемый формат. Поддерживаются: ${supportedExt.joinToString(", ")}")
                )
            }

            // Определяем формат и стиль промпта по расширению
            val format = if (ext == ".litertlm") ModelFormat.LITERTLM else ModelFormat.TASK
            val promptStyle = when {
                lowerName.contains("qwen3") || lowerName.contains("qwen_3") -> PromptStyle.QWEN3
                lowerName.contains("gemma") -> PromptStyle.GEMMA
                lowerName.contains("phi") -> PromptStyle.PHI3
                else -> PromptStyle.CHATML
            }

            try {
                val id = "custom_" + UUID.randomUUID().toString().take(8)
                val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                val modelDir = File(baseDir, "models/$id")
                if (!modelDir.exists()) modelDir.mkdirs()

                val outFile = File(modelDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.failure(Exception("Не удалось открыть файл"))

                val sizeMb = (outFile.length() / (1024 * 1024)).toInt()
                val minRam = when {
                    sizeMb < 500 -> 3
                    sizeMb < 2000 -> 4
                    else -> 6
                }

                val modelInfo = OfflineModelInfo(
                    id = id,
                    title = "📦 $fileName",
                    repoId = "custom",
                    filename = fileName,
                    license = "Custom",
                    approxSizeMb = sizeMb,
                    minRamGb = minRam,
                    promptStyle = promptStyle,
                    format = format,
                    description = "Локальный файл: ${sizeMb} МБ. Формат: ${ext.drop(1)}.",
                    isCustom = true
                )

                addCustomModel(modelInfo)
                Result.success(modelInfo)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun addExternalUrl(title: String, url: String): OfflineModelInfo {
        val id = "custom_" + UUID.randomUUID().toString().take(8)
        val fileName = url.substringAfterLast("/")
            .substringBefore("?")
            .ifEmpty { "model.bin" }

        val modelInfo = OfflineModelInfo(
            id = id,
            title = title,
            repoId = "custom",
            filename = fileName,
            license = "External",
            approxSizeMb = 0,
            minRamGb = 4,
            promptStyle = PromptStyle.CHATML,
            description = "Модель по внешней ссылке.",
            downloadUrl = url,
            isCustom = true
        )

        addCustomModel(modelInfo)
        return modelInfo
    }
}
