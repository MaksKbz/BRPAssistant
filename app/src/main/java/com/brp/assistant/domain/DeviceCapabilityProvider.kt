package com.brp.assistant.domain

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.brp.assistant.data.llm.OfflineModelInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Инкапсулирует всю работу с железом устройства.
 *
 * ChatViewModel и другие ViewModel получают DeviceCapabilityProvider через @Inject.
 * В unit-тестах можно передать фейковый провайдер без реального устройства.
 */
@Singleton
class DeviceCapabilityProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class MemoryStatus(
        val freeHeapMb: Long,
        val availRamMb: Long,
        val totalRamMb: Long,
        val isLowMemory: Boolean
    ) {
        val isSafeForGeneration: Boolean
            get() = freeHeapMb >= MIN_HEAP_MB && availRamMb >= MIN_AVAIL_RAM_MB && !isLowMemory
    }

    private val actManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

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
     * FIX #5: заменяет единый жёсткий порог RECOMMENDED_LLM_RAM_MB = 6144.
     *
     * Ранее единый порог 6 ГБ отсекал устройства с 4 ГБ RAM, на которых
     * Qwen3-0.6B и Gemma-2B работают нормально. Теперь порог вычисляется
     * динамически: approxSizeMb * 2.5 — эмпирически подобранный множитель,
     * учитывающий оверхед KV-кеша и рантайма LiteRT/MediaPipe.
     *
     * Примеры:
     *   Qwen3-0.6B  (~600 МБ)  → нужно ~1.5 ГБ  → безопасно на 4 ГБ устройствах
     *   Qwen3-1.7B  (~1700 МБ) → нужно ~4.3 ГБ  → предупреждение на 4 ГБ устройствах
     *   Qwen3-4B    (~4000 МБ) → нужно ~10 ГБ   → предупреждение на <12 ГБ устройствах
     */
    fun isSafeForModel(model: OfflineModelInfo): Boolean {
        if (model.approxSizeMb <= 0) return true  // размер неизвестен — не блокируем
        val requiredMb = (model.approxSizeMb * RAM_OVERHEAD_MULTIPLIER).toLong()
        return checkMemory().totalRamMb >= requiredMb
    }

    /** Быстрая проверка для UI: показывать ли предупреждение перед загрузкой. */
    fun hasEnoughMemoryForLocalLlm(): Boolean =
        checkMemory().totalRamMb >= MIN_RAM_FOR_ANY_LLM_MB

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
        const val MIN_HEAP_MB       = 150L
        const val MIN_AVAIL_RAM_MB  = 150L
        /** Минимум RAM для запуска хоть какой-то локальной LLM (3 ГБ для Qwen3-0.6B) */
        const val MIN_RAM_FOR_ANY_LLM_MB = 3_072L
        /**
         * FIX #5: множитель для динамического расчёта порога RAM.
         * approxSizeMb × 2.5 ≈ оверхед KV-кеша + рантайм LiteRT/MediaPipe.
         */
        const val RAM_OVERHEAD_MULTIPLIER = 2.5
    }
}
