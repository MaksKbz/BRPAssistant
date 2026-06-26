package com.brp.assistant.ui.diagnose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brp.assistant.domain.model.FaultCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DiagnoseUiState {
    object Idle    : DiagnoseUiState()
    object Loading : DiagnoseUiState()
    data class Result(val card: FaultCard) : DiagnoseUiState()
    data class Error(val message: String)  : DiagnoseUiState()
}

@HiltViewModel
class DiagnoseViewModel @Inject constructor(
    // TODO: inject GetFaultCardUseCase when knowledge-base layer is wired
) : ViewModel() {

    private val _uiState = MutableStateFlow<DiagnoseUiState>(DiagnoseUiState.Idle)
    val uiState: StateFlow<DiagnoseUiState> = _uiState.asStateFlow()

    /**
     * Search knowledge base for a fault code.
     * OOM-safe: all heavy work is confined to viewModelScope;
     * errors are caught and surfaced as DiagnoseUiState.Error.
     */
    fun searchFaultCode(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _uiState.value = DiagnoseUiState.Loading
            try {
                // Stub — replace with real use-case call once KB layer is ready:
                // val card = getFaultCard(code.trim().uppercase())
                // _uiState.value = if (card != null)
                //     DiagnoseUiState.Result(card)
                // else
                //     DiagnoseUiState.Error("Код $code не найден в базе знаний")
                _uiState.value = DiagnoseUiState.Error("База знаний в разработке: $code")
            } catch (e: OutOfMemoryError) {
                _uiState.value = DiagnoseUiState.Error("Недостаточно памяти для поиска")
            } catch (e: Exception) {
                _uiState.value = DiagnoseUiState.Error(e.message ?: "Ошибка поиска")
            }
        }
    }

    fun selectSymptomCategory(category: String) {
        // TODO: load symptom flow for given category
    }

    fun reset() {
        _uiState.value = DiagnoseUiState.Idle
    }
}
