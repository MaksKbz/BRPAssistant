package com.brp.assistant.data.rag

enum class AccessoryIntent {
    GUN, FOOD, CARGO, WINCH, LIGHT, HEATING, ROOF, DOORS, MIRROR, AUDIO, OTHER
}

object AccessoryIntentDetector {

    fun detectIntents(query: String): Set<AccessoryIntent> {
        val q = query.lowercase()
        val intents = mutableSetOf<AccessoryIntent>()
        if (listOf("руж", "оруж", "винтовк", "карабин", "охот", "gun", "стрел").any { q.contains(it) }) {
            intents.add(AccessoryIntent.GUN)
        }
        if (listOf("еда", "еду", "пища", "продукт", "напит", "холодильник", "cooler", "пикник", "термос", "холод").any { q.contains(it) }) {
            intents.add(AccessoryIntent.FOOD)
        }
        if (listOf("кофр", "багаж", "ящик", "перевоз", "груз", "вещ", "класть", "сумк", "багажник", "cargo", "box", "trunk", "storage", "linq").any { q.contains(it) }) {
            intents.add(AccessoryIntent.CARGO)
        }
        if (listOf("лебедк", "winch", "трос").any { q.contains(it) }) {
            intents.add(AccessoryIntent.WINCH)
        }
        if (listOf("свет", "фар", "led", "люстр", "фара", "light").any { q.contains(it) }) {
            intents.add(AccessoryIntent.LIGHT)
        }
        if (listOf("подогрев", "heated", "grips").any { q.contains(it) }) {
            intents.add(AccessoryIntent.HEATING)
        }
        if (listOf("крыш", "козырёк", "козырек", "тент", "roof").any { q.contains(it) }) {
            intents.add(AccessoryIntent.ROOF)
        }
        if (listOf("двер", "кабин", "doors", "cab").any { q.contains(it) }) {
            intents.add(AccessoryIntent.DOORS)
        }
        if (listOf("зеркал", "mirror").any { q.contains(it) }) {
            intents.add(AccessoryIntent.MIRROR)
        }
        if (listOf("музык", "звук", "аудио", "динамик", "колонк", "audio", "soundbar", "bluetooth").any { q.contains(it) }) {
            intents.add(AccessoryIntent.AUDIO)
        }
        if (intents.isEmpty()) intents.add(AccessoryIntent.OTHER)
        return intents
    }

    fun isFoodQuery(query: String): Boolean = detectIntents(query).contains(AccessoryIntent.FOOD)
    fun isGunQuery(query: String): Boolean = detectIntents(query).contains(AccessoryIntent.GUN)
    fun isCargoQuery(query: String): Boolean = detectIntents(query).contains(AccessoryIntent.CARGO)

    // For regression: ensure "редуктор" is NOT detected as FOOD
    fun isFalseFoodTrigger(query: String): Boolean {
        val q = query.lowercase()
        // old buggy trigger "ед" would match "редуктор"
        // new implementation must NOT
        return q.contains("редуктор") && isFoodQuery(query)
    }
}
