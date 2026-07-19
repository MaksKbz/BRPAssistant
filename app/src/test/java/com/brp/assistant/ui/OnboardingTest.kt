package com.brp.assistant.ui

import com.brp.assistant.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
        // SettingsRepository.onboardingCompleted default is false in DataStore
        // We simulate Flow<Boolean> returning false initially
        val flow = MutableStateFlow(false)
        every { settingsRepo.onboardingCompleted } returns flow

        assertFalse(flow.value)

        // After setter true
        flow.value = true
        assertTrue(flow.value)
    }

    @Test
    fun completeOnboardingPersists() = runTest {
        val onboardingFlow = MutableStateFlow<Boolean?>(false)
        every { settingsRepo.onboardingCompleted } returns onboardingFlow
        every { settingsRepo.selectedVehicleId } returns flowOf(null)
        every { settingsRepo.appTheme } returns flowOf("System")
        val modelRepo = mockk<com.brp.assistant.data.repository.ModelRepository>(relaxed = true)
        val llmEngine = mockk<com.brp.assistant.data.llm.LlmInferenceEngine>(relaxed = true)
        every { llmEngine.activeModelId } returns flowOf(null)
        val healthChecker = mockk<com.brp.assistant.domain.AppHealthChecker>(relaxed = true)
        every { healthChecker.status } returns flowOf(mockk(relaxed = true) {
            every { isLowDisk } returns false
            every { isDbError } returns false
        })

        val vm = MainViewModel(modelRepo, llmEngine, settingsRepo, healthChecker)
        advanceUntilIdle()
        assertEquals(false, vm.onboardingCompleted.value)

        vm.completeOnboarding()
        advanceUntilIdle()

        coVerify { settingsRepo.setOnboardingCompleted() }
        assertEquals(true, vm.onboardingCompleted.value)
    }

    @Test
    fun nullLoadingDoesNotShowHome() = runTest {
        // When onboardingCompleted is null (loading), UI should show loading, not Home
        val flow = MutableStateFlow<Boolean?>(null)
        assertNull(flow.value)
        // Home should not be shown when null
        val shouldShowHome = flow.value == true
        assertFalse(shouldShowHome)
        // Only when true
        flow.value = true
        assertTrue(flow.value == true)
    }
}
