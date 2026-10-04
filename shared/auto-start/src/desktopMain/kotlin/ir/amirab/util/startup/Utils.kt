package ir.amirab.util.startup

import ir.amirab.util.platform.Platform
import java.awt.GraphicsEnvironment
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object Utils {
    @get:Throws(Exception::class)
    val isRoot: Boolean
        get() = if (Platform.getCurrentPlatform() === Platform.Desktop.Windows) {
            false
        } else {
            // Runtime.exec(String) is deprecated since Java 18; ProcessBuilder is the replacement.
            val process = ProcessBuilder("whoami").redirectErrorStream(true).start()
            BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() } == "root"
        }

    val isHeadless: Boolean
        get() = GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance()
}
