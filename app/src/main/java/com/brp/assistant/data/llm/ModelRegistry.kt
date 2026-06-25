package com.brp.assistant.data.llm

import kotlinx.serialization.Serializable

@Serializable
data class OfflineModelInfo(
    val id: String,
    val title: String,
    val repoId: String,
    val filename: String,
    val license: String,
    val approxSizeMb: Int,
    val minRamGb: Int,
    val promptStyle: PromptStyle,
    val description: String,
    val downloadUrl: String? = null,
    val isCustom: Boolean = false
)

/**
 * FIX #10: удалён PromptStyle.QWEN3 — ни одна модель в каталоге его не использует.
 */
@Serializable
enum class PromptStyle {
    CHATML,
    PHI3
}

/**
 * FIX (local inference crash): все модели заменены с .gguf на .task (LiteRT/TFLite bundle).
 *
 * Причина: MediaPipe LlmInference на Android ожидает файл в формате TFLite/LiteRT (.task),
 * а не GGUF. Передача .gguf в setModelPath() вызывает ошибку:
 *   "Failed to initialize session: RET_CHECK failure ... modelError building tflite model"
 *
 * Все модели взяты из официального репозитория litert-community на Hugging Face,
 * которые явно опубликованы как Android-совместимые LiteRT bundles.
 *
 * Источники:
 *   https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android
 *   https://huggingface.co/collections/litert-community/android-models
 */
object PublicOfflineModelCatalog {
    val models = listOf(

        // ~450 MB — минимальная модель, запускается на любом устройстве с 3+ ГБ RAM
        OfflineModelInfo(
            id = "qwen2_5_0_5b_it_task",
            title = "Qwen 2.5 0.5B Instruct",
            repoId = "litert-community/Qwen2.5-0.5B-Instruct",
            filename = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1024.task",
            license = "Apache 2.0",
            approxSizeMb = 450,
            minRamGb = 3,
            promptStyle = PromptStyle.CHATML,
            description = "Очень лёгкая локальная модель. Запускается без API-ключей на слабых и средних устройствах.",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1024.task"
        ),

        // ~1 ГБ — оптимальный баланс качества и скорости
        OfflineModelInfo(
            id = "qwen2_5_1_5b_it_task",
            title = "Qwen 2.5 1.5B Instruct",
            repoId = "litert-community/Qwen2.5-1.5B-Instruct",
            filename = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1024.task",
            license = "Apache 2.0",
            approxSizeMb = 1000,
            minRamGb = 4,
            promptStyle = PromptStyle.CHATML,
            description = "Лучший баланс качества и скорости для офлайн-чата. Рекомендуется для большинства устройств.",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1024.task"
        ),

        // ~1.5 ГБ — хорошее качество, поддерживает русский язык
        OfflineModelInfo(
            id = "gemma2_2b_it_task",
            title = "Gemma 2 2B Instruct",
            repoId = "litert-community/Gemma2-2B-IT",
            filename = "gemma2-2b-it-cpu-int8.task",
            license = "Gemma",
            approxSizeMb = 1500,
            minRamGb = 4,
            promptStyle = PromptStyle.CHATML,
            description = "Качественная модель от Google. Хороша для диалогов и инструкций.",
            downloadUrl = "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/gemma2-2b-it-cpu-int8.task"
        ),

        // ~2.3 ГБ — мощная модель для устройств с 6+ ГБ RAM
        OfflineModelInfo(
            id = "gemma2_3b_it_task",
            title = "Gemma 2 2B Instruct (GPU)",
            repoId = "litert-community/Gemma2-2B-IT",
            filename = "gemma2-2b-it-gpu-int8.task",
            license = "Gemma",
            approxSizeMb = 1500,
            minRamGb = 6,
            promptStyle = PromptStyle.CHATML,
            description = "GPU-вариант Gemma 2 2B. Работает быстрее на устройствах с хорошим GPU (Snapdragon 8 Gen2+).",
            downloadUrl = "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/gemma2-2b-it-gpu-int8.task"
        )
    )

    fun defaultModel(): OfflineModelInfo = models.first { it.id == "qwen2_5_1_5b_it_task" }

    fun getById(id: String?): OfflineModelInfo? = models.find { it.id == id }

    fun recommendedForRam(totalRamGb: Int): OfflineModelInfo {
        return when {
            totalRamGb >= 6 -> models.first { it.id == "gemma2_3b_it_task" }
            totalRamGb >= 4 -> models.first { it.id == "qwen2_5_1_5b_it_task" }
            else            -> models.first { it.id == "qwen2_5_0_5b_it_task" }
        }
    }
}
