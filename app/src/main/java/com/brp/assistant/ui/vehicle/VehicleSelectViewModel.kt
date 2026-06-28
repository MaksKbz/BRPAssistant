package com.brp.assistant.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brp.assistant.data.db.entities.BrpModel
import com.brp.assistant.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VehicleSelectState(
    val brands: List<String> = emptyList(),
    val categories: Map<String, List<String>> = emptyMap(),
    val subcategories: Map<String, List<String>> = emptyMap(),
    val models: List<BrpModel> = emptyList(),
    val selectedBrand: String? = null,
    val selectedCategory: String? = null,
    val selectedSubcategory: String? = null,
    val selectedModel: BrpModel? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class VehicleSelectViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val settingsRepository: com.brp.assistant.data.repository.SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(VehicleSelectState())
    val state: StateFlow<VehicleSelectState> = _state.asStateFlow()

    init {
        loadBrands()
        viewModelScope.launch {
            settingsRepository.selectedVehicleId.collect { id ->
                id?.let {
                    val model = modelRepository.getById(it)
                    _state.update { s -> s.copy(selectedModel = model) }
                }
            }
        }
    }

    private fun loadBrands() {
        viewModelScope.launch {
            val brands = modelRepository.getBrands()
            _state.update { it.copy(brands = brands) }
        }
    }

    fun selectBrand(brand: String) {
        _state.update { it.copy(selectedBrand = brand, selectedCategory = null, selectedSubcategory = null, selectedModel = null) }
        viewModelScope.launch {
            val cats = modelRepository.getCategories(brand)
            _state.update { it.copy(categories = it.categories + (brand to cats)) }
            updateModels()
        }
    }

    fun selectCategory(category: String) {
        _state.update { it.copy(selectedCategory = category, selectedSubcategory = null, selectedModel = null) }
        val brand = _state.value.selectedBrand ?: return
        viewModelScope.launch {
            val subs = modelRepository.getSubcategories(brand, category)
            val key = "$brand/$category"
            _state.update { it.copy(subcategories = it.subcategories + (key to subs)) }
            updateModels()
        }
    }

    fun selectSubcategory(subcategory: String) {
        _state.update { it.copy(selectedSubcategory = subcategory, selectedModel = null) }
        updateModels()
    }

    fun selectModel(model: BrpModel) {
        _state.update { it.copy(selectedModel = model) }
        viewModelScope.launch {
            settingsRepository.setSelectedVehicleId(model.id)
        }
    }

    private fun updateModels() {
        val s = _state.value
        viewModelScope.launch {
            val models = modelRepository.search(s.selectedBrand, s.selectedCategory, s.selectedSubcategory, null)
            _state.update { it.copy(models = models) }
        }
    }
}
