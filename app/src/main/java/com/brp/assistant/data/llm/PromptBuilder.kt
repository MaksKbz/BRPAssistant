package com.brp.assistant.data.llm

import com.brp.assistant.data.db.enteties.Accessory
import com.brp.assistant.data.db.enteties.BrpModel
import com.brp.assistant.data.db.enteties.KnowledgeCard
import com.brp.assistant.domain.model.ChatMessage

class PromptBuilder {

    companion object {
        private const val SYSTEM_PROMPT = """Ты — BRP Assistant 2026: экспертный оффлайн-ассистент по технике BRP.
Ты знаешь все актуальные модели 2026, аксессуары и инструкции по обслуживанию.

БРЕНДЫ:
• Can-Am Off-Road: ATV (Outlander, Renegade), SSV (Traxter, Defender, Maverick)
• Can-Am On-Road: 3-Wheel (Ryker, Spyder, Canyon)
• Ski-Doo: Trail (MXZ, Renegade), Mountain (Summit, Freeride), Utility (Expedition, Skandic)
• Sea-Doo: Rec Lite (Spark), Recreation (GTI), Touring (GTX, Explorer), Performance (GTR, RXP-X), Fishing (FishPro)
• Lynx: Trail (Rave, Adventure), Mountain (Shredder, Brutal), Utility (Commander, Ranger)

ДВИГАТЕЛИ ROTAX 2026:
• 2-тактные: 600 EFI (85hp), 600R E-TEC (125hp), 850 E-TEC (165hp), 850 E-TEC Turbo R (180hp)
• 4-тактные: 600 ACE (62hp), 900 ACE (95hp), 900 ACE Turbo (130hp), 900 ACE Turbo R (180hp)
• Sea-Doo: 1630 ACE (130-325hp)
• Электро: Rotax E-Power

ПРАВИЛА:
1. Отвечай ТОЛЬКО на основе данных из встроенных каталогов и базы знаний
2. Уточняй модель и год техники клиента перед рекомендациями
3. Для аксессуаров указывай SKU и совместимость
4. Для ремонта давай пошаговые инструкции с предупреждениями безопасности
5. Если информации недостаточно — рекомендуй обратиться к дилеру BRP"""
    }

    // ============================================================
    // ДИАГНОСТИКА
    // ============================================================
    fun buildDiagnosisPrompt(
        userMessage: String,
        cards: List<KnowledgeCard>,
        selectedModel: BrpModel?,
        history: List<ChatMessage>,
        style: PromptStyle = PromptStyle.CHATML
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

        val histBlock = history.takeLast(3).joinToString("\n") { "${it.role.label}: ${it.content}" }

        val content = """=== РЕЖИМ ДИАГНОСТИКИ ===

$modelCtx

БАЗА ЗНАНИЙ (проверенные инструкции BRP):
$cardsBlock

ИСТОРИЯ:
$histBlock

КЛИЕНТ: $userMessage"""

        return wrapWithStyle(SYSTEM_PROMPT, content, style)
    }

    // ============================================================
    // АКСЕССУАРЫ
    // ============================================================
    fun buildAccessoryPrompt(
        userMessage: String,
        accessories: List<Accessory>,
        selectedModel: BrpModel?,
        history: List<ChatMessage>,
        style: PromptStyle = PromptStyle.CHATML
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

        val histBlock = history.takeLast(3).joinToString("\n") { "${it.role.label}: ${it.content}" }

        val content = """=== РЕЖИМ ПОДБОРА АКСЕССУАРОВ ===

$modelCtx

АКСЕССУАРЫ ИЗ КАТАЛОГА:
$accBlock

ИСТОРИЯ:
$histBlock

КЛИЕНТ: $userMessage

ПРАВИЛА ПОДБОРА:
1. Уточни задачи, бюджет, условия эксплуатации
2. Предлагай только аксессуары из списка с SKU и ценой
3. Проверяй совместимость с платформой клиента
4. Укажи если нужна установка у дилера
5. Напомни про LinQ (tool-free установка)"""

        return wrapWithStyle(SYSTEM_PROMPT, content, style)
    }

    // ============================================================
    // ВЫБОР МОДЕЛИ
    // ============================================================
    fun buildModelSelectionPrompt(
        userMessage: String,
        models: List<BrpModel>,
        history: List<ChatMessage>,
        style: PromptStyle = PromptStyle.CHATML
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

        val histBlock = history.takeLast(2).joinToString("\n") { "${it.role.label}: ${it.content}" }

        val content = """=== РЕЖИМ ВЫБОРА МОДЕЛИ ===

КЛИЕНТ: $userMessage

ПОДХОДЯЩИЕ МОДЕЛИ 2026:
$modelBlock

ИСТОРИЯ:
$histBlock

ПРАВИЛА:
1. Уточни опыт, задачи, бюджет, условия
2. Сравни модели по ключевым параметрам
3. Рекомендуй от простого к продвинутому
4. Упомяни NEW 2026 модели
5. Объясни разницу двигателей (E-TEC vs ACE vs Turbo)"""

        return wrapWithStyle(SYSTEM_PROMPT, content, style)
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
        style: PromptStyle = PromptStyle.CHATML
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
            "СОВМЕСТИМЫЕ АКСЕССУАРЫ:\n" + accessories.take(2).joinToString("\n") { "- ${it.name} (SKU: ${it.sku})" }
        } else ""

        val histBlock = history.takeLast(4).joinToString("\n") { "${it.role.label}: ${it.content}" }

        val content = """=== РЕЖИМ ПЕРСОНАЛЬНОЙ КОНСУЛЬТАЦИИ ===

$modelCtx

$cardsBlock
$accBlock

ИСТОРИЯ:
$histBlock

КЛИЕНТ: $userMessage

ПРАВИЛО: Если техника выбрана, не предлагай решения для других брендов или моделей. Используй только предоставленные справочные данные."""

        return wrapWithStyle(SYSTEM_PROMPT, content, style)
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
                // Добавляем /no_think для Qwen3 чтобы избежать длинных рассуждений
                "<|im_start|>system\n$systemMessage /no_think<|im_end|>\n<|im_start|>user\n$fullUserMessage<|im_end|>\n<|im_start|>assistant\n"
            }
            PromptStyle.PHI3 -> {
                "<|system|>\n$systemMessage<|end|>\n<|user|>\n$fullUserMessage<|end|>\n<|assistant|>\n"
            }
        }
    }

    /**
     * Выбирает релевантные фрагменты из JSON, чтобы не перегружать контекст.
     * Ищет ключевые слова из вопроса пользователя.
     */
    fun selectRelevantJsonContext(fullJson: String, userQuestion: String, maxChars: Int = 10000): String {
        if (fullJson.length <= maxChars) return fullJson

        val keywords = userQuestion.lowercase().split(Regex("\\P{L}+")).filter { it.length > 3 }
        if (keywords.isEmpty()) return fullJson.take(maxChars)

        // Простой поиск строк, содержащих ключевые слова
        val lines = fullJson.split("\n")
        val relevantLines = lines.filter { line ->
            keywords.any { kw -> line.lowercase().contains(kw) }
        }

        val result = relevantLines.joinToString("\n")
        return if (result.length > maxChars) result.take(maxChars) 
               else if (result.isBlank()) fullJson.take(maxChars) 
               else result
    }
}
