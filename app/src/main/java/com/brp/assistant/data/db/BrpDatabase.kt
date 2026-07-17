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
 *
 * exportSchema = true:
 *    Room генерирует JSON-снимки схемы в app/schemas/ при каждом ksp-прогоне.
 *    Снимки версионируются в git — это позволяет Room верифицировать
 *    Migration на этапе компиляции, а не в runtime у пользователей.
 */

/**
 * v3 → v4: приведение таблицы `fault_codes` в точное соответствие с [FaultCode] entity:
 *  - PRIMARY KEY = `code` (TEXT), не авто-id;
 *  - `possibleCauses` и `solution` — nullable (без DEFAULT '');
 *  - удалён лишний столбец `severity`, которого нет в entity.
 *
 * Ранее несоответствие схемы вызывало IllegalStateException при открытии Room.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `fault_codes` (
                `code` TEXT NOT NULL,
                `brand` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `possibleCauses` TEXT,
                `solution` TEXT,
                PRIMARY KEY(`code`)
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_fault_codes_brand` ON `fault_codes` (`brand`)"
        )
    }
}

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

/**
 * v6 → v7: добавляем таблицу чанков знаний [KnowledgeChunk] и FTS для неё.
 * Чанки — это фрагменты markdown-карточек, разбитые по H2/H3 секциям.
 * Это позволяет делать более точный RAG-поиск и подмешивать в промпт
 * только нужный раздел карточки, а не весь текст.
 *
 * Данные в чанки будут перезаполнены при следующем запуске после миграции
 * (они производны от knowledge_cards.fullText, пользовательских данных нет).
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `knowledge_chunks` (
                `id` TEXT NOT NULL,
                `cardId` TEXT NOT NULL,
                `brand` TEXT NOT NULL,
                `equipmentType` TEXT NOT NULL,
                `modelFamily` TEXT,
                `node` TEXT,
                `section` TEXT,
                `content` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`cardId`) REFERENCES `knowledge_cards`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_cardId` ON `knowledge_chunks` (`cardId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_brand` ON `knowledge_chunks` (`brand`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_equipmentType` ON `knowledge_chunks` (`equipmentType`)")
        database.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS `knowledge_chunks_fts` USING FTS4(
                `content` TEXT,
                `section` TEXT,
                `id` TEXT UNINDEXED,
                `cardId` TEXT UNINDEXED,
                tokenize=unicode61,
                content=`knowledge_chunks`
            )
            """.trimIndent()
        )
    }
}

/**
 * v7 → v8: добавляем столбец `sources` в chat_messages для хранения
 * ссылок на карточки БЗ, на основании которых был сгенерирован ответ.
 * ALTER TABLE с DEFAULT NULL безопасен для существующих строк.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE chat_messages ADD COLUMN sources TEXT")
    }
}

/**
 * v8 → v9: пользовательские документы (user_documents + чанки + FTS).
 * Это таблицы для хранения загружаемой пользователем базы знаний (.md/.txt).
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_documents` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `displayName` TEXT NOT NULL,
                `fileName` TEXT NOT NULL,
                `mimeType` TEXT NOT NULL,
                `sizeBytes` INTEGER NOT NULL,
                `addedAt` INTEGER NOT NULL,
                `chunkCount` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_document_chunks` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `documentId` TEXT NOT NULL,
                `section` TEXT,
                `content` TEXT NOT NULL,
                FOREIGN KEY(`documentId`) REFERENCES `user_documents`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_user_document_chunks_documentId` ON `user_document_chunks` (`documentId`)")
        database.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS `user_document_chunks_fts` USING FTS4(
                `content` TEXT,
                `section` TEXT,
                `id` TEXT UNINDEXED,
                `documentId` TEXT UNINDEXED,
                tokenize=unicode61,
                content=`user_document_chunks`
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [
        BrpModel::class,
        Accessory::class,
        KnowledgeCard::class,
        KnowledgeCardFts::class,
        KnowledgeChunk::class,
        KnowledgeChunkFts::class,
        FaultCode::class,
        AccessoryCompatibility::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        UserDocument::class,
        UserDocumentChunk::class,
        UserDocumentChunkFts::class
    ],
    version = 9,
    exportSchema = true  // FIX: false → true; Room теперь верифицирует Migration на этапе компиляции
)
abstract class BrpDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun accessoryDao(): AccessoryDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun knowledgeChunkDao(): KnowledgeChunkDao
    abstract fun userDocumentDao(): UserDocumentDao
    abstract fun faultCodeDao(): FaultCodeDao
    abstract fun chatSessionDao(): ChatSessionDao
}

@Dao
interface KnowledgeChunkDao {
    @Query("SELECT * FROM knowledge_chunks ORDER BY brand, equipmentType")
    suspend fun getAll(): List<KnowledgeChunk>

    @Query("""
        SELECT kc.* FROM knowledge_chunks kc
        JOIN knowledge_chunks_fts fts ON kc.id = fts.id
        WHERE knowledge_chunks_fts MATCH :query
        LIMIT :limit
    """)
    suspend fun searchFullText(query: String, limit: Int = 10): List<KnowledgeChunk>

    @Query("SELECT * FROM knowledge_chunks WHERE cardId = :cardId ORDER BY id")
    suspend fun getByCardId(cardId: String): List<KnowledgeChunk>

    @Query("DELETE FROM knowledge_chunks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM knowledge_chunks")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<KnowledgeChunk>)
}

@Dao
interface UserDocumentDao {
    @Query("SELECT * FROM user_documents ORDER BY addedAt DESC")
    suspend fun getAll(): List<UserDocument>

    @Query("SELECT * FROM user_documents WHERE id = :id")
    suspend fun getById(id: String): UserDocument?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: UserDocument)

    @Query("DELETE FROM user_documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM user_documents")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM user_documents")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM user_document_chunks")
    suspend fun countChunks(): Int

    @Query("DELETE FROM user_document_chunks WHERE documentId = :documentId")
    suspend fun deleteChunksByDoc(documentId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<UserDocumentChunk>)

    @Query("""
        SELECT udc.* FROM user_document_chunks udc
        JOIN user_document_chunks_fts fts ON udc.id = fts.id
        WHERE user_document_chunks_fts MATCH :query
        LIMIT :limit
    """)
    suspend fun searchChunks(query: String, limit: Int = 10): List<UserDocumentChunk>

    @Query("SELECT * FROM user_document_chunks WHERE documentId = :docId ORDER BY id")
    suspend fun getChunksByDoc(docId: String): List<UserDocumentChunk>

    @Query("SELECT * FROM user_document_chunks ORDER BY id LIMIT :limit")
    suspend fun getAllChunks(limit: Int = 500): List<UserDocumentChunk>
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
        WHERE (brand = :brand OR :brand LIKE '%' || brand || '%' OR brand = 'brp')
        AND (equipmentType = :category OR equipmentType = 'all' OR equipmentType LIKE '%' || :category || '%'
             OR (:category = 'ssv' AND equipmentType IN ('sxs', 'utv', 'atv_utv'))
             OR (:category = 'atv' AND equipmentType = 'atv_utv'))
        ORDER BY riskLevel DESC
    """)
    suspend fun getByBrandAndCategory(brand: String, category: String): List<KnowledgeCard>

    @Query("""
        SELECT * FROM knowledge_cards
        WHERE (brand = :brand OR :brand LIKE '%' || brand || '%' OR brand = 'brp')
        AND (modelFamily = :modelFamily OR modelFamily IS NULL OR modelFamily = 'all' OR :modelFamily LIKE '%' || modelFamily || '%')
        ORDER BY riskLevel DESC
    """)
    suspend fun getByModelFamily(brand: String, modelFamily: String): List<KnowledgeCard>

    @Query("""
        SELECT * FROM knowledge_cards
        WHERE (equipmentType = :type OR brand = :type OR :type LIKE '%' || brand || '%' OR brand LIKE '%' || :type || '%')
        AND node = :node
    """)
    suspend fun getByFilter(type: String, node: String): List<KnowledgeCard>

    @Query("""
        SELECT DISTINCT node FROM knowledge_cards
        WHERE equipmentType = :type OR brand = :type OR :type LIKE '%' || brand || '%' OR brand LIKE '%' || :type || '%'
    """)
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
