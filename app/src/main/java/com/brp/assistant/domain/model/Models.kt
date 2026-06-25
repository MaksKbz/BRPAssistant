package com.brp.assistant.domain.model

data class ChatMessage(
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    val sources: List<String> = emptyList()
)

enum class MessageRole(val label: String) {
    USER("Клиент"),
    ASSISTANT("Assistant")
}

data class DiagnosisResult(
    val message: String,
    val riskLevel: String,
    val sources: List<String>,
    val requiresEvacuation: Boolean
)

data class AccessoryRecommendation(
    val message: String,
    val accessories: List<String>,
    val totalMsrp: Double?
)

enum class ChatMode {
    DIAGNOSIS,
    ACCESSORY,
    MODEL_SELECTION,
    FREE_CHAT
}

enum class RetrievalMode {
    DIAGNOSIS,
    ACCESSORY,
    BOTH
}
