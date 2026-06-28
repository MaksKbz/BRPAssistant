package com.brp.assistant

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.brp.assistant.domain.AppHealthChecker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// FIX #2: App реализует Configuration.Provider для HiltWorkerFactory
// Это позволяет @HiltWorker-аннотированным Worker-классам (ModelDownloadWorker)
// получать зависимости через Hilt при создании WorkManager
@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * #5 / #10 — Health Check инжектируется и запускается при старте.
     *
     * runChecks() выполняет три проверки асинхронно в IO-диспетчере:
     *   1. Свободное место на диске (>= 500 MB)
     *   2. Доступность БД (пробный SELECT)
     *   3. Наличие хотя бы одного API-ключа
     *
     * Результат публикуется в AppHealthChecker.status (StateFlow<HealthStatus>).
     * Потребители:
     *   • ChatViewModel — подписывается и показывает предупреждение в UI
     *   • ChatSessionRepository (#8) — используется как пример safe-репозитория
     *     (не использует healthChecker напрямую, но следует тому же паттерну
     *     изоляции ошибок БД через safeQuery { })
     */
    @Inject
    lateinit var healthChecker: AppHealthChecker

    override fun onCreate() {
        super.onCreate()
        // #10: healthChecker.runChecks() — подключён здесь.
        // Статус доступен всем подписчикам через healthChecker.status StateFlow.
        healthChecker.runChecks()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
