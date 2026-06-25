package com.brp.assistant.data.accessory

import com.brp.assistant.data.db.enteties.Accessory

object AccessoryData {
    val predefinedAccessories = listOf(
        // Хранение LinQ
        Accessory(
            id = "acc_linq_1", sku = "860202444", brand = "Ski-Doo",
            category = "Хранение LinQ", name = "Сумка LinQ Cargo Box (17L)",
            description = "Герметичная сумка для перевозки вещей. Установка за секунды без инструментов.",
            isNew2026 = 0, requiresProfessionalInstall = 0
        ),
        Accessory(
            id = "acc_linq_2", sku = "715004301", brand = "Can-Am",
            category = "Хранение LinQ", name = "Багажный кофр LinQ (32L)",
            description = "Прочный пластиковый кофр для квадроциклов Outlander и Renegade.",
            isNew2026 = 0, requiresProfessionalInstall = 0
        ),
        
        // Защита
        Accessory(
            id = "acc_prot_1", sku = "715006000", brand = "Can-Am",
            category = "Защита", name = "Защита днища (Алюминий)",
            description = "Комплект полной защиты днища и рычагов для тяжелого бездорожья.",
            isNew2026 = 1, requiresProfessionalInstall = 1
        ),
        Accessory(
            id = "acc_prot_2", sku = "295100845", brand = "Sea-Doo",
            category = "Защита", name = "Бампер для причаливания",
            description = "Защищает корпус гидроцикла при контакте с пирсом.",
            isNew2026 = 0, requiresProfessionalInstall = 0
        ),

        // Свет и Аудио
        Accessory(
            id = "acc_light_1", sku = "715002935", brand = "Can-Am",
            category = "Освещение", name = "Доп. светодиодная балка 10''",
            description = "Мощный направленный свет для ночных поездок.",
            isNew2026 = 0, requiresProfessionalInstall = 1
        ),
        Accessory(
            id = "acc_audio_1", sku = "295100711", brand = "Sea-Doo",
            category = "Аудио", name = "Портативная аудиосистема BRP",
            description = "Bluetooth колонка 50 Вт, полностью водонепроницаемая и плавучая.",
            isNew2026 = 0, requiresProfessionalInstall = 0
        ),

        // Комфорт
        Accessory(
            id = "acc_comf_1", sku = "860202478", brand = "Ski-Doo",
            category = "Комфорт", name = "Сиденье 1+1 с подогревом",
            description = "Пассажирское сиденье для длительных зимних путешествий.",
            isNew2026 = 0, requiresProfessionalInstall = 0
        )
    )
}
