package com.abdownloadmanager.desktop.utils

import com.abdownloadmanager.desktop.AppArguments
import com.abdownloadmanager.shared.util.schemakt.initializeForABDM
import co.touchlab.kermit.Severity
import io.github.amir1376.schemakt.Schema
import ir.amirab.util.guardedEntry
import ir.amirab.util.logger.AppLogger

enum class EntryType {
    GUI,
    CLI,
    NativeMessaging,
}

object EntrypointInitializer {
    private val booted = guardedEntry()
    fun boot(
        debug: Boolean = false,
        entryType: EntryType
    ) {
        booted.action {
            AppArguments.update {
                it.copy(
                    debug = debug,
                )
            }
            AppProperties.boot(AppInfo.definedPaths.appPropertiesFile)
            val isDebug = AppInfo.isInDebugMode()
            AppLogger.init(
                // Running from source (an IDE or `gradlew run`) counts as debug mode, so the log
                // also goes to the console. With this hardcoded to false there was no way to see
                // anything the app logged while running it from Gradle.
                writeToConsole = isDebug,
                logFilePath = AppInfo.definedPaths.logDir
                    .let {
                        it / when (entryType) {
                            EntryType.GUI -> "AppLog.log"
                            EntryType.CLI -> "CliLog.log"
                            EntryType.NativeMessaging -> "NativeMessaging.log"
                        }
                    }.toFile(),
                minSeverity = if (isDebug) Severity.Verbose else Severity.Info,
            )
            Schema.initializeForABDM()
        }
    }
}
