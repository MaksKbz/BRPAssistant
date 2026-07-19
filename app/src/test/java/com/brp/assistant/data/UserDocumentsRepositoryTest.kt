package com.brp.assistant.data

import com.brp.assistant.data.db.UserDocumentDao
import com.brp.assistant.data.db.entities.UserDocument
import com.brp.assistant.data.db.entities.UserDocumentChunk
import com.brp.assistant.data.repository.UserDocumentsRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UserDocumentsRepositoryTest {

    @Test
    fun chunkCountEqualsRealSize() = runTest {
        val dao = mockk<UserDocumentDao>(relaxed = true)
        val repo = UserDocumentsRepository(mockk(relaxed = true), dao)

        val text = "## Section 1\nContent here\n\n## Section 2\nMore content"
        coEvery { dao.insertDocumentWithChunks(any(), any()) } just Runs

        val result = repo.addRawText(text, "TestDoc")
        assertTrue(result.isSuccess)
        val doc = result.getOrNull()!!
        // chunkCount should be >0 and equal to actual chunks
        assertTrue(doc.chunkCount > 0)

        coVerify { dao.insertDocumentWithChunks(any(), match { it.size == doc.chunkCount }) }
    }

    @Test
    fun emptyDocumentRejected() = runTest {
        val dao = mockk<UserDocumentDao>(relaxed = true)
        val repo = UserDocumentsRepository(mockk(relaxed = true), dao)

        val result = repo.addRawText("   \n  ", "Empty")
        assertTrue(result.isFailure)
    }

    @Test
    fun transactionalInsertCalled() = runTest {
        val dao = mockk<UserDocumentDao>(relaxed = true)
        val repo = UserDocumentsRepository(mockk(relaxed = true), dao)
        coEvery { dao.insertDocumentWithChunks(any(), any()) } just Runs

        repo.addRawText("Some content", "Doc")
        coVerify(exactly = 1) { dao.insertDocumentWithChunks(any(), any()) }
        // Ensure old separate inserts not used
        coVerify(exactly = 0) { dao.insert(any()) }
        coVerify(exactly = 0) { dao.insertChunks(any()) }
    }
}
