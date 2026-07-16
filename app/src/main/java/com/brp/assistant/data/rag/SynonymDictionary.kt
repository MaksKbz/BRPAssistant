package com.brp.assistant.data.rag

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Словарь синонимов и сленговых названий для техники BRP и типовых проблем.
 *
 * Расширяет пользовательский запрос дополнительными терминами, которые
 * используются в базе знаний, но не обязательно совпадают со словами
 * пользователя (например «квадрик» → «atv, outlander», «код» → «ошибка»
 * и т.п.). Используется в [UnifiedRetriever] при построении FTS-запроса.
 */
@Singleton
class SynonymDictionary @Inject constructor() {

    /**
     * Принимает исходный список терминов/слов из запроса и возвращает
     * расширенный список (оригиналы + синонимы/канонические термины).
     */
    fun expand(terms: List<String>): List<String> {
        val expanded = LinkedHashSet<String>()
        for (t in terms) {
            val key = t.lowercase().trim()
            if (key.length < 2) continue
            expanded.add(key)
            SYNONYMS[key]?.let { expanded.addAll(it) }
            // Частичное сопоставление (например «квадр» → «atv», «ремен» → «cvt/ремень»)
            for ((trigger, expansions) in PARTIAL) {
                if (key.contains(trigger)) {
                    expanded.addAll(expansions)
                }
            }
        }
        return expanded.toList()
    }

    companion object {
        // Точное совпадение
        private val SYNONYMS: Map<String, List<String>> = mapOf(
            // Техника
            "квадрик" to listOf("atv", "outlander", "renegade"),
            "квадроцикл" to listOf("atv", "квадр"),
            "квадр" to listOf("atv"),
            "ютик" to listOf("utv", "ssv", "defender", "traxter", "maverick"),
            "сайд" to listOf("ssv", "utv"),
            "багги" to listOf("ssv", "maverick", "defender"),
            "гидрик" to listOf("pwc", "sea-doo", "seadoo", "гидроцикл"),
            "гидроцикл" to listOf("pwc", "sea-doo", "seadoo"),
            "снежик" to listOf("snowmobile", "ski-doo", "skidoo", "снегоход"),
            "снегоход" to listOf("snowmobile", "ski-doo", "skidoo"),
            "мотик" to listOf("motorcycle"),
            "трайк" to listOf("3-wheel", "ryker", "spyder"),
            "райкер" to listOf("ryker"),
            "спайдер" to listOf("spyder"),

            // Проблемы
            "не заводится" to listOf("no start", "no-start", "пуск", "стартер"),
            "незаводится" to listOf("no start", "пуск", "стартер"),
            "глохнет" to listOf("stall", "заглох", "остановка"),
            "греется" to listOf("перегрев", "overheat", "temperature", "температур"),
            "перегрев" to listOf("overheat", "temperature", "охлаждение"),
            "дымит" to listOf("дым", "smoke", "выхлоп"),
            "стучит" to listOf("стук", "knock", "шум"),
            "свист" to listOf("whistle", "ремень", "cvt", "ролик"),
            "вибрация" to listOf("вибрац", "балансир", "кардан"),
            "течёт" to listOf("течь", "leak", "утечка", "течет"),
            "течет" to listOf("leak", "утечка", "течь"),
            "аккум" to listOf("аккумулятор", "battery", "батарея"),
            "аккумулятор" to listOf("battery", "акб"),
            "акб" to listOf("battery", "аккумулятор"),
            "ремень" to listOf("cvt", "belt", "вариатор"),
            "вариатор" to listOf("cvt", "ремень", "belt"),
            "лебедка" to listOf("winch", "лебёдка"),
            "лебёдка" to listOf("winch"),
            "кофр" to listOf("linq", "cargo", "trunk", "bag"),
            "стекло" to listOf("windshield", "ветровое"),

            // Коды / сервис
            "код" to listOf("ошибка", "fault", "diagnostic", "dtc"),
            "ошибка" to listOf("fault", "code", "dtc", "чек"),
            "чек" to listOf("ошибка", "check engine", "fault"),
            "лампа" to listOf("индикатор", "warning", "lamp"),
            "то" to listOf("обслуживание", "maintenance", "сервис"),
            "сервис" to listOf("обслуживание", "maintenance", "дилер"),
            "масло" to listOf("oil", "замена масла", "engine oil"),

            // Поломки отдельные
            "пробуксовка" to listOf("cvt", "ремень", "clutch"),
            "буксует" to listOf("cvt", "ремень", "clutch"),
            "рвёт" to listOf("break", "порвался", "ремень"),
            "рвется" to listOf("break", "порвался", "ремень"),
            "заводится" to listOf("start", "пуск"),
            "залил" to listOf("свеча", "flooded"),
            "свечи" to listOf("свеча", "spark plug"),
            "тормоза" to listOf("тормоз", "brakes", "колодки"),
            "колодки" to listOf("brakes", "pads", "тормоз"),
            "подвеска" to listOf("амортизатор", "suspension", "аморт")
        )

        // Частичное совпадение (содержит подстроку)
        private val PARTIAL: List<Pair<String, List<String>>> = listOf(
            "квадр" to listOf("atv", "outlander"),
            "гидр" to listOf("pwc", "sea-doo", "seadoo"),
            "снеж" to listOf("snowmobile", "ski-doo"),
            "ремен" to listOf("cvt", "belt", "вариатор"),
            "вариат" to listOf("cvt", "ремень"),
            "лебед" to listOf("winch"),
            "перегрев" to listOf("overheat", "temperature"),
            "завод" to listOf("start", "пуск", "стартер"),
            "завест" to listOf("start", "пуск", "стартер"),
            "ошиб" to listOf("fault", "code", "dtc"),
            "аккум" to listOf("battery", "акб"),
            "акб" to listOf("battery"),
            "тормоз" to listOf("brakes"),
            "подвес" to listOf("suspension"),
            "диагност" to listOf("diagnos", "dtc", "ошибка"),
            "dps" to listOf("усилитель", "руль"),
            "cvt" to listOf("ремень", "вариатор"),
            "4wd" to listOf("полный привод", "4х4", "привод"),
            "4х4" to listOf("4wd", "полный привод"),
            "линк" to listOf("linq"),
            "аксесс" to listOf("accessory")
        )
    }
}
