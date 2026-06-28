package com.brp.assistant.domain

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Результат проверки ресурсов перед/во время инференса.
 *
 * @param isSafeForGeneration Можно ли запускать генерацию.
 * @param freeHeapMb         Свободная куча JVM в МБ.
 * @param availRamMb         Доступная RAM в МБ.
 * @param batteryLevel       Уровень заряда 0-100 (-1 если недоступен).
 * @param isBatterySaverOn   Включён ли режим экономии батареи.
 * @param batteryWarning     Предупреждение о низком заряде (или null).
 * @param recommendedMaxTokens Рекомендуемое maxTokens с учётом состояния.
 */
data class ResourceCheckResult(
    val isSafeForGeneration: Boolean,
    val freeHeapMb: Long,
    val availRamMb: Long,
    val batteryLevel: Int = -1,
    val isBatterySaverOn: Boolean = false,
    val batteryWarning: String? = null,
    val recommendedMaxTokens: Int = 1024
)

/**
 * Монитор ресурсов во время инференса для LLM.
 *
 * Отслеживает RAM, заряд батареи, режим экономии.
 * Интегрируется в ChatViewModel и DiagnoseViewModel.
 *
 * На устройствах < 3 ГБ RAM (Tecno Spark, Itel A70, Redmi 9) предотвращает
 * OOM при запуске тяжёлых моделей с недостаточным запасом.
 */
@Singleton
class InferenceResourceMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "InferenceResourceMonitor"
        private const val MIN_FREE_HEAP_MB = 50L
        private const val MIN_AVAIL_RAM_MB = 200L
        private const val LOW_BATTERY_THRESHOLD = 20
        private const val MAX_TOKENS_DEFAULT = 1024
        private const val MAX_TOKENS_LOW_BATTERY = 512
        private const val MAX_TOKENS_LOW_MEM = 384
    }

    /**
     * Проверяет текущее состояние RAM и батареи.
     * Вызывается перед каждой генерацией из ChatViewModel.
     */
    suspend fun checkMemory(): ResourceCheckResult = withContext(Dispatchers.Default) {
        val runtime = Runtime.getRuntime()
        val freeHeapMb = (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / (1024L * 1024L)

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val availRamMb = memInfo.availMem / (1024L * 1024L)

        val batteryLevel = getBatteryLevel()
        val isBatterySaverOn = isBatterySaverEnabled()

        val isSafe = freeHeapMb >= MIN_FREE_HEAP_MB && availRamMb >= MIN_AVAIL_RAM_MB

        // Динамический recommendedMaxTokens
        val recommendedMaxTokens = when {
            freeHeapMb < MIN_FREE_HEAP_MB * 1.5 || availRamMb < MIN_AVAIL_RAM_MB * 1.5 -> MAX_TOKENS_LOW_MEM
            isBatterySaverOn || (batteryLevel in 1..LOW_BATTERY_THRESHOLD)              -> MAX_TOKENS_LOW_BATTERY
            else                                                                         -> MAX_TOKENS_DEFAULT
        }

        val batteryWarning = when {
            isBatterySaverOn -> "⚡ Режим экономии включён. Генерация будет медленнее, рекомендуется лёгкая модель."
            batteryLevel in 1..LOW_BATTERY_THRESHOLD -> "🔋 Заряд батареи: ${batteryLevel}%. Рекомендуется лёгкая модель для экономии питания."
            else -> null
        }

        Log.d(TAG, "freeHeap=${freeHeapMb}MB, availRAM=${availRamMb}MB, battery=${batteryLevel}%, batterySaver=$isBatterySaverOn, safe=$isSafe, maxTokens=$recommendedMaxTokens")

        ResourceCheckResult(
            isSafeForGeneration = isSafe,
            freeHeapMb = freeHeapMb,
            availRamMb = availRamMb,
            batteryLevel = batteryLevel,
            isBatterySaverOn = isBatterySaverOn,
            batteryWarning = batteryWarning,
            recommendedMaxTokens = recommendedMaxTokens
        )
    }

    private fun getBatteryLevel(): Int {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter) ?: return -1
            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) -1 else (level * 100 / scale)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get battery level", e)
            -1
        }
    }

    private fun isBatterySaverEnabled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isPowerSaveMode
            } else false
        } catch (e: Exception) {
            false
        }
    }
}
