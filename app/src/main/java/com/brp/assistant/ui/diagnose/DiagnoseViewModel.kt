package com.brp.assistant.ui.diagnose

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brp.assistant.domain.model.BrpFaultCatalog
import com.brp.assistant.domain.model.EngineResultState
import com.brp.assistant.domain.model.FaultCard
import com.brp.assistant.domain.model.capture
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI-состояние экрана диагностики */
data class DiagnoseUiState(
    val faultCards: List<FaultCard> = emptyList(),
    val filteredCards: List<FaultCard> = emptyList(),
    val engineState: EngineResultState<String> = EngineResultState.Idle,
    val selectedVehicleTag: String? = null
)

@HiltViewModel
class DiagnoseViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "DiagnoseViewModel"
    }

    private val _uiState = MutableStateFlow(DiagnoseUiState())
    val uiState: StateFlow<DiagnoseUiState> = _uiState.asStateFlow()

    init {
        loadFaultCards()
    }

    fun loadFaultCards(vehicleTag: String? = null) {
        viewModelScope.launch {
            try {
                val cards = BrpFaultCatalog.forVehicle(vehicleTag)
                _uiState.update {
                    it.copy(
                        faultCards = cards,
                        filteredCards = cards,
                        selectedVehicleTag = vehicleTag
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "loadFaultCards failed", e)
                _uiState.update {
                    it.copy(engineState = EngineResultState.Error("Ошибка загрузки карточек"))
                }
            }
        }
    }

    /**
     * Запрос диагностики отправляется через ChatScreen;
     * здесь только проверяем доступную RAM перед навигацией в чат.
     * Возвращает null если всё ОК, или OomError если RAM критически мало.
     */
    fun checkRamBeforeInference(): EngineResultState.OomError? {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val availMb = info.availMem / 1_048_576L
            // Меньше 500 МБ — риск OOM
            if (availMb < 500L) {
                val rt = Runtime.getRuntime()
                val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576L
                Log.w(TAG, "Low RAM: avail=${availMb}MB used=${usedMb}MB")
                EngineResultState.OomError(usedMb = usedMb, availMb = availMb)
            } else null
        } catch (e: Throwable) {
            Log.e(TAG, "RAM check failed", e)
            null
        }
    }

    fun clearEngineError() {
        _uiState.update { it.copy(engineState = EngineResultState.Idle) }
    }
}
