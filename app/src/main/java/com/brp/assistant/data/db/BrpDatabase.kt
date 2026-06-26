package com.brp.assistant.data.db

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.brp.assistant.data.db.entities.*

/**
 * SCHEMA HISTORY
 * version 1 — initial
 * version 2 — added FaultCode table
 * version 3 — added AccessoryCompatibility, KnowledgeCardFts
 * version 4 — added modelFamily column to KnowledgeCard
 * version 5 — added chat_sessions + chat_messages tables
 * version 6 — added vehicleName column to chat_sessions  ← current
 *
 * MIGRATION POLICY:
 * v1–v4 → fallbackToDestructiveMigrationFrom(1,2,3,4):
 *   данные берутся из assets при каждом запуске, пользовательских записей нет.
 * v4→v5 — CREATE TABLE chat_sessions + chat_messages (нет потери данных).
 * v5→v6 — ALTER TABLE chat_sessions ADD COLUMN vehicleName (нет потери данных).
 *
 * ⚠️  fallbackToDestructiveMigration() УДАЛЁН начиная с v5.
 *    Начиная с версии 5 база содержит пользовательские данные (история чатов),
 *    которые нельзя сносить при обновлении APK. Все последующие изменения схемы
 *    ОБЯЗАНЫ идти через явные Migration-объекты.
 */

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_sessions (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                vehicleId TEXT,
                mode TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id TEXT NOT NULL PRIMARY KEY,
                sessionId TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY (sessionId) REFERENCES chat_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_chat_messages_sessionId ON chat_messages(sessionId)"
        )
    }
}

