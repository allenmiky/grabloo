package ir.amirab.util.logger

import co.touchlab.kermit.Severity
import java.io.File
import java.nio.file.Files
import kotlin.OptIn
import kotlin.test.ExperimentalKotlinTestApi
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The log file used to stay at 0 bytes for an entire app run.
 *
 * The writer coroutine sits on a daemon thread, so the sink is never closed when the app exits,
 * and an okio [okio.BufferedSink] holds up to a segment (~8KB) in memory. With no flush per
 * message, a normal run wrote nothing at all and the `finally` flush never ran, which made the
 * app impossible to debug.
 */
@OptIn(ExperimentalKotlinTestApi::class)
class FileLogWriterTest {

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    @Test
    fun `a single small message reaches the log file`() {
        val dir = Files.createTempDirectory("file-log-writer").toFile()
        try {
            val logFile = File(dir, "AppLog.log")
            val writer = FileLogWriter(FileLogWriterConfig(logFile = logFile))

            writer.log(Severity.Info, "hello from the app", "TestTag", null)

            assertTrue(
                waitUntil { logFile.isFile && logFile.readText().contains("hello from the app") },
                "the message never made it to disk, file was ${logFile.length()} bytes",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `every message is written, not just the ones that fill a buffer`() {
        val dir = Files.createTempDirectory("file-log-writer-many").toFile()
        try {
            val logFile = File(dir, "AppLog.log")
            val writer = FileLogWriter(FileLogWriterConfig(logFile = logFile))

            val messages = (1..20).map { "line number $it" }
            messages.forEach { writer.log(Severity.Info, it, "TestTag", null) }

            assertTrue(
                waitUntil { messages.all { logFile.readText().contains(it) } },
                "not every message landed on disk",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `rotates into a backup once the size limit is passed`() {
        val dir = Files.createTempDirectory("file-log-writer-rotate").toFile()
        try {
            val logFile = File(dir, "AppLog.log")
            val writer = FileLogWriter(
                FileLogWriterConfig(logFile = logFile, maxFileSizeBytes = 512, keepBackups = 1)
            )

            repeat(200) { writer.log(Severity.Info, "padding message $it", "TestTag", null) }

            assertTrue(
                waitUntil { File(dir, "AppLog.log.1").isFile },
                "the log should have rotated into a backup, but the dir holds " +
                    (dir.listFiles()?.joinToString { "${it.name}=${it.length()}b" } ?: "<empty>"),
            )
            assertTrue(logFile.length() <= 512, "the live log should respect the size limit")
        } finally {
            dir.deleteRecursively()
        }
    }
}
