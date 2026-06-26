package com.brp.assistant.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brp.assistant.data.db.enteties.BrpModel
import com.brp.assistant.data.llm.LlmInferenceEngine
import com.brp.assistant.data.llm.PublicOfflineModelCatalog
import com.brp.assistant.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val llmEngine: LlmInferenceEngine,
    private val settingsRepository: com.brp.assistant.data.repository.SettingsRepository
) : ViewModel() {

    private val _selectedVehicleId = MutableStateFlow<String?>(null)
    val selectedVehicleId: StateFlow<String?> = _selectedVehicleId.asStateFlow()

    private val _selectedVehicleName = MutableStateFlow<String?>(null)
    val selectedVehicleName: StateFlow<String?> = _selectedVehicleName.asStateFlow()

    private val _selectedVehicle = MutableStateFlow<BrpModel?>(null)
    val selectedVehicle: StateFlow<BrpModel?> = _selectedVehicle.asStateFlow()

    private val _activeModelId = MutableStateFlow<String?>(null)
    val activeModelId: StateFlow<String?> = _activeModelId.asStateFlow()

    private val _activeModelName = MutableStateFlow<String?>(null)
    val activeModelName: StateFlow<String?> = _activeModelName.asStateFlow()

    private val _appTheme = MutableStateFlow("System")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.appTheme.collect { theme ->
                _appTheme.value = theme
            }
        }
        viewModelScope.launch {
            settingsRepository.selectedVehicleId.collect { id ->
                _selectedVehicleId.value = id
                id?.let {
                    val model = modelRepository.getById(it)
                    _selectedVehicle.value = model
                    _selectedVehicleName.value = model?.let { "${it.brand.uppercase()} ${it.modelName}" }
                } ?: run {
                    _selectedVehicle.value = null
                    _selectedVehicleName.value = null
                }
            }
        }

        viewModelScope.launch {
            llmEngine.activeModelId.collect { id ->
                Log.d("MainViewModel", "Active model changed to: $id")
                _activeModelId.value = id
                _activeModelName.value = id?.let {
                    PublicOfflineModelCatalog.getById(it)?.title ?: "Загруженная модель"
                }
            }
        }
    }

    fun selectVehicle(model: BrpModel) {
        viewModelScope.launch {
            settingsRepository.setSelectedVehicleId(model.id)
            // StateFlow обновляются автоматически через collect-поток в init{}
        }
    }

    /**
     * FIX #2: добавлен settingsRepository.setSelectedVehicleId(id).
     *
     * Раньше эта перегрузка записывала id/name напрямую в StateFlow,
     * обходя DataStore. После перезапуска приложения DataStore считывался
     * с null, сбрасывая выбранную технику.
     *
     * Теперь: settingsRepository.setSelectedVehicleId(id) вызывается явно,
     * а StateFlow обновляются автоматически через collect-поток в init{},
     * как это работает в первой перегрузке (BrpModel).
     * Прямая запись больше не нужна.
     */
    fun selectVehicle(id: String, name: String) {
        viewModelScope.launch {
            // FIX #2: добавлен вызов settingsRepository, чтобы выбортехники
            // переживал перезапуск. StateFlow обновится автоматически через collect
            settingsRepository.setSelectedVehicleId(id)
            try {
                _selectedVehicle.value = modelRepository.getById(id)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading vehicle by id=$id", e)
            }
        }
    }

    fun setAppTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.setAppTheme(theme)
        }
    }
}
