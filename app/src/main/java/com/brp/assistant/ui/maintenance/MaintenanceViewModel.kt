package com.brp.assistant.ui.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brp.assistant.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MaintenanceState(
    val purchaseDate: Long? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MaintenanceState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.purchaseDate.collect { date ->
                _state.update { it.copy(purchaseDate = date) }
            }
        }
    }

    fun updatePurchaseDate(date: Long) {
        viewModelScope.launch {
            settingsRepository.setPurchaseDate(date)
        }
    }

    fun syncWithCalendar() {
        // Implementation for calendar intent logic if needed in VM
    }
}
