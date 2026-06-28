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

/** B3 — Максимальное количество техники для сравнения в одном промпте. */
private const val MAX_COMPARE = 4

data class CompareState(
    val allModels: List<BrpModel> = emptyList(),
    val selectedModels: List<BrpModel> = emptyList(),
    /** B3 — true когда пользователь попытался добавить 5-ю технику */
    val showLimitToast: Boolean = false
)

@HiltViewModel
class CompareViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CompareState())
    val state: StateFlow<CompareState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val models = modelRepository.getAll()
            _state.update { it.copy(allModels = models) }
        }
    }

    /**
     * B3 — при попытке добавить более MAX_COMPARE техник выставляем флаг
     * showLimitToast, который CompareScreen показывает как Toast и сбрасывает
     * через dismissLimitToast().
     */
    fun toggleModel(model: BrpModel) {
        _state.update { s ->
            val current = s.selectedModels
            if (current.contains(model)) {
                s.copy(selectedModels = current - model, showLimitToast = false)
            } else {
                if (current.size >= MAX_COMPARE) {
                    // Лимит достигнут — показываем Toast, список не меняем
                    s.copy(showLimitToast = true)
                } else {
                    s.copy(selectedModels = current + model, showLimitToast = false)
                }
            }
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedModels = emptyList(), showLimitToast = false) }
    }

    /** B3 — вызывается из CompareScreen после показа Toast. */
    fun dismissLimitToast() {
        _state.update { it.copy(showLimitToast = false) }
    }
}
