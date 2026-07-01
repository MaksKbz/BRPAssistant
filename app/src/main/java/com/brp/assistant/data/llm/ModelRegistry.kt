package com.brp.assistant.data.llm

import kotlinx.serialization.Serializable

/**
 * Формат файла модели — определяет, какой движок будет использован.
 *
 *  TASK      → MediaPipe LlmInference API (существующий движок)
 *              Файлы: *.task
 *              API:   com.google.mediapipe:tasks-genai
 *
 *  LITERTLM  → LiteRT-LM Engine (Stage 2, реализован)
 *              Файлы: *.litertlm
 *              API:   com.google.ai.edge.litertlm:litertlm-android
 *              Даёт доступ к Qwen3, Gemma4, Phi-4-mini-litertlm и другим
 *              новым моделям с поддержкой NPU, Tool Use, мультимодальности.
 */
@Serializable
enum class ModelFormat {
    /** .task — MediaPipe, текущий стабильный формат */
    TASK,

    /** .litertlm — LiteRT-LM, новое поколение */
    LITERTLM
}

@Serializable
enum class PromptStyle {
    CHATML,
    QWEN3,
    PHI3,
    GEMMA
}

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
    /** Формат файла — определяет движок вывода */
    val format: ModelFormat = ModelFormat.TASK,
    val downloadUrl: String? = null,
    val isCustom: Boolean = false
)

/**
 * Каталог Android-совместимых офлайн-моделей.
 *
 * Источник: официальная коллекция litert-community на Hugging Face
 *   https://huggingface.co/collections/litert-community/android-models
 *
 * Критерии включения модели:
 *   1. Формат .task или .litertlm (LiteRT / MediaPipe)
 *   2. Прямое скачивание без авторизации HuggingFace (не gated)
 *   3. Свободная лицензия: Apache 2.0 или MIT
 *
 * ИСКЛЮЧЕНЫ модели семейства Gemma (Gemma license, gated):
 *   требуют логин на HuggingFace + принятие лицензии — неприемлемо для UX.
 *
 * Диапазон устройств: от 135 МБ (2 ГБ RAM) до 5.7 ГБ (12 ГБ RAM).
 */
object PublicOfflineModelCatalog {

