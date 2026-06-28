package com.brp.assistant.ui.chat

import androidx.lifecycle.SavedStateHandle
import com.brp.assistant.data.repository.ChatSessionRepository
import com.brp.assistant.data.repository.SettingsRepository
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
import kotlin.test.assertNull

/**
 * F1 — Unit-тесты для ChatViewModel.
 *
 * Покрывает:
 *  1. SavedStateHandle: активная сессия восстанавливается после OOM-kill.
 *  2. deleteSession: удаление активной сессии сбрасывает activeSessionId.
 *  3. filteredSessions: фильтрация по поисковому запросу.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @MockK lateinit var chatRepo: ChatSessionRepository
    @MockK lateinit var settingsRepo: SettingsRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)

        // Базовые стабы
        every { chatRepo.observeAll() } returns flowOf(emptyList())
        coEvery { settingsRepo.getApiKey(any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── F1-1: SavedStateHandle восстанавливает сессию ─────────────────────
    @Test
    fun `savedState restores active session on init`() = runTest {
        val savedState = SavedStateHandle(
            mapOf(
                "KEY_SESSION_ID" to "session_abc",
                "KEY_MODE" to "general"
            )
        )
        coEvery { chatRepo.getSession("session_abc") } returns mockk {
            every { id } returns "session_abc"
            every { title } returns "Restored session"
            every { vehicleName } returns null
        }
        coEvery { chatRepo.getMessages("session_abc") } returns emptyList()

        val vm = buildVm(savedState)
        advanceUntilIdle()

        assertEquals("session_abc", vm.activeSessionId)
    }

    // ── F1-2: deleteSession сбрасывает activeSessionId ────────────────────
    @Test
    fun `deleteSession clears activeSessionId when deleting active session`() = runTest {
        val savedState = SavedStateHandle(
            mapOf("KEY_SESSION_ID" to "session_xyz")
        )
        coEvery { chatRepo.getSession("session_xyz") } returns mockk {
            every { id } returns "session_xyz"
            every { title } returns "Active"
            every { vehicleName } returns null
        }
        coEvery { chatRepo.getMessages("session_xyz") } returns emptyList()
        coEvery { chatRepo.deleteSession("session_xyz") } just Runs

        val vm = buildVm(savedState)
        advanceUntilIdle()

        vm.deleteSession("session_xyz")
        advanceUntilIdle()

        assertNull(vm.activeSessionId)
    }

    // ── F1-3: filteredSessions фильтрует по запросу ───────────────────────
    @Test
    fun `filteredSessions returns sessions matching search query`() = runTest {
        val session1 = mockk<com.brp.assistant.data.db.entities.ChatSessionEntity> {
            every { title } returns "Обслуживание Can-Am"
            every { vehicleName } returns "Maverick X3"
        }
        val session2 = mockk<com.brp.assistant.data.db.entities.ChatSessionEntity> {
            every { title } returns "Диагностика двигателя"
            every { vehicleName } returns "Spyder F3"
        }
        every { chatRepo.observeAll() } returns flowOf(listOf(session1, session2))

        val vm = buildVm(SavedStateHandle())
        advanceUntilIdle()

        vm.setSearchQuery("Can-Am")
        advanceUntilIdle()

        val filtered = vm.filteredSessions.value
        assertEquals(1, filtered.size)
        assertEquals("Обслуживание Can-Am", filtered.first().title)
    }

    private fun buildVm(savedState: SavedStateHandle) = ChatViewModel(
        chatRepo = chatRepo,
        settingsRepo = settingsRepo,
        savedState = savedState
    )
}
