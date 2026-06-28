package com.brp.assistant.ui.compare

import com.brp.assistant.data.db.enteties.BrpModel
import com.brp.assistant.data.repository.ModelRepository
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F2 — Unit-тесты для CompareViewModel.
 *
 * Покрывает:
 *  1. Лимит 4 техники: 5-е добавление выставляет showLimitToast = true.
 *  2. dismissLimitToast сбрасывает флаг в false.
 *  3. clearSelection обнуляет selectedModels и сбрасывает все флаги.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompareViewModelTest {

    @MockK lateinit var modelRepo: ModelRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        every { modelRepo.observeAll() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── F2-1: 5-е добавление выставляет showLimitToast ────────────────────
    @Test
    fun `toggleModel sets showLimitToast when adding 5th model`() = runTest {
        val vm = CompareViewModel(modelRepo)
        advanceUntilIdle()

        val models = (1..5).map { makeModel(it) }

        // Добавляем 4 — должно пройти без Toast
        models.take(4).forEach { vm.toggleModel(it); advanceUntilIdle() }
        assertFalse(vm.showLimitToast.value)
        assertEquals(4, vm.selectedModels.value.size)

        // Пытаемся добавить 5-ю
        vm.toggleModel(models[4])
        advanceUntilIdle()

        assertTrue(vm.showLimitToast.value)
        assertEquals(4, vm.selectedModels.value.size) // список не изменился
    }

    // ── F2-2: dismissLimitToast сбрасывает флаг ───────────────────────────
    @Test
    fun `dismissLimitToast sets showLimitToast to false`() = runTest {
        val vm = CompareViewModel(modelRepo)
        advanceUntilIdle()

        val models = (1..5).map { makeModel(it) }
        models.take(4).forEach { vm.toggleModel(it); advanceUntilIdle() }
        vm.toggleModel(models[4])
        advanceUntilIdle()
        assertTrue(vm.showLimitToast.value)

        vm.dismissLimitToast()
        advanceUntilIdle()

        assertFalse(vm.showLimitToast.value)
    }

    // ── F2-3: clearSelection обнуляет всё ─────────────────────────────────
    @Test
    fun `clearSelection empties selectedModels and resets toast flag`() = runTest {
        val vm = CompareViewModel(modelRepo)
        advanceUntilIdle()

        val models = (1..3).map { makeModel(it) }
        models.forEach { vm.toggleModel(it); advanceUntilIdle() }
        assertEquals(3, vm.selectedModels.value.size)

        vm.clearSelection()
        advanceUntilIdle()

        assertEquals(0, vm.selectedModels.value.size)
        assertFalse(vm.showLimitToast.value)
    }

    private fun makeModel(id: Int) = BrpModel(
        id = id,
        modelName = "Model $id",
        brandId = 1,
        year = 2024,
        categoryId = 1,
        engineName = null,
        horsepower = null,
        displacementCc = null,
        transmission = null,
        driveType = null,
        isElectric = 0
    )
}
