package com.brp.assistant.data.situations

import com.brp.assistant.data.db.enteties.KnowledgeCard

/**
 * Расширенная база офлайн-решений BRP.
 * modelFamily используется для фильтрации: "FUEL", "ELECTRIC" или null (для всех).
 */
object SituationsData {
    val predefinedCards = listOf(
        // === SEA-DOO ===
        KnowledgeCard(
            id = "sd_1", equipmentType = "Sea-Doo", brand = "Sea-Doo", node = "Двигатель",
            symptom = "Затопление (Вода в двигателе)", riskLevel = "critical", requiresEvacuation = 1,
            modelFamily = "FUEL",
            causes = "[\"Переворот против часовой стрелки\", \"Гидроудар\"]",
            steps = "[\"1. НЕ ЗАПУСКАТЬ!\", \"2. На берег\", \"3. Выкрутить свечи\", \"4. Прокачать стартером\"]",
            canDo = "[\"Слив воды\"]", mustNotDo = "[\"Попытки завести\"]", stopConditions = "[\"Эмульсия\"]",
            fullText = "Гидроудар разрушит двигатель за секунду. Обратитесь в сервис: +7 702 214 94 70."
        ),
        KnowledgeCard(
            id = "sd_2", equipmentType = "Sea-Doo", brand = "Sea-Doo", node = "Трансмиссия",
            symptom = "Рвётся ремень", riskLevel = "high", requiresEvacuation = 0,
            modelFamily = "FUEL",
            causes = "[\"Износ\", \"Попадание воды в вариатор (на моделях Switch/других)\", \"Перегрев\"]",
            steps = "[\"1. Остановиться\", \"2. Снять кожух\", \"3. Удалить остатки\", \"4. Установить запасной\"]",
            canDo = "[\"Замена в поле\"]", mustNotDo = "[\"Езда с обрывками\"]", stopConditions = "[\"Удар\"]",
            fullText = "Если ваш Sea-Doo оснащен вариатором, всегда возите запасной ремень."
        ),

        // === CAN-AM ===
        KnowledgeCard(
            id = "ca_belt", equipmentType = "Can-Am", brand = "Can-Am", node = "Вариатор (CVT)",
            symptom = "Рвётся ремень", riskLevel = "high", requiresEvacuation = 0,
            modelFamily = "FUEL",
            causes = "[\"Езда на H в тяжелых условиях\", \"Буксировка на H\"]",
            steps = "[\"1. Снять крышку вариатора\", \"2. Тщательно удалить нитки корда\", \"3. Поставить запасной ремень\"]",
            canDo = "[\"Использовать L в будущем\"]", mustNotDo = "[\"Продолжать на H\"]", stopConditions = "[\"Разрыв\"]",
            fullText = "Ремень на Can-Am рвется от перегрева. Используйте пониженную передачу LOW (L) в грязи."
        ),
        KnowledgeCard(
            id = "ca_4wd", equipmentType = "Can-Am", brand = "Can-Am", node = "Трансмиссия",
            symptom = "Не включается 4WD", riskLevel = "medium", requiresEvacuation = 0,
            causes = "[\"Грязь в кнопке\", \"Обрыв актуатора\", \"Низкий заряд АКБ\"]",
            steps = "[\"1. Промыть блок кнопок\", \"2. Проверить предохранитель 4WD\", \"3. Проверить разъем на редукторе\"]",
            canDo = "[\"Чистка контактов\"]", mustNotDo = "[\"Включать на ходу под газом\"]", stopConditions = "[\"Треск\"]",
            fullText = "Если 4WD не включается, попробуйте проехать 1 метр назад и снова вперед."
        ),

        // === SKI-DOO / LYNX ===
        KnowledgeCard(
            id = "sk_belt", equipmentType = "Ski-Doo", brand = "Ski-Doo", node = "Трансмиссия",
            symptom = "Рвётся ремень", riskLevel = "high", requiresEvacuation = 0,
            modelFamily = "FUEL",
            causes = "[\"Нет обкатки\", \"Перегрев в пухляке\", \"Агрессивный старт\"]",
            steps = "[\"1. Удалить остатки ремня\", \"2. Очистить шкивы\", \"3. Установить новый ремень\", \"4. Отрегулировать натяжку\"]",
            canDo = "[\"Обкатка нового ремня 50км\"]", mustNotDo = "[\"Резкий газ на холодном ремне\"]", stopConditions = "[\"Вибрация\"]",
            fullText = "Новый ремень на снегоходе нужно обкатывать 50 км без резких ускорений."
        ),
        KnowledgeCard(
            id = "sk_start", equipmentType = "Ski-Doo", brand = "Ski-Doo", node = "Запуск",
            symptom = "Залило свечи", riskLevel = "low", requiresEvacuation = 0,
            modelFamily = "FUEL",
            causes = "[\"Мороз\", \"Короткие запуски\"]",
            steps = "[\"1. Газ ДО УПОРА\", \"2. Крутить стартером 5 сек\", \"3. Отпустить газ и заводить\"]",
            canDo = "[\"Режим продувки цилиндров\"]", mustNotDo = "[\"Подгазовывать при запуске\"]", stopConditions = "[\"Запах бензина\"]",
            fullText = "Режим полного газа при запуске отключает форсунки на моторах E-TEC."
        ),

        // === UNIVERSAL ===
        KnowledgeCard(
            id = "uni_batt", equipmentType = "Universal", brand = "BRP", node = "Электрика",
            symptom = "Не заводится / Щелчки стартера", riskLevel = "medium", requiresEvacuation = 0,
            causes = "[\"Слабый АКБ\", \"Окисление клемм\"]",
            steps = "[\"1. Проверить затяжку клемм\", \"2. Зачистить контакты\", \"3. Проверить вольтаж (>12.4V)\"]",
            canDo = "[\"Подзарядка\"]", mustNotDo = "[\"Прикуривать от заведенного авто\"]", stopConditions = "[\"Тишина\"]",
            fullText = "Проверьте массу на раме, если клеммы на АКБ затянуты, но стартер молчит."
        )
    )
}
