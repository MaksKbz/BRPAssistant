package com.brp.assistant.domain

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.brp.assistant.data.db.ChatSessionDao
import com.brp.assistant.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Результат проверки здоровья приложения при старте.
 *
 * @param diskFreeGb  Свободное место на внутреннем хранилище (ГБ).
 * @param dbOk        true — пробный SELECT в Room прошёл без исключений.
 * @param hasApiKey   true — хотя бы один API-ключ (Gemini или Groq) настроен.
 * @param warnings    Список предупреждений для показа пользователю.
 */
data class HealthStatus(
    val diskFreeGb: Double = 0.0,
    val dbOk: Boolean = true,
    val hasApiKey: Boolean = false,
    val warnings: List<String> = emptyList()
) {
    /** Приложение полностью работоспособно: диска достаточно, БД в порядке. */
    val isHealthy: Boolean get() = dbOk && diskFreeGb >= MIN_DISK_FREE_GB

    companion object {
        /** Минимум свободного места для нормальной работы (кэш, загрузка моделей). */
        const val MIN_DISK_FREE_GB = 0.5
    }
}

/**
 * #5 — Health Check при запуске.
 *
 * Запускается один раз в App.onCreate() через GlobalScope-like SupervisorJob.
 * Результат доступен через [status] StateFlow — подписываются ViewModel/UI.
 *
 * Три проверки:
 *  1. Свободное место на диске — предупреждение при < 500 МБ, критично при < 100 МБ.
 *  2. БД Room — пробный getMessages("_health_probe_") проверяет корректность
 *     схемы без создания лишних данных (IGNORE возвращает пустой список).
 *  3. API-ключи — хотя бы один Gemini или Groq ключ должен быть настроен,
 *     иначе онлайн-режим недоступен (не фатально — можно работать офлайн).
 */
@Singleton
class AppHealthChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ChatSessionDao,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(HealthStatus())
    val status: StateFlow<HealthStatus> = _status.asStateFlow()

    fun runChecks() {
        scope.launch {
            val warnings = mutableListOf<String>()

            // ── 1. Диск ──────────────────────────────────────────────────────
            val diskFreeGb = try {
                val stat = StatFs(Environment.getDataDirectory().path)
                stat.availableBlocksLong * stat.blockSizeLong / (1024.0 * 1024.0 * 1024.0)
            } catch (e: Exception) {
                Log.w(TAG, "Disk check failed", e)
                -1.0
            }
            when {
                diskFreeGb in 0.0..0.1 ->
                    warnings += "⚠️ Критически мало места: ${"%.0f".format(diskFreeGb * 1024)} МБ. Приложение может работать нестабильно."
                diskFreeGb in 0.1..HealthStatus.MIN_DISK_FREE_GB ->
                    warnings += "⚠️ Мало свободного места: ${"%.1f".format(diskFreeGb)} ГБ. Рекомендуется освободить хранилище."
            }

            // ── 2. БД ─────────────────────────────────────────────────────────
            val dbOk = try {
                dao.getMessages("_health_probe_")
                true
            } catch (e: Exception) {
                Log.e(TAG, "DB health check failed", e)
                warnings += "❌ Ошибка базы данных. Попробуйте переустановить приложение."
                false
            }

            // ── 3. API-ключи ──────────────────────────────────────────────────
            val geminiKey = settingsRepository.geminiApiKey.first()
            val groqKey = settingsRepository.groqApiKey.first()
            val hasApiKey = !geminiKey.isNullOrBlank() || !groqKey.isNullOrBlank()
            if (!hasApiKey) {
                warnings += "ℹ️ API-ключ не настроен. Доступен только офлайн-режим."
            }

            val result = HealthStatus(
                diskFreeGb = diskFreeGb,
                dbOk = dbOk,
                hasApiKey = hasApiKey,
                warnings = warnings
            )
            _status.value = result
            Log.i(TAG, "Health check: disk=${"%.2f".format(diskFreeGb)}GB, db=$dbOk, hasKey=$hasApiKey, warnings=${warnings.size}")
        }
    }

    companion object {
        private const val TAG = "AppHealthChecker"
    }
}