    val models: List<OfflineModelInfo> = listOf(

        // ─────────────────────────────────────────────────────────────────────
        // УРОВЕНЬ 1: Ultra-Lite — любой Android, от 2 ГБ RAM
        // ─────────────────────────────────────────────────────────────────────

        OfflineModelInfo(
            id           = "smollm2_135m_litertlm",
            title        = "SmolLM2 135M • 135 МБ",
            repoId       = "litert-community/SmolLM2-135M-Instruct",
            filename     = "SmolLM2_135M_Instruct.litertlm",
            license      = "Apache 2.0",
            approxSizeMb = 135,
            minRamGb     = 2,
            promptStyle  = PromptStyle.CHATML,
            format       = ModelFormat.LITERTLM,
            description  = "Самая лёгкая модель — 135 МБ. Запускается на любом Android-устройстве от 2 ГБ RAM. Отвечает на простые вопросы, подходит для базовой диагностики.",
            downloadUrl  = "https://huggingface.co/litert-community/SmolLM2-135M-Instruct/resolve/main/SmolLM2_135M_Instruct.litertlm"
        ),

        // ─────────────────────────────────────────────────────────────────────
        // УРОВЕНЬ 2: Light — бюджетный средний класс, от 3 ГБ RAM
        // ─────────────────────────────────────────────────────────────────────

        OfflineModelInfo(
            id           = "qwen2_5_0_5b_task",
            title        = "Qwen 2.5 0.5B • 547 МБ",
            repoId       = "litert-community/Qwen2.5-0.5B-Instruct",
            filename     = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            license      = "Apache 2.0",
            approxSizeMb = 547,
            minRamGb     = 3,
            promptStyle  = PromptStyle.CHATML,
            format       = ModelFormat.TASK,
            description  = "547 МБ. Лёгкая модель Alibaba Qwen 2.5. Хорошее качество для своего размера, работает на большинстве смартфонов от 3 ГБ RAM.",
            downloadUrl  = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
        ),

        // Qwen3 0.6B — LiteRT-LM
        // Источник: https://huggingface.co/litert-community/Qwen3-0.6B
        // Имя файла подтверждено через HF API: Qwen3-0.6B.litertlm
        OfflineModelInfo(
            id           = "qwen3_0_6b_litertlm",
            title        = "Qwen3 0.6B • 614 МБ ✨",
            repoId       = "litert-community/Qwen3-0.6B",
            filename     = "Qwen3-0.6B.litertlm",
            license      = "Apache 2.0",
            approxSizeMb = 614,
            minRamGb     = 3,
            promptStyle  = PromptStyle.QWEN3,
            format       = ModelFormat.LITERTLM,
            description  = "614 МБ. Qwen3 нового поколения (2025). Поддерживает режим размышлений: добавьте /think в запрос для пошагового анализа или /no_think для быстрого ответа. Требует 3 ГБ RAM.",
            downloadUrl  = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm"
        ),

        // ─────────────────────────────────────────────────────────────────────
        // УРОВЕНЬ 3: Mid-Range — флагманы 2021–2023, от 4 ГБ RAM
        // ─────────────────────────────────────────────────────────────────────

        OfflineModelInfo(
            id           = "qwen2_5_1_5b_task",
            title        = "Qwen 2.5 1.5B • 1.6 ГБ ⭐",
            repoId       = "litert-community/Qwen2.5-1.5B-Instruct",
            filename     = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            license      = "Apache 2.0",
            approxSizeMb = 1638,
            minRamGb     = 4,
            promptStyle  = PromptStyle.CHATML,
            format       = ModelFormat.TASK,
            description  = "1.6 ГБ. Рекомендуемая модель для большинства пользователей. Отличный баланс скорости и качества на устройствах от 4 ГБ RAM.",
            downloadUrl  = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
        ),

        OfflineModelInfo(
            id           = "deepseek_r1_1_5b_task",
            title        = "DeepSeek-R1 1.5B • 1.8 ГБ",
            repoId       = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
            filename     = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.task",
            license      = "MIT",
            approxSizeMb = 1831,
            minRamGb     = 4,
            promptStyle  = PromptStyle.CHATML,
            format       = ModelFormat.TASK,
            description  = "1.8 ГБ. DeepSeek R1 с цепочкой рассуждений. Думает пошагово перед ответом — лучший выбор для диагностики и анализа кодов ошибок BRP.",
            downloadUrl  = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.task"
        ),

        // Qwen3 1.7B — LiteRT-LM
        // Источник: https://huggingface.co/litert-community/Qwen3-1.7B
        // Имя файла подтверждено через HF API: Qwen3_1.7B.litertlm (подчёркивание!)
        //
        // FIX: minRamGb повышен с 4 до 5.
        // Модель 1.74 ГБ на диске требует ~2.5–3 ГБ свободной RAM при инференсе.
        // На устройствах с 4 ГБ физических (≈2.3 ГБ доступных при работающем фоне)
        // возникал OOM (OutOfMemoryError в нативном слое LiteRT-LM).
        // 5 ГБ физических = ~3.2 ГБ доступных — безопасный минимум.
        OfflineModelInfo(
            id           = "qwen3_1_7b_litertlm",
            title        = "Qwen3 1.7B • ~1.7 ГБ ✨",
            repoId       = "litert-community/Qwen3-1.7B",
            filename     = "Qwen3_1.7B.litertlm",
            license      = "Apache 2.0",
            approxSizeMb = 1740,
            minRamGb     = 5,
            promptStyle  = PromptStyle.QWEN3,
            format       = ModelFormat.LITERTLM,
            description  = "~1.7 ГБ. Qwen3 нового поколения с нативным режимом размышлений. Добавьте /think для глубокого анализа кодов ошибок BRP. Рекомендуется 5+ ГБ RAM (Snapdragon 778/7s Gen 2 и новее).",
            downloadUrl  = "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/main/Qwen3_1.7B.litertlm"
        ),

        // ─────────────────────────────────────────────────────────────────────
        // УРОВЕНЬ 4: High-End — Snapdragon 8 Gen1+, от 6 ГБ RAM
        // ─────────────────────────────────────────────────────────────────────

        OfflineModelInfo(
            id           = "qwen2_5_3b_task",
            title        = "Qwen 2.5 3B • 3.2 ГБ",
            repoId       = "litert-community/Qwen2.5-3B-Instruct",
            filename     = "Qwen2.5-3B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            license      = "Apache 2.0",
            approxSizeMb = 3200,
            minRamGb     = 6,
            promptStyle  = PromptStyle.CHATML,
            format       = ModelFormat.TASK,
            description  = "3.2 ГБ. Мощная модель Qwen 2.5 3B для топовых устройств (6+ ГБ RAM). Высокое качество развёрнутых ответов и технического анализа.",
            downloadUrl  = "https://huggingface.co/litert-community/Qwen2.5-3B-Instruct/resolve/main/Qwen2.5-3B-Instruct_multi-prefill-seq_q8_ekv1280.task"
        ),

        OfflineModelInfo(
            id           = "phi4_mini_task",
            title        = "Phi-4-mini 3.8B • 3.9 ГБ",
            repoId       = "litert-community/Phi-4-mini-instruct",
            filename     = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
            license      = "MIT",
            approxSizeMb = 3940,
            minRamGb     = 8,
            promptStyle  = PromptStyle.PHI3,
            format       = ModelFormat.TASK,
            description  = "3.9 ГБ. Microsoft Phi-4-mini — флагманская офлайн-модель. Качество близко к облачным сервисам. Требует 8+ ГБ RAM (Galaxy S23+, Pixel 8 Pro).",
            downloadUrl  = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task"
        ),

        // ─────────────────────────────────────────────────────────────────────
        // УРОВЕНЬ 5: Ultra High-End — флагманы 2024–2025, от 12 ГБ RAM
        // ─────────────────────────────────────────────────────────────────────

        // Qwen3 4B — channelwise int8 квантизация, 5.67 ГБ на диске
        // Нативный /think — thinking-mode, Tool Use, MCP-совместимость
        // Источник: https://huggingface.co/litert-community/Qwen3-4B
        // Имя файла подтверждено через HF API: qwen3_4b_channelwise_int8_float32kv.litertlm
        OfflineModelInfo(
            id           = "qwen3_4b_litertlm",
            title        = "Qwen3 4B • 5.7 ГБ 🔥",
            repoId       = "litert-community/Qwen3-4B",
            filename     = "qwen3_4b_channelwise_int8_float32kv.litertlm",
            license      = "Apache 2.0",
            approxSizeMb = 5810,
            minRamGb     = 12,
            promptStyle  = PromptStyle.QWEN3,
            format       = ModelFormat.LITERTLM,
            description  = "5.7 ГБ. Qwen3 4B — мощнейшая офлайн-модель каталога. Channelwise int8 квантизация. Нативный режим размышлений (/think), Tool Use, контекст 32K. Только для топ-флагманов с 12+ ГБ RAM.",
            downloadUrl  = "https://huggingface.co/litert-community/Qwen3-4B/resolve/main/qwen3_4b_channelwise_int8_float32kv.litertlm"
        )

    )

