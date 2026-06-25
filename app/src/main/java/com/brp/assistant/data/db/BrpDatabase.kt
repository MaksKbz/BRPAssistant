package com.brp.assistant.data.db

import androidx.room.*
import com.brp.assistant.data.db.enteties.*

@Database(
    entities = [
        BrpModel::class,
        Accessory::class,
        KnowledgeCard::class,
        KnowledgeCardFts::class,
        FaultCode::class,
        AccessoryCompatibility::class
    ],
    version = 4,
    exportSchema = false
)
abstract class BrpDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun accessoryDao(): AccessoryDao
    abstract fun knowledgeDao(): KnowledgeDao
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

    @Query("SELECT * FROM knowledge_cards WHERE equipmentType = :type AND node = :node")
    suspend fun getByFilter(type: String, node: String): List<KnowledgeCard>

    @Query("SELECT DISTINCT node FROM knowledge_cards WHERE equipmentType = :type")
    suspend fun getNodesForType(type: String): List<String>

    @Query("SELECT DISTINCT equipmentType FROM knowledge_cards")
    suspend fun getCategories(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<KnowledgeCard>)
}
