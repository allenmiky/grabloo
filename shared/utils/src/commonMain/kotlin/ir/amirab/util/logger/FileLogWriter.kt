@file:OptIn(ExperimentalTime::class)

package ir.amirab.util.logger

import co.touchlab.kermit.*
import ir.amirab.util.createParentDirectories
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import okio.buffer
import okio.FileSystem
import okio.BufferedSink
import okio.Buffer
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal open class FileLogWriter(
    private val config: FileLogWriterConfig,
    private val clock: Clock = Clock.System,
    private val messageStringFormatter: MessageStringFormatter = DefaultFormatter,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : LogWriter() {

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val coroutineScope = CoroutineScope(
        newSingleThreadContext("FileLogWriter") +
                SupervisorJob() +
                CoroutineName("FileLogWriter") +
                CoroutineExceptionHandler { _, throwable ->
                    // can't log it, we're the logger -- print to standard error
                    println("FileLogWriter: Uncaught exception in writer coroutine")
                    throwable.printStackTrace()
                },
    )

    private val loggingChannel: Channel<Buffer> = Channel()

    init {
        coroutineScope.launch {
            writer()
        }
    }

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        bufferLog(
            formatMessage(
                severity = severity,
                tag = Tag(tag),
                message = Message(message),
            ),
            throwable,
        )
    }

    private fun bufferLog(message: String, throwable: Throwable?) {
        val log = buildString {
            if (config.prependTimestamp) {
                append(clock.now().format(DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET))
                append(" ")
            }
            appendLine(message)
            if (throwable != null) {
                appendLine(throwable.stackTraceToString())
            }
        }
        loggingChannel.trySendBlocking(Buffer().apply { writeUtf8(log) })
    }

    private fun formatMessage(severity: Severity, tag: Tag?, message: Message): String =
        messageStringFormatter.formatMessage(severity, if (config.logTag) tag else null, message)

    val logFile: File = config.logFile

    private fun backupFile(index: Int): File = File(logFile.absolutePath + ".$index")

    private fun openSink(): BufferedSink {
        logFile.parentFile?.mkdirs()
        rotateIfOversized()
        return fileSystem.sink(logFile.toOkioPath(), mustCreate = false).buffer()
    }

    private fun rotateIfOversized() {
        val currentSize = logFile.length()
        if (currentSize <= config.maxFileSizeBytes) return
        if (config.keepBackups <= 0) {
            logFile.delete()
            return
        }
        for (index in (config.keepBackups - 1) downTo 1) {
            val source = backupFile(index)
            val target = backupFile(index + 1)
            if (!source.exists()) continue
            if (target.exists()) {
                target.delete()
            }
            runCatching { fileSystem.atomicMove(source.toOkioPath(), target.toOkioPath()) }
        }
        if (logFile.exists()) {
            backupFile(1).delete()
            runCatching { fileSystem.atomicMove(logFile.toOkioPath(), backupFile(1).toOkioPath()) }
        }
    }

    private suspend fun writer() {
        var sink = openSink()
        var bytesInCurrentSink = 0L
        try {
            while (currentCoroutineContext().isActive) {
                val buffer = loggingChannel.receiveCatching().getOrNull() ?: break
                // Read the size before writing: okio's write drains the source buffer, so
                // buffer.size is 0 afterwards. Counting after the write meant bytesInCurrentSink
                // never moved off 0 and rotation never triggered, so the log grew unbounded.
                val byteCount = buffer.size
                sink.write(buffer, byteCount)
                // Every message has to reach the disk on its own. The writer coroutine lives on a
                // daemon thread, so nothing runs the finally block below when the app exits and a
                // buffered sink silently drops whatever it still holds. Without this flush the log
                // file stayed at 0 bytes for the whole run, which makes the app undebuggable.
                sink.flush()
                bytesInCurrentSink += byteCount
                if (bytesInCurrentSink >= config.maxFileSizeBytes) {
                    sink.close()
                    sink = openSink()
                    bytesInCurrentSink = 0
                }
            }
        } finally {
            runCatching { sink.flush() }
            runCatching { sink.close() }
        }
    }
}

data class FileLogWriterConfig(
    val logFile: File,
    val logTag: Boolean = true,
    val prependTimestamp: Boolean = true,
    val maxFileSizeBytes: Long = 2L * 1024 * 1024,
    val keepBackups: Int = 1,
)