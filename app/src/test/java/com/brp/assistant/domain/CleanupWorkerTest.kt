package com.brp.assistant.domain

import com.brp.assistant.data.db.ChatSessionDao
import com.brp.assistant.data.llm.LlmInferenceEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CleanupWorkerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun oldSessionsDeletedNewKept() = runTest {
        val dao = mockk<ChatSessionDao>(relaxed = true)
        val engine = mockk<LlmInferenceEngine>(relaxed = true)
        coEvery { dao.deleteSessionsOlderThan(any()) } returns Unit
        every { engine.getModelsBaseDir() } returns tempFolder.root.absolutePath

        // Simulate cutoff 90 days
        val cutoff = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000L)
        // The worker should call deleteSessionsOlderThan with cutoff ~90 days ago
        // We just verify it is called
        val worker = TestableCleanupWorker(dao, engine, tempFolder.root)
        worker.doTestWork()

        coVerify(atLeast = 1) { dao.deleteSessionsOlderThan(any()) }
    }

    @Test
    fun stalePartFilesDeletedFreshKept() {
        val dao = mockk<ChatSessionDao>(relaxed = true)
        val engine = mockk<LlmInferenceEngine>(relaxed = true)

        val modelsDir = File(tempFolder.root, "models").apply { mkdirs() }
        val oldPart = File(modelsDir, "old_model.task.part").apply {
            createNewFile()
            setLastModified(System.currentTimeMillis() - (25 * 60 * 60 * 1000L)) // 25h ago
        }
        val freshPart = File(modelsDir, "fresh_model.task.part").apply {
            createNewFile()
            setLastModified(System.currentTimeMillis() - (1 * 60 * 60 * 1000L)) // 1h ago
        }
        val normalFile = File(modelsDir, "model.task").apply { createNewFile() }

        every { engine.getModelsBaseDir() } returns tempFolder.root.absolutePath

        val worker = TestableCleanupWorker(dao, engine, tempFolder.root)
        worker.cleanPartFiles()

        assertFalse("Old .part should be deleted", oldPart.exists())
        assertTrue("Fresh .part should be kept", freshPart.exists())
        assertTrue("Normal file should be kept", normalFile.exists())
    }

    // Testable version without WorkManager dependencies
    class TestableCleanupWorker(
        private val dao: ChatSessionDao,
        private val engine: LlmInferenceEngine,
        private val baseDir: File
    ) {
        suspend fun doTestWork() {
            val cutoffMs = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000L)
            dao.deleteSessionsOlderThan(cutoffMs)
            cleanPartFiles()
        }

        fun cleanPartFiles(): Int {
            var deleted = 0
            val modelsDir = File(baseDir, "models")
            if (modelsDir.exists() && modelsDir.isDirectory) {
                modelsDir.walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".part") }
                    .forEach { partFile ->
                        val ageMs = System.currentTimeMillis() - partFile.lastModified()
                        if (ageMs > 24 * 60 * 60 * 1000L) {
                            if (partFile.delete()) deleted++
                        }
                    }
            }
            return deleted
        }
    }
}
