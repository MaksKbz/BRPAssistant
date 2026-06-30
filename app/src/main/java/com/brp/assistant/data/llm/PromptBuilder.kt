package com.brp.assistant.data.llm

import com.brp.assistant.data.db.entities.Accessory
import com.brp.assistant.data.db.entities.BrpModel
import com.brp.assistant.data.db.entities.KnowledgeCard
import com.brp.assistant.domain.model.ChatMessage

class PromptBuilder {

    companion object {
        // FIX: сокращённый промпт для экономии контекста.
        // Старый (1458 символов) переполнял KV-cache моделей ekv1280 → мусор/краш.
        // Ключевые правила сохранены, детали убраны (модель знает BRP из обучения).
        private const val SYSTEM_PROMPT = """Ты — BRP Assistant: эксперт по технике BRP (Can-Am, Ski-Doo, Sea-Doo, Lynx).

ПРАВИЛА:
1. ВСЕГДА отвечай ТОЛЬКО на русском языке
2. Отвечай по данным каталогов и базы знаний
3. Для аксессуаров указывай SKU, описание, цену и совместимость
4. Для ремонта давай пошаговые инструкции с предупреждениями
5. При недостатке данных рекомендуй обратиться к дилеру BRP"""

        /**
         * FIX #oom-guard: ограничения контекста для защиты от OOM.
         *
         * На Samsung A03 / Redmi 9A (3 ГБ RAM) история из 50+ сообщений
         * занимала 2–5 МБ в heap, плюс MediaPipe копировал всё в нативную
         * память при старте инференса — что приводило к OutOfMemoryError.
         *
         * MAX_HISTORY_MESSAGES: максимум сообщений из истории в промпте.
         * MAX_MSG_CHARS: длинные сообщения обрезаются — типичное сообщение
         *   пользователя редко несёт полезной информации после 500 символов.
         * MAX_CONTEXT_CHARS: абсолютный потолок символов в блоке истории.
         */
        private const val MAX_HISTORY_MESSAGES = 6
        private const val MAX_MSG_CHARS = 500
        private const val MAX_CONTEXT_CHARS = 4_000
    }

    // ============================================================
    // ВСПОМОГАТЕЛЬНЫЕ
    // ============================================================

    /**
     * Возвращает безопасный срез истории:
     * последние MAX_HISTORY_MESSAGES сообщений, каждое обрезано до MAX_MSG_CHARS.
     * Общий объём не превышает MAX_CONTEXT_CHARS символов.
     */
    private fun safeHistory(history: List<ChatMessage>): List<ChatMessage> {
        val slice = history.takeLast(MAX_HISTORY_MESSAGES)
        var total = 0
        val result = mutableListOf<ChatMessage>()
        for (msg in slice) {
            val truncated = msg.content.truncate(MAX_MSG_CHARS)
            val len = truncated.length + 20  // +20 для ролевого префикса
            if (total + len > MAX_CONTEXT_CHARS) break
            result.add(msg.copy(content = truncated))
            total += len
        }
        return result
    }

    /** Обрезает строку до [max] символов с маркером "…" если она длиннее. */
    private fun String.truncate(max: Int): String =
        if (length <= max) this else take(max - 1) + "…"

    private fun histBlock(history: List<ChatMessage>): String =
        safeHistory(history).joinToString("\n") { "${it.role.label}: ${it.content}" }

