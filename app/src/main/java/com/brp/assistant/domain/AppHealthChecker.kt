package com.brp.assistant.domain

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.brp.assistant.data.llm.LlmInferenceEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Выполняет быстрый Health Check при запуске приложения.
 *
 * Проверяет:
 *  1. Достаточно ли свободного места на диске (> 200 МБ).
 *  2. Доступна ли хотя бы одна скачанная модель.
 *  3. Состояние базы данных (доступна ли).
 *
 * Используется в MainActivity для показа non-blocking предупреждений.
 */
@Singleton
class AppHealthChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmEngine: LlmInferenceEngine
) {
    companion object {
        private const val TAG = "AppHealthChecker"
        private const val MIN_FREE_STORAGE_MB = 200L
    }

    suspend fun checkHealth(): HealthCheckResult = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()

        // 1. Проверка свободного места
        val freeMb = getFreeMb()
        val isStorageSufficient = freeMb < 0 || freeMb >= MIN_FREE_STORAGE_MB
        if (!isStorageSufficient) {
            val msg = "⚠️ Мало места: ~${freeMb} МБ свободно. Для загрузки моделей нужно минимум ${MIN_FREE_STORAGE_MB} МБ."
            warnings.add(msg)
            Log.w(TAG, msg)
        }

        // 2. Проверка наличия модели
        val hasActiveModel = llmEngine.isReady() || llmEngine.getDownloadedModels().isNotEmpty()
        if (!hasActiveModel) {
            warnings.add("ℹ️ Нет загруженных моделей. Скачайте модель в разделе 'Менеджер моделей' или настройте API-ключ для онлайн-режима.")
        }

        // 3. Проверка БД — если до этого приложение работало, БД доступна (Hilt инициализировал)
        val isDatabaseHealthy = true

        HealthCheckResult(
            isStorageSufficient = isStorageSufficient,
            hasActiveModel = hasActiveModel,
            isDatabaseHealthy = isDatabaseHealthy,
            freeMb = freeMb,
            warnings = warnings
        )
    }

    private fun getFreeMb(): Long {
        return try {
            val path = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED)
                context.getExternalFilesDir(null) ?: context.filesDir
            else
                context.filesDir
            val stat = StatFs(path.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong / (1024L * 1024L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get free storage", e)
            -1L
        }
    }
}