/**
 * v5 → v6: добавляем денормализованное поле vehicleName.
 * ALTER TABLE с DEFAULT NULL безопасен для существующих строк.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE chat_sessions ADD COLUMN vehicleName TEXT"
        )
    }
}

@Database(
    entities = [
        BrpModel::class,
        Accessory::class,
        KnowledgeCard::class,
        KnowledgeCardFts::class,
        FaultCode::class,
        AccessoryCompatibility::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class BrpDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun accessoryDao(): AccessoryDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun faultCodeDao(): FaultCodeDao
    abstract fun chatSessionDao(): ChatSessionDao
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM brp_models ORDER BY brand, category, modelName")
    suspend fun getAll(): List<BrpModel>

    @Query("SELECT * FROM brp_models WHERE id = :id")
    suspend fun getById(id: String): BrpModel?

    @Query("SELECT * FROM brp_models WHERE brand = :brand ORDER BY modelName")
    suspend fun getByBrand(brand: String): List<BrpModel>

    @Query("SELECT DISTINCT brand FROM brp_models ORDER BY brand")
    suspend fun getBrands(): List<String>

    @Query("SELECT DISTINCT category FROM brp_models WHERE brand = :brand ORDER BY category")
    suspend fun getCategories(brand: String): List<String>

    @Query("SELECT DISTINCT subcategory FROM brp_models WHERE brand = :brand AND category = :category")
    suspend fun getSubcategories(brand: String, category: String): List<String>

    @Query("""
        SELECT * FROM brp_models 
        WHERE (:brand IS NULL OR brand = :brand)
        AND (:category IS NULL OR category = :category)
        AND (:subcategory IS NULL OR subcategory = :subcategory)
        AND (:minHp IS NULL OR horsepower >= :minHp)
        AND (:maxHp IS NULL OR horsepower <= :maxHp)
        AND (:isElectric IS NULL OR isElectric = :isElectric)
        AND (:query IS NULL OR modelName LIKE '%' || :query || '%' OR engineName LIKE '%' || :query || '%')
        ORDER BY modelName
    """)
    suspend fun search(
        brand: String? = null,
        category: String? = null,
        subcategory: String? = null,
        minHp: Int? = null,
        maxHp: Int? = null,
        isElectric: Int? = null,
        query: String? = null
    ): List<BrpModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<BrpModel>)
}

@Dao
interface AccessoryDao {
    @Query("SELECT * FROM brp_accessories ORDER BY category, name")
    suspend fun getAll(): List<Accessory>

    @Query("SELECT * FROM brp_accessories WHERE sku = :sku")
    suspend fun getBySku(sku: String): Accessory?

    @Query("""
        SELECT a.* FROM brp_accessories a
        JOIN accessory_compatibility ac ON a.id = ac.accessoryId
        WHERE ac.modelId = :modelId
    """)
    suspend fun getForModel(modelId: String): List<Accessory>

    @Query("""
        SELECT * FROM brp_accessories 
        WHERE name LIKE '%' || :query || '%' 
        OR description LIKE '%' || :query || '%'
        OR tags LIKE '%' || :query || '%'
        ORDER BY category, name
        LIMIT :limit
    """)
    suspend fun search(query: String, limit: Int = 20): List<Accessory>

    @Query("""
        SELECT * FROM brp_accessories 
        WHERE compatiblePlatforms LIKE '%' || :platform || '%'
        ORDER BY category, name
    """)
    suspend fun getByPlatform(platform: String): List<Accessory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accessories: List<Accessory>)
}

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledge_cards ORDER BY brand, equipmentType")
    suspend fun getAll(): List<KnowledgeCard>

    @Query("SELECT * FROM knowledge_cards WHERE id = :id")
    suspend fun getById(id: String): KnowledgeCard?

    @Query("SELECT * FROM knowledge_cards WHERE brand = :brand")
    suspend fun getByBrand(brand: String): List<KnowledgeCard>

    @Query("SELECT * FROM knowledge_cards WHERE node = :node")
    suspend fun getByNode(node: String): List<KnowledgeCard>

    @Query("""
        SELECT kc.* FROM knowledge_cards kc
        JOIN knowledge_cards_fts fts ON kc.id = fts.id
        WHERE knowledge_cards_fts MATCH :query
        LIMIT :limit
    """)
    suspend fun searchFullText(query: String, limit: Int = 5): List<KnowledgeCard>

    @Query("""
        SELECT * FROM knowledge_cards
        WHERE brand = :brand OR equipmentType = :category
        ORDER BY riskLevel DESC
    """)
    suspend fun getByBrandOrCategory(brand: String, category: String): List<KnowledgeCard>

    @Query("""
        SELECT * FROM knowledge_cards
        WHERE brand = :brand AND equipmentType = :category
        ORDER BY riskLevel DESC
    """)
    suspend fun getByBrandAndCategory(brand: String, category: String): List<KnowledgeCard>

    @Query("""
        SELECT * FROM knowledge_cards
        WHERE brand = :brand
        AND (modelFamily = :modelFamily OR modelFamily IS NULL)
        ORDER BY riskLevel DESC
    """)
    suspend fun getByModelFamily(brand: String, modelFamily: String): List<KnowledgeCard>

    @Query("SELECT * FROM knowledge_cards WHERE equipmentType = :type AND node = :node")
    suspend fun getByFilter(type: String, node: String): List<KnowledgeCard>

    @Query("SELECT DISTINCT node FROM knowledge_cards WHERE equipmentType = :type")
    suspend fun getNodesForType(type: String): List<String>

    @Query("SELECT DISTINCT equipmentType FROM knowledge_cards")
    suspend fun getCategories(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<KnowledgeCard>)
}

@Dao
interface FaultCodeDao {
    @Query("SELECT * FROM fault_codes ORDER BY code")
    suspend fun getAll(): List<FaultCode>

    @Query("SELECT * FROM fault_codes WHERE code = :code")
    suspend fun getByCode(code: String): FaultCode?

    @Query("SELECT * FROM fault_codes WHERE brand = :brand ORDER BY code")
    suspend fun getByBrand(brand: String): List<FaultCode>

    @Query("""
        SELECT * FROM fault_codes
        WHERE code LIKE '%' || :query || '%'
        OR description LIKE '%' || :query || '%'
        ORDER BY code
        LIMIT :limit
    """)
    suspend fun search(query: String, limit: Int = 20): List<FaultCode>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(codes: List<FaultCode>)
}
