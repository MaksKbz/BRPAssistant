package com.brp.assistant.data.repository

import android.util.Log
import com.brp.assistant.data.db.AccessoryDao
import com.brp.assistant.data.db.KnowledgeDao
import com.brp.assistant.data.db.ModelDao
import com.brp.assistant.data.db.entities.Accessory
import com.brp.assistant.data.db.entities.BrpModel
import com.brp.assistant.data.db.entities.KnowledgeCard
import com.brp.assistant.data.rag.*
import com.brp.assistant.domain.model.RetrievalMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(private val modelDao: ModelDao) {
    suspend fun getAllModels() = safeQuery { modelDao.getAll() } ?: emptyList()
    suspend fun getBrands() = safeQuery { modelDao.getBrands() } ?: emptyList()
    suspend fun getCategories(brand: String) = safeQuery { modelDao.getCategories(brand) } ?: emptyList()
    suspend fun getSubcategories(brand: String, category: String) = safeQuery { modelDao.getSubcategories(brand, category) } ?: emptyList()
    suspend fun search(brand: String?, category: String?, subcategory: String?, query: String?) =
        safeQuery { modelDao.search(brand, category, subcategory, query = query) } ?: emptyList()
    suspend fun getById(id: String) = safeQuery { modelDao.getById(id) }

    private suspend fun <T> safeQuery(block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: Exception) {
            Log.e("ModelRepository", "Database error", e)
            null
        }
    }
}

@Singleton
class KnowledgeRepository @Inject constructor(
    private val knowledgeDao: KnowledgeDao,
    private val retriever: UnifiedRetriever
) {
    suspend fun search(query: String, modelId: String?, topK: Int = 5): List<ScoredCard> {
        return try {
            val result = retriever.retrieve(query, RetrievalMode.DIAGNOSIS, modelId, topK)
            result.cards
        } catch (e: Exception) {
            Log.e("KnowledgeRepository", "Search error", e)
            emptyList()
        }
    }
    suspend fun getAll() = try { knowledgeDao.getAll() } catch (e: Exception) { emptyList() }
}

@Singleton
class AccessoryRepository @Inject constructor(
    private val accessoryDao: AccessoryDao,
    private val retriever: UnifiedRetriever
) {
    suspend fun search(query: String, modelId: String?, topK: Int = 5): List<ScoredAccessory> {
        return try {
            val result = retriever.retrieve(query, RetrievalMode.ACCESSORY, modelId, topK)
            result.accessories
        } catch (e: Exception) {
            Log.e("AccessoryRepository", "Search error", e)
            emptyList()
        }
    }
    suspend fun getForModel(modelId: String) = try { accessoryDao.getForModel(modelId) } catch (e: Exception) { emptyList() }
    suspend fun getAll() = try { accessoryDao.getAll() } catch (e: Exception) { emptyList() }
}
