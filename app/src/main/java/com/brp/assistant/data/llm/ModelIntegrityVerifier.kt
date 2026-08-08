package com.brp.assistant.data.llm

import java.io.File
import java.security.MessageDigest

/** Validates model download origins and optional catalog SHA-256 checksums. */
object ModelIntegrityVerifier {
    private val allowedHosts = setOf(
        "huggingface.co",
        "hf-mirror.com",
        "cdn-lfs.huggingface.co",
        "cdn-lfs-us-1.hf.co"
    )

    fun isAllowedDownloadUrl(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return false
        return allowedHosts.any { host == it || host.endsWith(".$it") }
    }

    fun verifySha256(file: File, expectedSha256: String?): Result<Unit> {
        if (expectedSha256.isNullOrBlank()) return Result.success(Unit)
        val expected = expectedSha256.trim().lowercase()
        if (!Regex("[0-9a-f]{64}").matches(expected)) {
            return Result.failure(IllegalArgumentException("Некорректный SHA-256 в каталоге модели"))
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return if (actual == expected) Result.success(Unit)
        else Result.failure(IllegalStateException("SHA-256 модели не совпадает с ожидаемым"))
    }
}
