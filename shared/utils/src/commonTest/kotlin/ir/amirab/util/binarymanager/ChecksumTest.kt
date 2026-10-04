package ir.amirab.util.binarymanager

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.OptIn
import kotlin.test.ExperimentalKotlinTestApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalKotlinTestApi::class)
class ChecksumTest {

    @Test
    fun `verifyChecksum passes for correct sha256`() {
        val tempFile = Files.createTempFile("checksum-test", ".bin").toFile()
        try {
            val content = "test content for sha256"
            tempFile.writeText(content, StandardCharsets.UTF_8)

            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(content.toByteArray(StandardCharsets.UTF_8))
            val expectedSha256 = digest.digest().joinToString("") { "%02x".format(it) }

            BinaryManager.verifyChecksum(tempFile, expectedSha256, "test")
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `verifyChecksum throws for incorrect sha256`() {
        val tempFile = Files.createTempFile("checksum-test", ".bin").toFile()
        try {
            tempFile.writeText("correct content", StandardCharsets.UTF_8)

            val wrongSha256 = "0".repeat(64)
            var threw = false
            try {
                BinaryManager.verifyChecksum(tempFile, wrongSha256, "test")
            } catch (e: BinaryDownloadException) {
                threw = true
                assertTrue(e.message?.contains("checksum mismatch") == true)
            }
            assertTrue(threw) { "Expected BinaryDownloadException to be thrown" }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `verifyChecksum skips when sha256 is blank`() {
        val tempFile = Files.createTempFile("checksum-test", ".bin").toFile()
        try {
            tempFile.writeText("any content", StandardCharsets.UTF_8)

            BinaryManager.verifyChecksum(tempFile, "", "test")
            BinaryManager.verifyChecksum(tempFile, "   ", "test")
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `verifyChecksum skips when sha256 starts with PENDING`() {
        val tempFile = Files.createTempFile("checksum-test", ".bin").toFile()
        try {
            tempFile.writeText("any content", StandardCharsets.UTF_8)

            BinaryManager.verifyChecksum(tempFile, "PENDING_SHA256", "test")
        } finally {
            tempFile.delete()
        }
    }
}