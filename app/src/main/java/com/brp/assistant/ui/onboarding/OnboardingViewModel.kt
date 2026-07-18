package com.brp.assistant.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brp.assistant.data.repository.SettingsRepository
import com.brp.assistant.domain.DeviceCapabilityProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val deviceCapabilityProvider: DeviceCapabilityProvider
) : ViewModel() {

    val deviceInfo: String = deviceCapabilityProvider.formatDeviceInfo()
    val recommendLocalModel: Boolean = deviceCapabilityProvider.hasEnoughMemoryForLocalLlm()

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted()
        }
    }
}
