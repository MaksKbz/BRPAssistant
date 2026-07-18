package com.brp.assistant.domain

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.brp.assistant.data.db.ChatSessionDao
import com.brp.assistant.data.llm.LlmInferenceEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Еженедельная очистка устаревших данных.
 *
 * Выполняет:
 *  1. Удаляет сессии чата старше 90 дней.
 *  2. Удаляет остаточные .part-файлы (прерванные загрузки).
 *
 * Запускается через WorkManager раз в неделю (enqueueUniquePeriodicWork).
 * Регистрация происходит в App.kt.
 * Требует @HiltWorker + @AssistedInject для Hilt-инъекции.
 */
@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val chatSessionDao: ChatSessionDao,
    private val llmEngine: LlmInferenceEngine
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "CleanupWorker"
        private const val WORK_NAME = "brp_weekly_cleanup"
        private const val DAYS_TO_KEEP = 90L

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<CleanupWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.DAYS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(TAG, "CleanupWorker scheduled (weekly)")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting weekly cleanup")
        var deletedPartFiles = 0

        return try {
            // 1. Удаляем старые сессии (> 90 дней)
            val cutoffMs = System.currentTimeMillis() - (DAYS_TO_KEEP * 24 * 60 * 60 * 1000L)
            chatSessionDao.deleteSessionsOlderThan(cutoffMs)
            Log.i(TAG, "Deleted sessions older than $DAYS_TO_KEEP days")

            // 2. Удаляем .part-файлы прерванных загрузок
            val baseDir = llmEngine.getModelsBaseDir()
            val modelsDir = java.io.File(baseDir, "models")
            if (modelsDir.exists() && modelsDir.isDirectory) {
                modelsDir.walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".part") }
                    .forEach { partFile ->
                        val ageMs = System.currentTimeMillis() - partFile.lastModified()
                        if (ageMs > 24 * 60 * 60 * 1000L) {
                            if (partFile.delete()) {
                                deletedPartFiles++
                                Log.i(TAG, "Deleted stale .part file: ${partFile.name}")
                            }
                        }
                    }
            }

            Log.i(TAG, "Cleanup complete: $deletedPartFiles .part files removed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
            Result.retry()
        }
    }
}