    /** Модель по умолчанию — оптимальный баланс для большинства устройств */
    fun defaultModel(): OfflineModelInfo = models.first { it.id == "qwen2_5_1_5b_task" }

    fun getById(id: String?): OfflineModelInfo? = models.find { it.id == id }

    /**
     * Автоматически рекомендует оптимальную модель по объёму RAM устройства.
     * Использование: ActivityManager.MemoryInfo().totalMem / 1_073_741_824L
     *
     * Стратегия выбора:
     *   12+ ГБ → Qwen3 4B (LiteRT-LM, thinking mode, Tool Use)
     *    8+ ГБ → Phi-4-mini (MediaPipe, стабильный, близко к GPT-уровню)
     *    6+ ГБ → Qwen 2.5 3B (MediaPipe, высокое качество)
     *    5+ ГБ → Qwen3 1.7B  (LiteRT-LM, новое поколение с /think)
     *              FIX: порог поднят с 4 до 5 ГБ — на 4 ГБ устройствах
     *              (~2.3 ГБ доступных) нативный LiteRT-LM даёт OOM
     *              при активных фоновых приложениях.
     *    3+ ГБ → Qwen3 0.6B  (LiteRT-LM, новое поколение, лёгкий)
     *    <3 ГБ → SmolLM2 135M (работает везде)
     *
     * FIX: исправлен id smollm2_135m_task → smollm2_135m_litertlm.
     * Ранее ветка else ссылалась на несуществующий id "smollm2_135m_task",
     * что приводило к NoSuchElementException на устройствах с <3 ГБ RAM.
     */
    fun recommendedForRam(totalRamGb: Int): OfflineModelInfo = when {
        totalRamGb >= 12 -> models.first { it.id == "qwen3_4b_litertlm" }
        totalRamGb >= 8  -> models.first { it.id == "phi4_mini_task" }
        totalRamGb >= 6  -> models.first { it.id == "qwen2_5_3b_task" }
        totalRamGb >= 5  -> models.first { it.id == "qwen3_1_7b_litertlm" }
        totalRamGb >= 3  -> models.first { it.id == "qwen3_0_6b_litertlm" }
        else             -> models.first { it.id == "smollm2_135m_litertlm" }
    }
}
