package com.brp.assistant

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.brp.assistant.data.rag.KnowledgeChunkInitializer
import com.brp.assistant.domain.AppHealthChecker
import com.brp.assistant.domain.CleanupWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Health Check запускается при старте:
     * 1. Свободное место на диске (>= 500 MB)
     * 2. Доступность БД (пробный SELECT)
     * 3. Наличие хотя бы одного API-ключа
     *
     * Статус публикуется в AppHealthChecker.status (StateFlow<HealthStatus>).
     */
    @Inject
    lateinit var healthChecker: AppHealthChecker

    @Inject
    lateinit var chunkInitializer: KnowledgeChunkInitializer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Health check — проверяет диск, БД и API-ключи при старте
        healthChecker.runChecks()

        // Заполняем knowledge_chunks из markdown-карточек (фоновая операция)
        appScope.launch {
            runCatching { chunkInitializer.ensureChunksPopulated() }
                .onFailure { it.printStackTrace() }
        }

        // Еженедельная очистка: старые сессии + .part-файлы
        // Запускается только при достаточном заряде батареи
        CleanupWorker.enqueue(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
