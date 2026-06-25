package com.brp.assistant.data.db.entities

import androidx.room.*

// ============================================================
// BRP MODEL (каталог техники)
// ============================================================
@Entity(
    tableName = "brp_models",
    indices = [
        Index("brand", name = "idx_models_brand"),
        Index("category", name = "idx_models_category"),
        Index("subcategory", name = "idx_models_subcategory")
    ]
)
data class BrpModel(
    @PrimaryKey val id: String,
    val brand: String,
    val category: String,
    val subcategory: String,
    @ColumnInfo(name = "model_year") val modelYear: Int,
    val modelName: String,
    val platform: String? = null,
    val engineName: String? = null,
    val engineType: String? = null,
    val displacementCc: Double? = null,
    val horsepower: Int? = null,
    val transmission: String? = null,
    val driveType: String? = null,
    val keyFeatures: String? = null,
    val whatNew: String? = null,
    val colors: String? = null,
    val msrpUsd: Double? = null,
    val isElectric: Int,
    val description: String? = null
)

// ============================================================
// ACCESSORY (аксессуары)
// ============================================================
@Entity(
    tableName = "brp_accessories",
    indices = [
        Index("sku"),
        Index("brand", name = "idx_acc_brand"),
        Index("category", name = "idx_acc_category")
    ]
)
data class Accessory(
    @PrimaryKey val id: String,
    val sku: String,
    val brand: String,
    val category: String,
    val subcategory: String? = null,
    val name: String,
    val description: String,
    val compatiblePlatforms: String? = null,
    val compatibleModels: String? = null,
    val msrpUsd: Double? = null,
    val isNew2026: Int,
    val requiresProfessionalInstall: Int,
    val requiresParts: String? = null,
    val tags: String? = null
)

// ============================================================
// KNOWLEDGE CARD (карточки ремонта/диагностики)
// ============================================================
@Entity(tableName = "knowledge_cards")
data class KnowledgeCard(
    @PrimaryKey val id: String,
    val equipmentType: String,
    val brand: String,
    val modelFamily: String? = null,
    val node: String,                        // engine, drivetrain, cooling, electrical, cvt, suspension
    val symptom: String,
    val riskLevel: String,                   // low, medium, high, critical
    val requiresEvacuation: Int,
    val compatibleModels: String? = null,    // JSON
    val causes: String,                      // JSON array
    val steps: String,                       // JSON array
    val canDo: String,                       // JSON
    val mustNotDo: String,                   // JSON
    val stopConditions: String,              // JSON
    val fullText: String
)

// ============================================================
// SEA-DOO FAULT CODES
// ============================================================
@Entity(tableName = "fault_codes")
data class FaultCode(
    @PrimaryKey val code: String,
    val brand: String,                       // sea-doo, can-am, ski-doo
    val description: String,
    val possibleCauses: String? = null,      // JSON
    val solution: String? = null
)

// ============================================================
// ACCESSORY COMPATIBILITY
// ============================================================
@Entity(
    tableName = "accessory_compatibility",
    foreignKeys = [
        ForeignKey(entity = Accessory::class, parentColumns = ["id"], childColumns = ["accessoryId"]),
        ForeignKey(entity = BrpModel::class, parentColumns = ["id"], childColumns = ["modelId"])
    ],
    indices = [Index("accessoryId"), Index("modelId")]
)
data class AccessoryCompatibility(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val accessoryId: String,
    val modelId: String,
    val fitmentNotes: String? = null,
    val requiresProfessionalInstall: Int
)

// ============================================================
// FTS for knowledge search
// ============================================================
@Fts4(
    contentEntity = KnowledgeCard::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61
)
@Entity(tableName = "knowledge_cards_fts")
data class KnowledgeCardFts(
    val fullText: String,
    val symptom: String,
    val id: String,
    val causes: String
)
