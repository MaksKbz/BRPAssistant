package com.brp.assistant.domain.model

/**
 * Карточка неисправности / симптома, отображаемая на экране диагностики.
 *
 * @param id          Уникальный идентификатор
 * @param symptom     Короткое описание симптома (показывается на кнопке)
 * @param description Подробное описание для промпта
 * @param severity    Степень серьёзности
 * @param vehicleTags Техника, для которой актуальна карточка (пустой = для всех)
 * @param chatQuery   Запрос, передаваемый в ChatScreen при нажатии
 */
data class FaultCard(
    val id: String,
    val symptom: String,
    val description: String = "",
    val severity: Severity = Severity.UNKNOWN,
    val vehicleTags: Set<String> = emptySet(),
    val chatQuery: String = symptom
) {
    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN }
}

/** Предопределённый набор для диагностики BRP */
object BrpFaultCatalog {
    val cards: List<FaultCard> = listOf(
        FaultCard(
            id = "engine_no_start",
            symptom = "Двигатель не заводится",
            severity = FaultCard.Severity.HIGH,
            vehicleTags = setOf("can-am", "ski-doo", "lynx"),
            chatQuery = "Двигатель не заводится. Проверь все возможные причины."
        ),
        FaultCard(
            id = "4wd_off",
            symptom = "Не включается 4WD",
            severity = FaultCard.Severity.MEDIUM,
            vehicleTags = setOf("can-am"),
            chatQuery = "Не включается 4WD на Can-Am. Что проверить?"
        ),
        FaultCard(
            id = "cvt_belt",
            symptom = "Проблемы с CVT ремнём",
            severity = FaultCard.Severity.HIGH,
            vehicleTags = setOf("can-am"),
            chatQuery = "Проблемы с CVT ремнём: скольжение, запах, вибрация."
        ),
        FaultCard(
            id = "overheat",
            symptom = "Перегрев двигателя",
            severity = FaultCard.Severity.CRITICAL,
            vehicleTags = setOf("can-am", "sea-doo"),
            chatQuery = "Перегрев двигателя. Причины и действия."
        ),
        FaultCard(
            id = "ibr_fault",
            symptom = "iBR не работает (Sea-Doo)",
            severity = FaultCard.Severity.HIGH,
            vehicleTags = setOf("sea-doo"),
            chatQuery = "Sea-Doo iBR не работает или ошибка iBR. Диагностика."
        ),
        FaultCard(
            id = "idle_stall_etec",
            symptom = "Глохнет на холостых (850 E-TEC)",
            severity = FaultCard.Severity.MEDIUM,
            vehicleTags = setOf("ski-doo", "lynx"),
            chatQuery = "Ski-Doo/Lynx 850 E-TEC глохнет на холостых. Причины."
        ),
        FaultCard(
            id = "belt_break",
            symptom = "Рвётся ремень (Ski-Doo/Lynx)",
            severity = FaultCard.Severity.HIGH,
            vehicleTags = setOf("ski-doo", "lynx"),
            chatQuery = "Рвётся ремень Ski-Doo/Lynx. Причины, замена, профилактика."
        ),
        FaultCard(
            id = "dct_issue",
            symptom = "Проблемы с DCT (Maverick R)",
            severity = FaultCard.Severity.HIGH,
            vehicleTags = setOf("can-am"),
            chatQuery = "Can-Am Maverick R DCT проблема: рычки, ошибки, не переключается."
        )
    )

    /** Фильтрация по привязке техники */
    fun forVehicle(brandSlug: String?): List<FaultCard> {
        if (brandSlug.isNullOrBlank()) return cards
        val tag = brandSlug.lowercase()
        return cards.filter { it.vehicleTags.isEmpty() || tag in it.vehicleTags }
    }
}
