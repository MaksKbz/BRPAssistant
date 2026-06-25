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
 * Объявленный, но нигде не применяемый enum-вариант создавал мёртвую ветку кода
 * в PromptBuilder и делал невозможным тестирование этого пути.
 * Вернуть при добавлении реальной Qwen3-модели в PublicOfflineModelCatalog.
 */
@Serializable
enum class PromptStyle {
    CHATML,
    PHI3
}

object PublicOfflineModelCatalog {
    val models = listOf(
        OfflineModelInfo(
            id = "qwen2_5_0_5b_it_bin",
            title = "Qwen 2.5 0.5B Instruct",
            repoId = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
            filename = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            license = "Apache 2.0",
            approxSizeMb = 420,
            minRamGb = 3,
            promptStyle = PromptStyle.CHATML,
            description = "Очень лёгкая локальная модель для слабых и средних телефонов. Хороший старт без API-ключей.",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true"
        ),
        OfflineModelInfo(
            id = "qwen2_5_1_5b_it_bin",
            title = "Qwen 2.5 1.5B Instruct",
            repoId = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            filename = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            license = "Apache 2.0",
            approxSizeMb = 980,
            minRamGb = 4,
            promptStyle = PromptStyle.CHATML,
            description = "Лучший баланс качества и скорости для русскоязычного офлайн-чата на телефоне.",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true"
        ),
        OfflineModelInfo(
            id = "smollm2_1_7b_it_bin",
            title = "SmolLM2 1.7B Instruct",
            repoId = "HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF",
            filename = "smollm2-1.7b-instruct-q4_k_m.gguf",
            license = "Apache 2.0",
            approxSizeMb = 1100,
            minRamGb = 4,
            promptStyle = PromptStyle.CHATML,
            description = "Свободная компактная instruct-модель для локального использования без авторизации.",
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q4_k_m.gguf?download=true"
        ),
        OfflineModelInfo(
            id = "phi3_5_mini_it_bin",
            title = "Phi-3.5 Mini Instruct",
            repoId = "microsoft/Phi-3.5-mini-instruct-gguf",
            filename = "Phi-3.5-mini-instruct-q4_k_m.gguf",
            license = "MIT",
            approxSizeMb = 2300,
            minRamGb = 6,
            promptStyle = PromptStyle.PHI3,
            description = "Качественная свободная модель для мощных устройств. Хороша для рассуждений и длинных ответов.",
            downloadUrl = "https://huggingface.co/microsoft/Phi-3.5-mini-instruct-gguf/resolve/main/Phi-3.5-mini-instruct-q4_k_m.gguf?download=true"
        )
    )

    fun defaultModel(): OfflineModelInfo = models.first { it.id == "qwen2_5_1_5b_it_bin" }

    fun getById(id: String?): OfflineModelInfo? = models.find { it.id == id }

    fun recommendedForRam(totalRamGb: Int): OfflineModelInfo {
        return when {
            totalRamGb >= 6 -> models.first { it.id == "phi3_5_mini_it_bin" }
            totalRamGb >= 4 -> models.first { it.id == "qwen2_5_1_5b_it_bin" }
            else            -> models.first { it.id == "qwen2_5_0_5b_it_bin" }
        }
    }
}
