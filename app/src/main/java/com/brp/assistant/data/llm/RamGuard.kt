package com.brp.assistant.data.llm

import android.app.ActivityManager
import android.content.Context
import android.util.Log

/**
 * Утилита проверки доступной RAM перед загрузкой LLM-модели.
 *
 * Правило: запуск модели разрешён только если доступно
 * не менее (modelSizeMb * RAM_HEADROOM_MULTIPLIER) мегабайт.
 *
 * Headroom = 1.4x: 40% overhead сверх размера файла.
 * Это покрывает рабочие буферы KV-кеша, активации и системные нужды Android.
 */
object RamGuard {

    private const val TAG = "RamGuard"
    private const val RAM_HEADROOM_MULTIPLIER = 1.4f

    /**
     * Возвращает доступную RAM в мегабайтах.
     * Использует [ActivityManager.MemoryInfo.availMem], актуальное на момент вызова.
     */
    fun availableRamMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / (1024L * 1024L)
    }

    /**
     * Возвращает полный объём RAM устройства в мегабайтах.
     */
    fun totalRamMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024L * 1024L)
    }

    /**
     * Проверяет, достаточно ли памяти для загрузки модели размером [modelSizeMb] МБ.
     *
     * @return [RamCheckResult.Ok]    если памяти достаточно
     * @return [RamCheckResult.Low]   если памяти мало, но можно попробовать
     * @return [RamCheckResult.Critical] если памяти критически мало — не запускать
     */
    fun check(context: Context, modelSizeMb: Int): RamCheckResult {
        val available = availableRamMb(context)
        val required = (modelSizeMb * RAM_HEADROOM_MULTIPLIER).toLong()
        val ratio = available.toFloat() / required.toFloat()

        Log.d(TAG, "RAM check: available=${available}MB, required=${required}MB, model=${modelSizeMb}MB, ratio=$ratio")

        return when {
            ratio >= 1.0f  -> RamCheckResult.Ok(available)
            ratio >= 0.7f  -> RamCheckResult.Low(available, required)
            else           -> RamCheckResult.Critical(available, required)
        }
    }

    sealed class RamCheckResult {
        /** Памяти достаточно. */
        data class Ok(val availableMb: Long) : RamCheckResult()

        /**
         * Памяти мало (70-100% от требуемого).
         * Предупредить пользователя, но разрешить загрузку.
         */
        data class Low(val availableMb: Long, val requiredMb: Long) : RamCheckResult() {
            val warningMessage: String get() =
                "Доступно ${availableMb} МБ RAM, рекомендуется ${requiredMb} МБ. " +
                "Приложение может работать медленнее."
        }

        /**
         * Памяти критически мало (< 70% от требуемого).
         * Заблокировать загрузку, показать ошибку.
         */
        data class Critical(val availableMb: Long, val requiredMb: Long) : RamCheckResult() {
            val errorMessage: String get() =
                "Недостаточно RAM: доступно ${availableMb} МБ, требуется ≈${requiredMb} МБ. " +
                "Закройте другие приложения или выберите модель меньшего размера."
        }
    }
}
