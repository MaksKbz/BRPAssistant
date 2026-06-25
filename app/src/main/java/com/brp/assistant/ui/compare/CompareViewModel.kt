package com.brp.assistant.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brp.assistant.data.db.enteties.BrpModel
import com.brp.assistant.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompareState(
    val allModels: List<BrpModel> = emptyList(),
    val selectedModels: List<BrpModel> = emptyList()
)

@HiltViewModel
class CompareViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CompareState())
    val state: StateFlow<CompareState> = _state.asStateFlow()

    init {
        loadModels()
    }

    private fun loadModels() {
        viewModelScope.launch {
            val models = modelRepository.getAllModels()
            _state.update { it.copy(allModels = models) }
        }
    }

    fun toggleModel(model: BrpModel) {
        _state.update { s ->
            val current = s.selectedModels.toMutableList()
            if (current.any { it.id == model.id }) {
                current.removeAll { it.id == model.id }
            } else if (current.size < 3) {
                current.add(model)
            }
            s.copy(selectedModels = current)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedModels = emptyList()) }
    }
}
