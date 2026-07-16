package com.brp.assistant.data.llm

import com.brp.assistant.data.db.entities.Accessory
import com.brp.assistant.data.db.entities.BrpModel
import com.brp.assistant.data.db.entities.KnowledgeCard
import com.brp.assistant.data.db.entities.KnowledgeChunk
import com.brp.assistant.domain.model.ChatMessage

class PromptBuilder @Inject constructor(
    private val systemPromptProvider: SystemPromptProvider
) {

    companion object {
        // FIX: ультра-короткий промпт для маленьких офлайн-моделей.
        // Маленькие квантизованные модели (0.5B-1.5B) не справляются с длинными
        // инструкциями и начинают галлюцинировать. Однако теперь полноценный
        // промпт подгружается из assets/prompts/system_prompt.txt через
        // SystemPromptProvider, а эта константа — лишь запасной вариант.
        private const val FALLBACK_SYSTEM_PROMPT =
            "Ты BRP-ассистент. Отвечай кратко на русском. Используй только предоставленные факты."

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
        return wrapWithStyle(systemPromptProvider.getSystemPrompt(), finalContent, style)
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
        return wrapWithStyle(systemPromptProvider.getSystemPrompt(), finalContent, style)
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
        return wrapWithStyle(systemPromptProvider.getSystemPrompt(), finalContent, style)
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
        return wrapWithStyle(systemPromptProvider.getSystemPrompt(), finalContent, style)
    }

    private fun wrapWithStyle(system: String, content: String, style: PromptStyle): String {
        // Для QWEN3 добавляем /no_think чтобы отключить reasoning у моделей
        // с thinking-tokens (они хуже работают на офлайн-устройствах).
        val wrappedSystem = if (style == PromptStyle.QWEN3) "$system /no_think" else system
        return when (style) {
            PromptStyle.CHATML -> {
                "<|im_start|>system\n$wrappedSystem<|im_end|>\n<|im_start|>user\n$content<|im_end|>\n<|im_start|>assistant\n"
            }
            PromptStyle.QWEN3 -> {
                "<|im_start|>system\n$wrappedSystem<|im_end|>\n<|im_start|>user\n$content<|im_end|>\n<|im_start|>assistant\n"
            }
            PromptStyle.PHI3 -> {
                "<|system|>\n$wrappedSystem<|end|>\n<|user|>\n$content<|end|>\n<|assistant|>\n"
            }
            PromptStyle.GEMMA -> {
                "<start_of_turn>user\n$wrappedSystem\n\n$content<end_of_turn>\n<start_of_turn>model\n"
            }
        }
    }

    /**
     * УЛЬТРА-ПРОСТОЙ промпт для локальных офлайн-моделей.
     *
     * Маленькие квантизованные модели (0.5B-1.5B) не справляются с длинными
     * инструкциями, RAG-контекстом, заголовками и правилами — начинают
     * галлюцинировать, выдавать BPE-мусор или отвечать не по теме.
     *
     * Этот метод строит МИНИМАЛЬНЫЙ промпт:
     *   system: короткая роль + контакты дилера (если есть)
     *   user: только вопрос клиента + техника (если выбрана)
     * Без RAG, без истории, без заголовков.
     */
    fun buildLocalChatPrompt(
        userMessage: String,
        selectedModel: BrpModel?,
        customSystemPrompt: String = "",
        accessories: List<Accessory> = emptyList(),
        cards: List<KnowledgeCard> = emptyList(),
        chunks: List<KnowledgeChunk> = emptyList(),
        userChunks: List<com.brp.assistant.data.rag.UserChunk> = emptyList()
    ): String {
        val baseSystem = systemPromptProvider.getSystemPrompt()
        val contacts = if (customSystemPrompt.isNotBlank()) "\n$customSystemPrompt" else ""
        val vehicle = selectedModel?.let {
            "\nТехника клиента: ${it.brand.uppercase()} ${it.modelName} ${it.modelYear}. " +
                "Двигатель: ${it.engineName ?: "N/A"}. " +
                "Платформа: ${it.platform ?: "N/A"}."
        } ?: "\nКлиент пока не выбрал технику — можно кратко уточнить модель BRP."

        val accSection = if (accessories.isNotEmpty()) {
            "\nОРИГИНАЛЬНЫЕ АКСЕССУАРЫ ИЗ КАТАЛОГА BRP:\n" + accessories.take(4).joinToString("\n") {
                buildString {
                    append("- ${it.name}")
                    append(" | SKU: ${it.sku}")
                    if (it.msrpUsd != null) append(" | \$${it.msrpUsd}")
                    if (it.requiresProfessionalInstall == 1) append(" | ⚠️ установка у дилера")
                }
            }
        } else ""

        // Чанки встроенных карточек BRP — в сжатом виде
        val chunksSection = if (chunks.isNotEmpty()) {
            "\nФРАГМЕНТЫ ИЗ БАЗЫ BRP:\n" +
                chunks.take(4).distinctBy { it.cardId + (it.section ?: "") }.joinToString("\n\n") { ch ->
                    val body = ch.content
                        .replace(Regex("^[\\s*#\\-•\\d\\.]+"), "")
                        .replace(Regex("\\n{2,}"), "\n")
                        .take(450)
                    "[${ch.section ?: "Фрагмент"}] ${body.trim()}"
                }
        } else ""

        // Чанки ПОЛЬЗОВАТЕЛЬСКИХ документов — с указанием имени документа
        val userChunksSection = if (userChunks.isNotEmpty()) {
            "\nИЗ ВАШЕЙ БАЗЫ ЗНАНИЙ:\n" +
                userChunks.take(4).distinctBy { it.chunk.id }.joinToString("\n\n") { uc ->
                    val body = uc.chunk.content
                        .replace(Regex("^[\\s*#\\-•\\d\\.]+"), "")
                        .replace(Regex("\\n{2,}"), "\n")
                        .take(450)
                    "[${uc.docName} / ${uc.chunk.section ?: "Фрагмент"}] ${body.trim()}"
                }
        } else ""

        val cardsSection = if (cards.isNotEmpty()) {
            "\nИНСТРУКЦИИ ПО СВЯЗАННЫМ ПРОБЛЕМАМ:\n" +
                cards.take(if (chunks.isEmpty()) 3 else 2).joinToString("\n\n") { c ->
                    buildString {
                        append("【${c.symptom}】")
                        append(" (${c.brand}/${c.equipmentType}, риск: ${c.riskLevel})")
                        if (c.causes.isNotBlank() && c.causes != "[]")
                            append("\nПричины: ${stripJsonList(c.causes).take(240)}")
                        if (c.canDo.isNotBlank() && c.canDo != "[]")
                            append("\nЧто делать: ${stripJsonList(c.canDo).take(240)}")
                        if (c.mustNotDo.isNotBlank() && c.mustNotDo != "[]")
                            append("\nНЕЛЬЗЯ: ${stripJsonList(c.mustNotDo).take(180)}")
                    }
                }
        } else ""

        val modeHint = if (accessories.isNotEmpty()) {
            "\nРЕЖИМ: Подбор аксессуаров. Укажи SKU и совместимость с техникой клиента."
        } else if (cards.isNotEmpty()) {
            "\nРЕЖИМ: Диагностика/ремонт. Дай пошаговые действия на русском. Начни с безопасности. " +
                "Если риск high/critical — предупреди в первой строке. Не выдумывай ничего сверх инструкций."
        } else ""

        val system = "$baseSystem$contacts\nКраткость: отвечай по существу, 3–6 пунктов. Используй приведённые факты. Если информация есть в «ВАШЕЙ БАЗЕ ЗНАНИЙ» — приоритет ей."
        val content = "$vehicle$chunksSection$userChunksSection$cardsSection$accSection$modeHint\n\nВОПРОС КЛИЕНТА: $userMessage"

        return wrapWithStyle(system, content, PromptStyle.CHATML)
    }

    /**
     * Конвертирует JSON-массив вида `["один","два"]` в простую строку "один; два".
     * Используется при рендеринге полей [KnowledgeCard] в промпт для LLM.
     */
    private fun stripJsonList(json: String): String = runCatching {
        val content = json.trim().removeSurrounding("[", "]")
        if (content.isBlank()) ""
        else content.split(",").joinToString("; ") {
            it.trim().removeSurrounding("\"").removeSurrounding("'")
        }
    }.getOrDefault(json)

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