    // ============================================================
    // ДИАГНОСТИКА
    // ============================================================
    fun buildDiagnosisPrompt(
        userMessage: String,
        cards: List<KnowledgeCard>,
        selectedModel: BrpModel?,
        history: List<ChatMessage>,
        style: PromptStyle = PromptStyle.CHATML,
        customSystemPrompt: String = ""
    ): String {
        val modelCtx = selectedModel?.let { m ->
            """МАШИНА КЛИЕНТА:
  Бренд: ${m.brand.uppercase()}
  Модель: ${m.modelName} (${m.modelYear})
  Двигатель: ${m.engineName ?: "N/A"} ${m.horsepower?.let { "$it л.с." } ?: ""} ${m.displacementCc?.let { "(${it}cc)" } ?: ""}
  Трансмиссия: ${m.transmission ?: "N/A"}
  Привод: ${m.driveType ?: "N/A"}"""
        } ?: "МАШИНА: клиент ещё не указал модель. Спроси какая у него техника."

        val cardsBlock = cards.take(2).joinToString("\n\n") { c ->
            buildString {
                append("[КАРТОЧКА: ${c.symptom}]\n")
                append("Бренд: ${c.brand} | Тип: ${c.equipmentType} | Узел: ${c.node}\n")
                append("Причины: ${c.causes}\n")
                append("Шаги: ${c.steps}\n")
                append("МОЖНО: ${c.canDo}\n")
                append("НЕЛЬЗЯ: ${c.mustNotDo}\n")
                if (c.stopConditions != "[]") append("⛔ СТОП: ${c.stopConditions}\n")
                if (c.requiresEvacuation == 1) append("🚨 ЭВАКУАЦИЯ!\n")
                append("Уровень риска: ${c.riskLevel}")
            }
        }

        val content = """=== РЕЖИМ ДИАГНОСТИКИ ===

$modelCtx

БАЗА ЗНАНИЙ (проверенные инструкции BRP):
$cardsBlock

ИСТОРИЯ:
${histBlock(history)}

КЛИЕНТ: $userMessage"""

        val finalContent = if (customSystemPrompt.isNotBlank()) {
            "$content\n\nВАЖНЫЕ ДАННЫЕ ОТ ВЛАДЕЛЬЦА:\n$customSystemPrompt"
        } else {
            content
        }
        return wrapWithStyle(SYSTEM_PROMPT, finalContent, style)
    }

    // ============================================================
    // АКСЕССУАРЫ
    // ============================================================
    fun buildAccessoryPrompt(
        userMessage: String,
        accessories: List<Accessory>,
        selectedModel: BrpModel?,
        history: List<ChatMessage>,
        style: PromptStyle = PromptStyle.CHATML,
        customSystemPrompt: String = ""
    ): String {
        val modelCtx = selectedModel?.let { m ->
            "КЛИЕНТ: ${m.brand.uppercase()} ${m.modelName} (${m.modelYear}), платформа: ${m.platform ?: "N/A"}"
        } ?: "МОДЕЛЬ НЕ ВЫБРАНА. Спроси клиента какая у него машина."

        val accBlock = accessories.take(3).joinToString("\n\n") { a ->
            buildString {
                append("[SKU: ${a.sku}] ${a.name}")
                if (a.isNew2026 == 1) append(" ⭐ NEW 2026")
                append("\n")
                a.compatiblePlatforms?.let { append("Совместимость: $it\n") }
                append("Описание: ${a.description}")
                if (a.requiresProfessionalInstall == 1) append("\n⚠️ Установка у дилера")
                a.requiresParts?.let { if (it.isNotBlank()) append("\nДоп. SKU: $it") }
            }
        }

        val content = """=== РЕЖИМ ПОДБОРА АКСЕССУАРОВ ===

$modelCtx

АКСЕССУАРЫ ИЗ КАТАЛОГА:
$accBlock

ИСТОРИЯ:
${histBlock(history)}

КЛИЕНТ: $userMessage

ПРАВИЛА ПОДБОРА:
1. Уточни задачи, бюджет, условия эксплуатации
2. Предлагай только аксессуары из списка с SKU и ценой
3. Проверяй совместимость с платформой клиента
4. Укажи если нужна установка у дилера
5. Напомни про LinQ (tool-free установка)"""

        val finalContent = if (customSystemPrompt.isNotBlank()) {
            "$content\n\nВАЖНЫЕ ДАННЫЕ ОТ ВЛАДЕЛЬЦА:\n$customSystemPrompt"
        } else {
            content
        }
        return wrapWithStyle(SYSTEM_PROMPT, finalContent, style)
    }

