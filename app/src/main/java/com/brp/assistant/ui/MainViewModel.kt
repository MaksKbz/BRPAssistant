package com.brp.assistant.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brp.assistant.data.db.entities.BrpModel
import com.brp.assistant.data.llm.LlmInferenceEngine
import com.brp.assistant.data.llm.PublicOfflineModelCatalog
import com.brp.assistant.data.repository.ModelRepository
import com.brp.assistant.domain.AppHealthChecker
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
    private val settingsRepository: com.brp.assistant.data.repository.SettingsRepository,
    private val healthChecker: AppHealthChecker
) : ViewModel() {

    private val _selectedVehicleId   = MutableStateFlow<String?>(null)
    val selectedVehicleId: StateFlow<String?> = _selectedVehicleId.asStateFlow()

    private val _selectedVehicleName = MutableStateFlow<String?>(null)
    val selectedVehicleName: StateFlow<String?> = _selectedVehicleName.asStateFlow()

    private val _selectedVehicle     = MutableStateFlow<BrpModel?>(null)
    val selectedVehicle: StateFlow<BrpModel?> = _selectedVehicle.asStateFlow()

    private val _activeModelId       = MutableStateFlow<String?>(null)
    val activeModelId: StateFlow<String?> = _activeModelId.asStateFlow()

    private val _activeModelName     = MutableStateFlow<String?>(null)
    val activeModelName: StateFlow<String?> = _activeModelName.asStateFlow()

    private val _appTheme            = MutableStateFlow("System")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    private val _healthWarning       = MutableStateFlow<String?>(null)
    val healthWarning: StateFlow<String?> = _healthWarning.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.appTheme.collect { theme ->
                _appTheme.value = theme
            }
        }

        // FIX: восстанавливаем selectedVehicleName мгновенно из DataStore,
        // затем проверяем и обновляем по id.
        viewModelScope.launch {
            settingsRepository.selectedVehicleName.collect { savedName ->
                if (_selectedVehicleName.value == null) {
                    _selectedVehicleName.value = savedName
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.selectedVehicleId.collect { id ->
                _selectedVehicleId.value = id
                if (id != null) {
                    val model = modelRepository.getById(id)
                    _selectedVehicle.value = model
                    // Имя из БД — каноничный источник правды
                    val name = model?.let { "${it.brand.uppercase()} ${it.modelName}" }
                    _selectedVehicleName.value = name
                } else {
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
        viewModelScope.launch {
            healthChecker.status.collect { status ->
                _healthWarning.value = when {
                    status.isLowDisk -> "⚠️ Мало места на диске. Освободите память для стабильной работы."
                    status.isDbError -> "❌ Ошибка БД. Перезагрузите приложение."
                    else             -> null
                }
            }
        }
    }

    fun selectVehicle(model: BrpModel) {
        viewModelScope.launch {
            val name = "${model.brand.uppercase()} ${model.modelName}"
            // Сохраняем id и name атомарно— при холодном старте имя восстанавливается мгновенно
            settingsRepository.setSelectedVehicleId(model.id)
            settingsRepository.setSelectedVehicleName(name)
            _selectedVehicle.value = model
            _selectedVehicleName.value = name
        }
    }

    fun selectVehicle(id: String, name: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedVehicleId(id)
            settingsRepository.setSelectedVehicleName(name)
            _selectedVehicleName.value = name
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
