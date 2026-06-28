package com.brp.assistant

import com.brp.assistant.domain.model.ChatMessage
import com.brp.assistant.domain.model.MessageRole
import com.brp.assistant.domain.usecase.ConversationSummaryUseCase
import org.junit.Assert.*
import org.junit.Test

class ConversationSummaryUseCaseTest {

    private val useCase = ConversationSummaryUseCase()

    @Test
    fun `empty list returns fallback`() {
        assertEquals("Новый чат", useCase(emptyList()))
    }

    @Test
    fun `short message returned as-is`() {
        val msgs = listOf(ChatMessage(content = "Замена масла", role = MessageRole.USER))
        assertEquals("Замена масла", useCase(msgs))
    }

    @Test
    fun `long message truncated on word boundary with ellipsis`() {
        val long = "Какую вязкость масла использовать для квадроцикла Can-Am Outlander 500 при температуре ниже нуля"
        val msgs = listOf(ChatMessage(content = long, role = MessageRole.USER))
        val result = useCase(msgs)
        assertTrue(result.length <= 63) // 60 chars + "..."
        assertTrue(result.endsWith("..."))
        assertFalse(result.contains("\n"))
    }

    @Test
    fun `assistant message ignored — picks first user message`() {
        val msgs = listOf(
            ChatMessage(content = "Привет!", role = MessageRole.ASSISTANT),
            ChatMessage(content = "Как сменить фильтр", role = MessageRole.USER)
        )
        assertEquals("Как сменить фильтр", useCase(msgs))
    }

    @Test
    fun `extra whitespace and newlines cleaned up`() {
        val msgs = listOf(ChatMessage(content = "Вопрос\n\nс переносами  и   пробелами", role = MessageRole.USER))
        val result = useCase(msgs)
        assertFalse(result.contains("\n"))
        assertFalse(result.contains("  "))
    }
}
