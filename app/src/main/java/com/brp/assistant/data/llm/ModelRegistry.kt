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
 * Источник: официальная коллекция litert-community на Hugging Face
 *   https://huggingface.co/collections/litert-community/android-models
 *
 * Критерии включения модели:
 *   1. Формат .task (совместим с MediaPipe LlmInference API)
 *   2. Прямое скачивание без авторизации HuggingFace (не gated)
 *   3. Свободная лицензия: Apache 2.0 или MIT
 *
 * ИСКЛЮЧЕНЫ модели семейства Gemma (Gemma license, gated):
 *   требуют логин на HuggingFace + принятие лицензии — неприемлемо для UX.
 *
 * Диапазон устройств: от 135 МБ (2 ГБ RAM) до 3.9 ГБ (8 ГБ RAM).
 */
object PublicOfflineModelCatalog {

    val models: List<OfflineModelInfo> = listOf(

        // ─────────────────────────────────────────────────────────────────────
        // УРОВЕНЬ 1: Ultra-Lite — любой Android, от 2 ГБ RAM
        // ─────────────────────────────────────────────────────────────────────

        OfflineModelInfo(
            id             = "smollm2_135m_task",
            title          = "SmolLM2 135M • 135 МБ",
            repoId         = "litert-community/SmolLM2-135M-Instruct",
            filename       = "smollm2-135m-instruct_multi-prefill-seq_q8_ekv1024.task",
            license        = "Apache 2.0",
            approxSizeMb   = 135,
            minRamGb       = 2,
            promptStyle    = PromptStyle.CHATML,
            description    = "Самая лёгкая модель — 135 МБ. Запускается на любом Android-устройстве от 2 ГБ RAM. Отвечает на простые вопросы, подходит для базовой диагностики.",
            downloadUrl    = "https://huggingface.co/litert-community/SmolLM2-135M-Instruct/resolve/main/smollm2-135m-instruct_multi-prefill-seq_q8_ekv1024.task"
        ),

        // ─────────────────────────────────────────────────────────────────────
        // УРОВЕНЬ 2: Light — бюджетный средний класс, от 3 ГБ RAM
        // ─────────────────────────────────────────────────────────────────────

        OfflineModelInfo(
            id             = "qwen2_5_0_5b_task",
            title          = "Qwen 2.5 0.5B • 547 МБ",
            repoId         = "litert-community/Qwen2.5-0.5B-Instruct",
            filename       = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            license        = "Apache 2.0",
            approxSizeMb   = 547,
            minRamGb       = 3,
            promptStyle    = PromptStyle.CHATML,
            description    = "547 МБ. Лёгкая модель Alibaba Qwen 2.5. Хорошее качество для своего размера, работает на большинстве смартфонов от 3 ГБ RAM.",
            downloadUrl    = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
        ),

        // ─────────────────────────────────────────────────────────────────────
        // УРОВЕНЬ 3: Mid-Range — флагманы 2021–2023, от 4 ГБ RAM
        // ─────────────────────────────────────────────────────────────────────

        OfflineModelInfo(
            id             = "qwen2_5_1_5b_task",
            title          = "Qwen 2.5 1.5B • 1.6 ГБ ⭐",
            repoId         = "litert-community/Qwen2.5-1.5B-Instruct",
            filename       = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            license        = "Apache 2.0",
            approxSizeMb   = 1638,
            minRamGb       = 4,
            promptStyle    = PromptStyle.CHATML,
            description    = "1.6 ГБ. Рекомендуемая модель для большинства пользователей. Отличный баланс скорости и качества на устройствах от 4 ГБ RAM.",
            downloadUrl    = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
        ),

        OfflineModelInfo(
            id             = "deepseek_r1_1_5b_task",
            title          = "DeepSeek-R1 1.5B • 1.8 ГБ",
            repoId         = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
            filename       = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.task",
            license        = "MIT",
            approxSizeMb   = 1831,
            minRamGb       = 4,
            promptStyle    = PromptStyle.CHATML,
            description    = "1.8 ГБ. DeepSeek R1 с цепочкой рассуждений. Думает пошагово перед ответом — лучший выбор для диагностики и анализа кодов ошибок BRP.",
            downloadUrl    = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.task"
        ),

        // ─────────────────────────────────────────────────────────────────────
        // УРОВЕНЬ 4: High-End — Snapdragon 8 Gen1+, от 6 ГБ RAM
        // ─────────────────────────────────────────────────────────────────────

        OfflineModelInfo(
            id             = "qwen2_5_3b_task",
            title          = "Qwen 2.5 3B • 3.2 ГБ",
            repoId         = "litert-community/Qwen2.5-3B-Instruct",
            filename       = "Qwen2.5-3B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            license        = "Apache 2.0",
            approxSizeMb   = 3200,
            minRamGb       = 6,
            promptStyle    = PromptStyle.CHATML,
            description    = "3.2 ГБ. Мощная модель Qwen 2.5 3B для топовых устройств (6+ ГБ RAM). Высокое качество развёрнутых ответов и технического анализа.",
            downloadUrl    = "https://huggingface.co/litert-community/Qwen2.5-3B-Instruct/resolve/main/Qwen2.5-3B-Instruct_multi-prefill-seq_q8_ekv1280.task"
        ),

        OfflineModelInfo(
            id             = "phi4_mini_task",
            title          = "Phi-4-mini 3.8B • 3.9 ГБ",
            repoId         = "litert-community/Phi-4-mini-instruct",
            filename       = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
            license        = "MIT",
            approxSizeMb   = 3940,
            minRamGb       = 8,
            promptStyle    = PromptStyle.PHI3,
            description    = "3.9 ГБ. Microsoft Phi-4-mini — флагманская офлайн-модель. Качество близко к облачным сервисам. Требует 8+ ГБ RAM (Galaxy S23+, Pixel 8 Pro).",
            downloadUrl    = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task"
        )
    )

    /** Модель по умолчанию — оптимальный баланс для большинства устройств */
    fun defaultModel(): OfflineModelInfo = models.first { it.id == "qwen2_5_1_5b_task" }

    fun getById(id: String?): OfflineModelInfo? = models.find { it.id == id }

    /**
     * Автоматически рекомендует оптимальную модель по объёму RAM устройства.
     * Использование: ActivityManager.MemoryInfo().totalMem / 1_073_741_824L
     */
    fun recommendedForRam(totalRamGb: Int): OfflineModelInfo = when {
        totalRamGb >= 8 -> models.first { it.id == "phi4_mini_task" }
        totalRamGb >= 6 -> models.first { it.id == "qwen2_5_3b_task" }
        totalRamGb >= 4 -> models.first { it.id == "qwen2_5_1_5b_task" }
        totalRamGb >= 3 -> models.first { it.id == "qwen2_5_0_5b_task" }
        else            -> models.first { it.id == "smollm2_135m_task" }
    }
}
