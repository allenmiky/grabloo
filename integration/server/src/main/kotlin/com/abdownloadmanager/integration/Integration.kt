package com.abdownloadmanager.integration

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.net.BindException

sealed interface IntegrationResult {
    data object Inactive : IntegrationResult
    data class Fail(val throwable: Throwable) : IntegrationResult
    data class Success(val port: Int) : IntegrationResult
}

class Integration(
    val integrationHandler: IntegrationHandler,
    val scope: CoroutineScope,
    private val json: Json,
    val debugMode: Boolean,
) {

    private val portFlow = MutableStateFlow<IntegrationSettings?>(null)
    val integrationStatus = MutableStateFlow<IntegrationResult>(IntegrationResult.Inactive)

    fun enable(settings: IntegrationSettings) {
        portFlow.update { settings }
    }

    fun disable() {
        portFlow.update { null }
    }

    fun boot() {
        scope.launch {
            kotlin.runCatching {
                portFlow.collect { settings ->
                    runCatching {
                        if (settings != null) {
                            startServer(settings)
                            integrationStatus.update { IntegrationResult.Success(settings.port) }
                        } else {
                            stopServer()
                            integrationStatus.update { IntegrationResult.Inactive }
                        }
                    }.onFailure { throwable ->
                        integrationStatus.update {
                            IntegrationResult.Fail(throwable)
                        }
                        kotlin.runCatching {
                            disable()
                        }
                    }
                }
            }
        }
    }

    @Volatile
    private var server: MyServer? = null

    private suspend fun startServer(settings: IntegrationSettings) {
        stopServer()
        // After stopping, the OS may keep the port in TIME_WAIT for a brief period.
        // Retry a few times with a short delay before giving up.
        val maxAttempts = 5
        val retryDelayMs = 500L
        repeat(maxAttempts) { attempt ->
            val result = runCatching {
                val newServer = createServer(settings)
                withContext(Dispatchers.IO) {
//                    println("start server")
                    newServer.startMyServer()
                }
                this.server = newServer
            }
            if (result.isSuccess) return
            val cause = result.exceptionOrNull()
            if (cause is BindException && attempt < maxAttempts - 1) {
                // Port not yet released by OS; wait and retry
                delay(retryDelayMs)
            } else {
                throw cause ?: IllegalStateException("Unknown error starting server")
            }
        }
    }

    private suspend fun stopServer() {
        server?.let {
//            println("stop server")
            withContext(Dispatchers.IO) {
                it.stopMyServer()
            }
        }
        server = null
    }

    private fun createServer(settings: IntegrationSettings): MyServer {
        val server = embeddedServer(
            factory = CIO,
            port = settings.port,
            host = "127.0.0.1",
        ) {
            setupRouting(json, integrationHandler, settings)
        }
        return KtorServer(server)
    }
}
