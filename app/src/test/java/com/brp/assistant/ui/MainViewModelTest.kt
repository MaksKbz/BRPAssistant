package com.brp.assistant.ui

import com.brp.assistant.data.db.entities.BrpModel
import com.brp.assistant.data.llm.LlmInferenceEngine
import com.brp.assistant.data.repository.ModelRepository
import com.brp.assistant.data.repository.SettingsRepository
import com.brp.assistant.domain.AppHealthChecker
import com.brp.assistant.domain.DeviceCapabilityProvider
import com.brp.assistant.domain.HealthStatus
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
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var modelRepo: ModelRepository
    private lateinit var llmEngine: LlmInferenceEngine
    private lateinit var healthChecker: AppHealthChecker
    private lateinit var deviceCapabilityProvider: DeviceCapabilityProvider
    private lateinit var viewModel: MainViewModel

    private val vehicleIdFlow = MutableStateFlow<String?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        settingsRepo = mockk(relaxed = true)
        modelRepo = mockk(relaxed = true)
        llmEngine = mockk(relaxed = true)
        healthChecker = mockk(relaxed = true)
        deviceCapabilityProvider = mockk(relaxed = true)

        every { settingsRepo.selectedVehicleId } returns vehicleIdFlow
        every { settingsRepo.appTheme } returns flowOf("System")
        every { settingsRepo.onboardingCompleted } returns flowOf(true)
        every { settingsRepo.selectedVehicleName } returns flowOf<String?>(null)
        every { llmEngine.activeModelId } returns flowOf(null)
        every { healthChecker.status } returns MutableStateFlow(HealthStatus(diskFreeGb = 10.0, dbOk = true))
        every { deviceCapabilityProvider.formatDeviceInfo() } returns "Test Device"
        every { deviceCapabilityProvider.checkMemory() } returns DeviceCapabilityProvider.MemoryStatus(200, 500, 4000, false)

        viewModel = MainViewModel(
            modelRepository = modelRepo,
            llmEngine = llmEngine,
            settingsRepository = settingsRepo,
            healthChecker = healthChecker,
            deviceCapabilityProvider = deviceCapabilityProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectVehicle(BrpModel) persists id to SettingsRepository`() = runTest {
        val model = BrpModel(id = "can-am-1", brand = "Can-Am", modelName = "Maverick R", category = "SxS", subcategory = null, modelYear = 2024, isElectric = 0)

        viewModel.selectVehicle(model)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepo.setSelectedVehicleId("can-am-1") }
    }

    @Test
    fun `selectVehicle(id, name) persists id to SettingsRepository`() = runTest {
        coEvery { modelRepo.getById("sea-doo-42") } returns mockk(relaxed = true)

        viewModel.selectVehicle("sea-doo-42", "Sea-Doo RXP")
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepo.setSelectedVehicleId("sea-doo-42") }
    }

    @Test
    fun `selectVehicle(id, name) loads vehicle from ModelRepository`() = runTest {
        val expected = mockk<BrpModel>(relaxed = true)
        coEvery { modelRepo.getById("ski-doo-7") } returns expected

        viewModel.selectVehicle("ski-doo-7", "Ski-Doo Summit")
        advanceUntilIdle()

        assertEquals(expected, viewModel.selectedVehicle.value)
    }

    @Test
    fun `selectVehicle(id, name) does not crash when ModelRepository throws`() = runTest {
        coEvery { modelRepo.getById(any()) } throws RuntimeException("DB error")

        viewModel.selectVehicle("bad-id", "Unknown")
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepo.setSelectedVehicleId("bad-id") }
    }

    @Test
    fun `healthWarning is null when AppHealthChecker reports no issues`() = runTest {
        advanceUntilIdle()
        assertNull(viewModel.healthWarning.value)
    }

    @Test
    fun `setAppTheme persists theme to SettingsRepository`() = runTest {
        viewModel.setAppTheme("Dark")
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepo.setAppTheme("Dark") }
    }
}