    // ============================================================
    // ВЫБОР МОДЕЛИ
    // ============================================================
    fun buildModelSelectionPrompt(
        userMessage: String,
        models: List<BrpModel>,
        history: List<ChatMessage>,
        style: PromptStyle = PromptStyle.CHATML,
        customSystemPrompt: String = ""
    ): String {
        val modelBlock = models.take(3).joinToString("\n\n") { m ->
            buildString {
                append("[${m.id}] ${m.brand.uppercase()} ${m.modelName}")
                append("\nДвигатель: ${m.engineName ?: "N/A"} ${m.horsepower?.let { "$it л.с." } ?: ""}")
                m.displacementCc?.let { append(" (${it}cc)") }
                append("\nТип: ${m.subcategory}")
                m.transmission?.let { append(" | КПП: $it") }
                if (m.isElectric == 1) append("\n⚡ ЭЛЕКТРО")
            }
        }

        val content = """=== РЕЖИМ ВЫБОРА МОДЕЛИ ===

КЛИЕНТ: $userMessage

ПОДХОДЯЩИЕ МОДЕЛИ 2026:
$modelBlock

ИСТОРИЯ:
${histBlock(history)}

ПРАВИЛА:
1. Уточни опыт, задачи, бюджет, условия
2. Сравни модели по ключевым параметрам
3. Рекомендуй от простого к продвинутому
4. Упомяни NEW 2026 модели
5. Объясни разницу двигателей (E-TEC vs ACE vs Turbo)"""

        val finalContent = if (customSystemPrompt.isNotBlank()) {
            "$content\n\nВАЖНЫЕ ДАННЫЕ ОТ ВЛАДЕЛЬЦА:\n$customSystemPrompt"
        } else {
            content
        }
        return wrapWithStyle(SYSTEM_PROMPT, finalContent, style)
    }

    // ============================================================
    // СВОБОДНЫЙ ЧАТ
    // ============================================================
    fun buildFreeChatPrompt(
        userMessage: String,
        cards: List<KnowledgeCard>,
        accessories: List<Accessory>,
        selectedModel: BrpModel?,
        history: List<ChatMessage>,
        style: PromptStyle = PromptStyle.CHATML,
        customSystemPrompt: String = ""
    ): String {
        val isComparison = userMessage.contains("сравни", ignoreCase = true) ||
                           userMessage.contains("разница", ignoreCase = true)

        val modelCtx = if (selectedModel != null && !isComparison) {
            """ТЕХНИКА КЛИЕНТА (ОТВЕЧАЙ СТРОГО ПО ЭТОЙ МОДЕЛИ):
  Бренд: ${selectedModel.brand.uppercase()}
  Модель: ${selectedModel.modelName} (${selectedModel.modelYear})
  Вид: ${selectedModel.category} / ${selectedModel.subcategory}
  Двигатель: ${selectedModel.engineName ?: "N/A"}
  Платформа: ${selectedModel.platform ?: "N/A"}"""
        } else if (isComparison) {
            "РЕЖИМ СРАВНЕНИЯ: Пользователь просит сравнить разные модели. Используй только технические данные."
        } else {
            "ТЕХНИКА НЕ ВЫБРАНА. Спроси клиента какая у него модель BRP."
        }

        val cardsBlock = if (cards.isNotEmpty() && !isComparison) {
            "ТЕХНИЧЕСКИЕ ДАННЫЕ ИЗ СПРАВОЧНИКОВ:\n" + cards.take(2).joinToString("\n") { "- ${it.symptom}: ${it.causes}" }
        } else ""

        val accBlock = if (accessories.isNotEmpty()) {
            "СОВМЕСТИМЫЕ АКСЕССУАРЫ:\n" + accessories.take(3).joinToString("\n\n") {
                val sb = StringBuilder("- ${it.name} (SKU: ${it.sku})")
                if (it.description.isNotBlank()) sb.append("\n  Описание: ${it.description}")
                it.msrpUsd?.let { p -> sb.append("\n  Цена: \$${p}") }
                it.compatiblePlatforms?.let { cp -> if (cp.isNotBlank()) sb.append("\n  Совместимость: $cp") }
                if (it.requiresProfessionalInstall == 1) sb.append("\n  ⚠️ Установка у дилера")
                sb.toString()
            }
        } else ""

        val content = """=== РЕЖИМ ПЕРСОНАЛЬНОЙ КОНСУЛЬТАЦИИ ===

$modelCtx

$cardsBlock
$accBlock

ИСТОРИЯ:
${histBlock(history)}

КЛИЕНТ: $userMessage

ПРАВИЛО: Если техника выбрана, не предлагай решения для других брендов или моделей. Используй только предоставленные справочные данные."""

        val finalContent = if (customSystemPrompt.isNotBlank()) {
            "$content\n\nВАЖНЫЕ ДАННЫЕ ОТ ВЛАДЕЛЬЦА:\n$customSystemPrompt"
        } else {
            content
        }
        return wrapWithStyle(SYSTEM_PROMPT, finalContent, style)
    }

