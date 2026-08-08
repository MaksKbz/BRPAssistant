package com.brp.assistant.data.llm

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelIntegrityVerifierTest {
    @Test
    fun `only approved model hosts are accepted`() {
        assertTrue(ModelIntegrityVerifier.isAllowedDownloadUrl("https://huggingface.co/org/model/file.task"))
        assertTrue(ModelIntegrityVerifier.isAllowedDownloadUrl("https://hf-mirror.com/org/model/file.task"))
        assertFalse(ModelIntegrityVerifier.isAllowedDownloadUrl("https://example.com/file.task"))
    }

    @Test
    fun `sha256 is verified when catalog provides checksum`() {
        val file = File.createTempFile("model", ".bin")
        try {
            file.writeText("BRP")
            val sha = java.security.MessageDigest.getInstance("SHA-256")
                .digest("BRP".toByteArray()).joinToString("") { "%02x".format(it) }
            assertTrue(ModelIntegrityVerifier.verifySha256(file, sha).isSuccess)
            assertFalse(ModelIntegrityVerifier.verifySha256(file, "0".repeat(64)).isSuccess)
        } finally {
            file.delete()
        }
    }
}
