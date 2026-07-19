package com.brp.assistant.data.rag

enum class AccessoryIntent {
    GUN, FOOD, CARGO, WINCH, LIGHT, HEATING, ROOF, DOORS, MIRROR, AUDIO, OTHER
}

object AccessoryIntentDetector {

    private fun tokenize(query: String): List<String> {
        return query.lowercase()
            .split(Regex("[^a-zа-яё0-9]+"))
            .filter { it.length >= 2 }
    }

    private fun anyWordStartsWith(tokens: List<String>, prefixes: List<String>): Boolean {
        return tokens.any { token ->
            prefixes.any { pref -> token.startsWith(pref) }
        }
    }

    private fun anyWordEquals(tokens: List<String>, words: List<String>): Boolean {
        return tokens.any { it in words }
    }

    fun detectIntents(query: String): Set<AccessoryIntent> {
        val tokens = tokenize(query)
        val q = query.lowercase()
        val intents = mutableSetOf<AccessoryIntent>()

        // GUN: руж, оруж, винтовк, карабин, охот, gun, стрел
        if (anyWordStartsWith(tokens, listOf("руж", "оруж", "винтовк", "карабин", "охот", "gun", "стрел")) ||
            anyWordEquals(tokens, listOf("gun", "rifle"))) {
            intents.add(AccessoryIntent.GUN)
        }

        // FOOD: еда, еду, пища, продукт, напит, холодильник, cooler, пикник, термос, холод
        // Используем startsWith чтобы избежать "передача" -> "еда" и "редуктор" -> "еду"
        if (anyWordStartsWith(tokens, listOf("еда", "еду", "еды", "едой", "пища", "пищ", "продукт", "напит", "холодильник", "холод", "термос", "cooler", "пикник")) ) {
            intents.add(AccessoryIntent.FOOD)
        }

        // CARGO: кофр, багаж, ящик, перевоз, груз, вещ, класть, сумк, багажник, cargo, box, trunk, storage, linq
        if (anyWordStartsWith(tokens, listOf("кофр", "багаж", "ящик", "перевоз", "груз", "вещ", "класть", "сумк", "багажник", "cargo", "trunk", "storage", "linq")) ||
            anyWordEquals(tokens, listOf("box"))) {
            // box как отдельное слово, чтобы не матчить "toolbox" случайно, но storage/box часто отдельно
            intents.add(AccessoryIntent.CARGO)
        }
        // Отдельно box как startsWith тоже, но проверяем токен "box" точно
        if (tokens.any { it == "box" || it.startsWith("box") }) {
            // box часто встречается как "toolbox" - но в контексте BRP это cargo, оставим
            if (!intents.contains(AccessoryIntent.FOOD)) {
                // не добавляем cargo если уже food? на самом деле можно оба
            }
        }

        if (anyWordStartsWith(tokens, listOf("лебедк", "winch", "трос"))) {
            intents.add(AccessoryIntent.WINCH)
        }
        if (anyWordStartsWith(tokens, listOf("свет", "фар", "led", "люстр", "фара", "light"))) {
            intents.add(AccessoryIntent.LIGHT)
        }
        if (anyWordStartsWith(tokens, listOf("подогрев", "heated", "grips"))) {
            intents.add(AccessoryIntent.HEATING)
        }
        if (anyWordStartsWith(tokens, listOf("крыш", "козырёк", "козырек", "тент", "roof"))) {
            intents.add(AccessoryIntent.ROOF)
        }
        if (anyWordStartsWith(tokens, listOf("двер", "кабин", "doors", "cab"))) {
            intents.add(AccessoryIntent.DOORS)
        }
        if (anyWordStartsWith(tokens, listOf("зеркал", "mirror"))) {
            intents.add(AccessoryIntent.MIRROR)
        }
        if (anyWordStartsWith(tokens, listOf("музык", "звук", "аудио", "динамик", "колонк", "audio", "soundbar", "bluetooth"))) {
            intents.add(AccessoryIntent.AUDIO)
        }
        if (intents.isEmpty()) intents.add(AccessoryIntent.OTHER)
        return intents
    }

    fun isFoodQuery(query: String): Boolean = detectIntents(query).contains(AccessoryIntent.FOOD)
    fun isGunQuery(query: String): Boolean = detectIntents(query).contains(AccessoryIntent.GUN)
    fun isCargoQuery(query: String): Boolean = detectIntents(query).contains(AccessoryIntent.CARGO)

    fun isFalseFoodTrigger(query: String): Boolean {
        val q = query.lowercase()
        return q.contains("редуктор") && isFoodQuery(query)
    }
}