    private fun wrapWithStyle(system: String, content: String, style: PromptStyle): String {
        return when (style) {
            PromptStyle.CHATML -> {
                "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$content<|im_end|>\n<|im_start|>assistant\n"
            }
            PromptStyle.QWEN3 -> {
                "<|im_start|>system\n$system /no_think<|im_end|>\n<|im_start|>user\n$content<|im_end|>\n<|im_start|>assistant\n"
            }
            PromptStyle.PHI3 -> {
                "<|system|>\n$system<|end|>\n<|user|>\n$content<|end|>\n<|assistant|>\n"
            }
            PromptStyle.GEMMA -> {
                "<start_of_turn>user\n$system\n\n$content<end_of_turn>\n<start_of_turn>model\n"
            }
        }
    }

    // ============================================================
    // КОНСУЛЬТАНТ ПО JSON
    // ============================================================
    fun buildConsultantPrompt(
        model: OfflineModelInfo,
        jsonContext: String,
        userQuestion: String
    ): String {
        val systemMessage = "Ты офлайн-консультант внутри мобильного приложения. Отвечай на русском языке. Используй данные из JSON-КОНТЕКСТА. Если точного ответа в JSON нет, скажи: 'В локальных данных приложения нет точной информации'. Не выдумывай цены, сроки, гарантии, условия доставки и характеристики."
        val fullUserMessage = "JSON-КОНТЕКСТ:\n$jsonContext\n\nВОПРОС ПОЛЬЗОВАТЕЛЯ: $userQuestion"

        return when (model.promptStyle) {
            PromptStyle.CHATML -> {
                "<|im_start|>system\n$systemMessage<|im_end|>\n<|im_start|>user\n$fullUserMessage<|im_end|>\n<|im_start|>assistant\n"
            }
            PromptStyle.QWEN3 -> {
                "<|im_start|>system\n$systemMessage /no_think<|im_end|>\n<|im_start|>user\n$fullUserMessage<|im_end|>\n<|im_start|>assistant\n"
            }
            PromptStyle.PHI3 -> {
                "<|system|>\n$systemMessage<|end|>\n<|user|>\n$fullUserMessage<|end|>\n<|assistant|>\n"
            }
            PromptStyle.GEMMA -> {
                "<start_of_turn>user\n$systemMessage\n\n$fullUserMessage<end_of_turn>\n<start_of_turn>model\n"
            }
        }
    }

    /**
     * Выбирает релевантные фрагменты из JSON, чтобы не перегружать контекст.
     *
     * FIX #maxLines: добавлен лимит maxLines=200.
     * Большие JSON-каталоги (>1000 строк) раньше проходили целиком через
     * joinToString перед обрезкой по maxChars — это создавало промежуточную
     * строку в heap размером до 5 МБ. Теперь relevantLines обрезаются до
     * maxLines сразу после фильтрации, до финального join.
     */
    fun selectRelevantJsonContext(
        fullJson: String,
        userQuestion: String,
        maxChars: Int = 10_000,
        maxLines: Int = 200
    ): String {
        if (fullJson.length <= maxChars) return fullJson

        val keywords = userQuestion.lowercase().split(Regex("\\P{L}+")).filter { it.length > 3 }
        if (keywords.isEmpty()) return fullJson.take(maxChars)

        val lines = fullJson.split("\n")
        val relevantLines = lines
            .filter { line -> keywords.any { kw -> line.lowercase().contains(kw) } }
            .take(maxLines)  // FIX: обрезаем до maxLines до join

        val result = relevantLines.joinToString("\n")
        return when {
            result.length > maxChars -> result.take(maxChars)
            result.isBlank()         -> fullJson.take(maxChars)
            else                     -> result
        }
    }
}
