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

@Serializable
enum class PromptStyle {
    CHATML,
    PHI3,
    GEMMA
}

/**
 * Каталог Android-совместимых офлайн-моделей в формате .task (LiteRT / MediaPipe).
 *
 * Все модели взяты из официальной коллекции litert-community на Hugging Face:
 *   https://huggingface.co/collections/litert-community/android-models
 *
 * Требования к MediaPipe LlmInference:
 *   https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android
 *
 * Диапазон устройств: от самых слабых (бюджетные 2 ГБ RAM) до флагманских (8+ ГБ RAM).
 */
object PublicOfflineModelCatalog {

    val models: List<OfflineModelInfo> = listOf(

        // ─────────────────────────────────────────────────────────────────────
        // УРОВЕНЬ 1: Ultra-Lite (любой телефон, 2+ ГБ RAM)
        // ─────────────────────────────────────────────────────────────────────

        OfflineModelInfo(
            id = "smollm2_135m_task",
            title = "SmolLM2 135M • 135 MB",
            repoId = "litert-community/SmolLM2-135M-Instruct",
            filename = "smollm2-135m-instruct_multi-prefill-seq_q8_ekv1024.task",
            license = "Apache 2.0",
            approxSizeMb = 135,
            minRamGb = 2,
            promptStyle = PromptStyle.CHATML,
            description = "Минимальная модель — 135 МБ. Запускается абсолютно на любом Android-устройстве (от 2 ГБ RAM). Отвечает на простые вопросы.",
            downloadUrl = "https://huggingface.co/litert-community/SmolLM2-135M-Instruct/resolve/main/smollm2-135m-instruct_multi-prefill-seq_q8_ekv1024.task"
        ),

        OfflineModelInfo(
            id = "gemma3_270m_task",
            title = "Gemma 3 270M • 270 MB",
            repoId = "litert-community/Gemma3-270M-IT",
            filename = "gemma3-270m-it_multi-prefill-seq_q8_ekv1024.task",
            license = "Gemma",
            approxSizeMb = 270,
            minRamGb = 2,
            promptStyle = PromptStyle.GEMMA,
            description = "270 МБ от Google. Самая маленькая модель семейства Gemma 3. Подходит для старых и бюджетных телефонов.",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-270M-IT/resolve/main/gemma3-270m-it_multi-prefill-seq_q8_ekv1024.task"
        ),

        // ─────────────────────────────────────────────────────────────────────
        // УРОВЕНЬ 2: Light (3–4 ГБ RAM — среднебюджетные)
        // ─────────────────────────────────────────────────────────────────────

        OfflineModelInfo(
            id = "qwen2_5_0_5b_it_task",
            title = "Qwen 2.5 0.5B • 450 MB",
            repoId = "litert-community/Qwen2.5-0.5B-Instruct",
            filename = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1024.task",
            license = "Apache 2.0",
            approxSizeMb = 450,
            minRamGb = 3,
            promptStyle = PromptStyle.CHATML,
            description = "450 МБ. Лёгкая модель Alibaba. Хорошо работает на слабых и средних телефонах (от 3 ГБ RAM).",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1024.task"
        ),

        OfflineModelInfo(
            id = "gemma3_1b_it_task",
            title = "Gemma 3 1B • 657 MB",
            repoId = "litert-community/Gemma3-1B-IT",
            filename = "gemma3-1b-it_multi-prefill-seq_q4_w_ekv1280.task",
            license = "Gemma",
            approxSizeMb = 657,
            minRamGb = 3,
            promptStyle = PromptStyle.GEMMA,
            description = "657 МБ (int4). Google Gemma 3 1B — в 4 раза быстрее по сравнению с FP32. Рекомендована для большинства устройств.",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it_multi-prefill-seq_q4_w_ekv1280.task"
        ),

        // ─────────────────────────────────────────────────────────────────────
        // УРОВЕНЬ 3: Mid-Range (4 ГБ RAM — большинство Android 2022+)
        // ─────────────────────────────────────────────────────────────────────

        OfflineModelInfo(
            id = "qwen2_5_1_5b_it_task",
            title = "Qwen 2.5 1.5B • 1.0 ГБ ⭐",
            repoId = "litert-community/Qwen2.5-1.5B-Instruct",
            filename = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1024.task",
            license = "Apache 2.0",
            approxSizeMb = 1000,
            minRamGb = 4,
            promptStyle = PromptStyle.CHATML,
            description = "1 ГБ. Лучший баланс качества и скорости. Рекомендуем для большинства средних устройств (4 ГБ RAM).",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1024.task"
        ),

        OfflineModelInfo(
            id = "deepseek_r1_1_5b_task",
            title = "DeepSeek-R1 1.5B • 1.8 ГБ",
            repoId = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
            filename = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.task",
            license = "MIT",
            approxSizeMb = 1831,
            minRamGb = 4,
            promptStyle = PromptStyle.CHATML,
            description = "1.8 ГБ. DeepSeek R1 — модель с цепочкой рассуждения (диагностика). Отлично анализирует неисправности техники. 81 000 загрузок/мес.",
            downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.task"
        ),

        OfflineModelInfo(
            id = "gemma2_2b_it_cpu_task",
            title = "Gemma 2 2B CPU • 1.5 ГБ",
            repoId = "litert-community/Gemma2-2B-IT",
            filename = "gemma2-2b-it-cpu-int8.task",
            license = "Gemma",
            approxSizeMb = 1500,
            minRamGb = 4,
            promptStyle = PromptStyle.GEMMA,
            description = "1.5 ГБ. Gemma 2 2B от Google, CPU-вариант int8. Подходит для диалогов и инструкций. Работает на любом CPU.",
            downloadUrl = "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/gemma2-2b-it-cpu-int8.task"
        ),

        // ─────────────────────────────────────────────────────────────────────
        // УРОВЕНЬ 4: High-End (6+ ГБ RAM — Snapdragon 8 Gen1+, 8+ ГБ RAM)
        // ─────────────────────────────────────────────────────────────────────

        OfflineModelInfo(
            id = "gemma2_2b_it_gpu_task",
            title = "Gemma 2 2B GPU • 1.5 ГБ",
            repoId = "litert-community/Gemma2-2B-IT",
            filename = "gemma2-2b-it-gpu-int8.task",
            license = "Gemma",
            approxSizeMb = 1500,
            minRamGb = 6,
            promptStyle = PromptStyle.GEMMA,
            description = "1.5 ГБ. Gemma 2 2B, GPU-вариант. Работает значительно быстрее на устройствах с Snapdragon 8 Gen1 и выше.",
            downloadUrl = "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/gemma2-2b-it-gpu-int8.task"
        ),

        OfflineModelInfo(
            id = "phi4_mini_task",
            title = "Phi-4-mini 3.8B • 3.9 ГБ",
            repoId = "litert-community/Phi-4-mini-instruct",
            filename = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.task",
            license = "MIT",
            approxSizeMb = 3906,
            minRamGb = 8,
            promptStyle = PromptStyle.PHI3,
            description = "3.9 ГБ. Microsoft Phi-4-mini — флагманская офлайн-модель. Качество близко к онлайн-моделям. Требует 8+ ГБ RAM (Galaxy S23+, Pixel 8 Pro и аналоги).",
            downloadUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.task"
        )
    )

    fun defaultModel(): OfflineModelInfo = models.first { it.id == "qwen2_5_1_5b_it_task" }

    fun getById(id: String?): OfflineModelInfo? = models.find { it.id == id }

    /** Автоматически выбирает оптимальную модель под объём рам устройства */
    fun recommendedForRam(totalRamGb: Int): OfflineModelInfo = when {
        totalRamGb >= 8 -> models.first { it.id == "phi4_mini_task" }
        totalRamGb >= 6 -> models.first { it.id == "gemma2_2b_it_gpu_task" }
        totalRamGb >= 4 -> models.first { it.id == "qwen2_5_1_5b_it_task" }
        totalRamGb >= 3 -> models.first { it.id == "gemma3_1b_it_task" }
        else            -> models.first { it.id == "gemma3_270m_task" }
    }
}
