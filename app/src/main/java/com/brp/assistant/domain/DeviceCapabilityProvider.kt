package com.brp.assistant.domain

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX #5: инкапсулирует всю работу с железом устройства.
 *
 * Раньше ChatViewModel напрямую вызывал ActivityManager и Runtime —
 * Android-зависимость в ViewModel нарушает Clean Architecture и
 * делает тестирование невозможным без реального устройства.
 *
 * Теперь:
 * - ActivityManager / Runtime — только здесь
 * - ChatViewModel получает DeviceCapabilityProvider через @Inject
 * - В unit-тестах можно передать фейковый провайдер
 */
@Singleton
class DeviceCapabilityProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class MemoryStatus(
        /** Свободная куча JVM в МБ */
        val freeHeapMb: Long,
        /** Доступная системная RAM в МБ */
        val availRamMb: Long,
        /** Общая системная RAM в МБ */
        val totalRamMb: Long,
        /** true = Android сигнализирует low memory */
        val isLowMemory: Boolean
    ) {
        /** Достаточно памяти для безопасной генерации (~150 МБ heap + RAM) */
        val isSafeForGeneration: Boolean
            get() = freeHeapMb >= MIN_HEAP_MB && availRamMb >= MIN_AVAIL_RAM_MB && !isLowMemory

        /** Достаточно RAM для запуска локальной LLM (≥6 ГБ рекомендовано) */
        val isSafeForLocalLlm: Boolean
            get() = totalRamMb >= RECOMMENDED_LLM_RAM_MB
    }

    private val actManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    /**
     * Текущее состояние памяти устройства.
     * Вызов дешёвый — просто читает системные счётчики.
     */
    fun checkMemory(): MemoryStatus {
        val runtime   = Runtime.getRuntime()
        val freeHeap  = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / MB
        val memInfo   = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val availRam  = memInfo.availMem / MB
        val totalRam  = memInfo.totalMem / MB
        return MemoryStatus(
            freeHeapMb  = freeHeap,
            availRamMb  = availRam,
            totalRamMb  = totalRam,
            isLowMemory = memInfo.lowMemory
        )
    }

    /**
     * Быстрая проверка для UI: показывать ли предупреждение о нехватке памяти
     * перед загрузкой локальной модели.
     */
    fun hasEnoughMemoryForLocalLlm(): Boolean = checkMemory().isSafeForLocalLlm

    /**
     * Строка с описанием железа для DeviceCapabilityScreen.
     */
    fun formatDeviceInfo(): String {
        val mem    = checkMemory()
        val ramGb  = "%.1f".format(mem.totalRamMb / 1024.0)
        val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val sdk    = Build.VERSION.SDK_INT
        val abi    = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        return "$device · Android API $sdk · $abi · RAM ${ramGb} ГБ"
    }

    companion object {
        private const val MB = 1_048_576L
        const val MIN_HEAP_MB          = 150L
        const val MIN_AVAIL_RAM_MB     = 150L
        /** 6 144 МБ = 6 ГБ — минимум для комфортной работы 1.5B-модели */
        const val RECOMMENDED_LLM_RAM_MB = 6_144L
    }
}
