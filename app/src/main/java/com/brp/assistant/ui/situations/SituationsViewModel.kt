package com.brp.assistant.ui.situations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brp.assistant.data.db.BrpDatabase
import com.brp.assistant.data.db.entities.KnowledgeCard
import com.brp.assistant.data.situations.SituationsData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SituationsState(
    val categories: List<String> = emptyList(),
    val nodes: List<String> = emptyList(),
    val cards: List<KnowledgeCard> = emptyList(),
    val selectedCategory: String? = null,
    val selectedNode: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class SituationsViewModel @Inject constructor(
    private val db: BrpDatabase
) : ViewModel() {

    private val _state = MutableStateFlow(SituationsState())
    val state = _state.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                // Fetch categories from DB or fallback to empty
                val dbCats = try {
                    db.knowledgeDao().getCategories()
                } catch (e: Exception) {
                    Log.e("SituationsVM", "DB error getting categories", e)
                    emptyList<String>()
                }
                
                val hardcodedCats = SituationsData.predefinedCards.map { it.equipmentType }.distinct()
                val cats = (dbCats + hardcodedCats).distinct().sorted()
                
                _state.update { it.copy(categories = cats, isLoading = false) }
            } catch (e: Exception) {
                Log.e("SituationsVM", "Unexpected error in loadInitialData", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectCategory(category: String) {
        if (category == _state.value.selectedCategory) return
        
        viewModelScope.launch {
            try {
                val dbNodes = try {
                    db.knowledgeDao().getNodesForType(category)
                } catch (e: Exception) {
                    Log.e("SituationsVM", "DB error getting nodes", e)
                    emptyList<String>()
                }
                
                val hardcodedNodes = SituationsData.predefinedCards
                    .filter { it.equipmentType.equals(category, ignoreCase = true) || category.contains(it.equipmentType, ignoreCase = true) || it.equipmentType.contains(category, ignoreCase = true) }
                    .map { it.node }
                    .distinct()
                
                val nodes = (dbNodes + hardcodedNodes).distinct().sorted()
                _state.update { it.copy(selectedCategory = category, nodes = nodes, selectedNode = null, cards = emptyList()) }
            } catch (e: Exception) {
                Log.e("SituationsVM", "Unexpected error in selectCategory", e)
            }
        }
    }

    fun selectNode(node: String, isElectric: Boolean = false) {
        viewModelScope.launch {
            try {
                val category = _state.value.selectedCategory ?: return@launch
                val dbCards = try { db.knowledgeDao().getByFilter(category, node) } catch (e: Exception) { emptyList() }
                
                // Фильтрация зашитых карт:
                // Если техника электро — показываем ELECTRIC или null
                // Если бензин — показываем FUEL или null
                val filterTag = if (isElectric) "ELECTRIC" else "FUEL"
                
                val hardcodedCards = SituationsData.predefinedCards.filter { 
                    (it.equipmentType.equals(category, ignoreCase = true) || category.contains(it.equipmentType, ignoreCase = true) || it.equipmentType.contains(category, ignoreCase = true)) && 
                    it.node == node &&
                    (it.modelFamily == null || it.modelFamily == filterTag)
                }
                
                val cards = (dbCards + hardcodedCards).distinctBy { it.id }
                _state.update { it.copy(selectedNode = node, cards = cards) }
            } catch (e: Exception) {
                Log.e("SituationsVM", "Error selecting node", e)
            }
        }
    }
}
