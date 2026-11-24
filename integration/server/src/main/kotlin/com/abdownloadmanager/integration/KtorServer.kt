package com.abdownloadmanager.integration

import io.ktor.server.engine.EmbeddedServer

class KtorServer(
    private val server: EmbeddedServer<*, *>
) : MyServer {
    override fun stopMyServer() {
        // Use short grace/timeout so the port is released quickly.
        // Without explicit values Ktor may hold the socket open long enough
        // that a rapid toggle causes BindException on the next start.
        server.stop(gracePeriodMillis = 100, timeoutMillis = 500)
    }

    override fun startMyServer() {
        server.start(wait = false)
    }
}
