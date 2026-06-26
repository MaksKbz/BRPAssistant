package com.brp.assistant.domain.model

/**
 * Domain model for a BRP fault code card.
 * Used by DiagnoseScreen, FaultCardScreen and the knowledge-base RAG layer.
 */
data class FaultCard(
    val code: String,            // e.g. "P0562"
    val title: String,           // e.g. "Battery Voltage Too Low"
    val brand: String,           // "can-am" | "ski-doo" | "sea-doo" | "lynx"
    val modelFamily: String,     // "defender" | "maverick" | "outlander" | "renegade" | ...
    val severity: Severity,
    val symptoms: List<String>,
    val likelyCauses: List<String>,
    val checkSteps: List<String>,
    val toolsRequired: List<String>,
    val canContinue: Boolean,
    val dealerRequired: Boolean,
    val buds2Required: Boolean,
    val relatedFaults: List<String> = emptyList(),
    val notes: String = "",
)

enum class Severity { CRITICAL, WARNING, INFO }
