package com.brp.assistant.ui

import com.brp.assistant.data.repository.SettingsRepository
import com.brp.assistant.domain.AppHealthChecker
import com.brp.assistant.domain.DeviceCapabilityProvider
import com.brp.assistant.domain.HealthStatus
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepo: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsRepo = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onboardingDefaultFalse() = runTest {
        val flow = MutableStateFlow(false)
        every { settingsRepo.onboardingCompleted } returns flow

        assertFalse(flow.value)

        flow.value = true
        assertTrue(flow.value)
    }

    @Test
    fun completeOnboardingPersists() = runTest {
        val onboardingFlow = MutableStateFlow<Boolean?>(false)
        every { settingsRepo.onboardingCompleted } returns onboardingFlow
        every { settingsRepo.selectedVehicleId } returns flowOf<String?>(null)
        every { settingsRepo.appTheme } returns flowOf("System")
        every { settingsRepo.selectedVehicleName } returns flowOf<String?>(null)
        val modelRepo = mockk<com.brp.assistant.data.repository.ModelRepository>(relaxed = true)
        val llmEngine = mockk<com.brp.assistant.data.llm.LlmInferenceEngine>(relaxed = true)
        every { llmEngine.activeModelId } returns flowOf(null)
        val healthChecker = mockk<AppHealthChecker>(relaxed = true)
        every { healthChecker.status } returns MutableStateFlow(HealthStatus(diskFreeGb = 10.0, dbOk = true))
        val deviceProvider = mockk<DeviceCapabilityProvider>(relaxed = true)
        every { deviceProvider.formatDeviceInfo() } returns "Test Device"
        every { deviceProvider.checkMemory() } returns DeviceCapabilityProvider.MemoryStatus(200, 500, 4000, false)

        val vm = MainViewModel(modelRepo, llmEngine, settingsRepo, healthChecker, deviceProvider)
        advanceUntilIdle()
        assertEquals(false, vm.onboardingCompleted.value)

        vm.completeOnboarding()
        advanceUntilIdle()

        coVerify { settingsRepo.setOnboardingCompleted() }
        assertEquals(true, vm.onboardingCompleted.value)
    }

    @Test
    fun nullLoadingDoesNotShowHome() = runTest {
        val flow = MutableStateFlow<Boolean?>(null)
        assertNull(flow.value)
        val shouldShowHome = flow.value == true
        assertFalse(shouldShowHome)
        flow.value = true
        assertTrue(flow.value == true)
    }
}
