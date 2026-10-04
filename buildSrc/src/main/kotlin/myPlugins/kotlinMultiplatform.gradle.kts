package myPlugins

plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
}

fun getOptIns(): Set<String> = setOf(
    "androidx.compose.animation.ExperimentalAnimationApi",
    "androidx.compose.foundation.ExperimentalFoundationApi",
    "androidx.compose.ui.ExperimentalComposeUiApi",
    // flatMapLatest / mapLatest / debounce are FlowPreview. They are already used across
    // shared/app, shared/utils and desktop/app, so opt in once here rather than sprinkling
    // @OptIn over 25 call sites.
    "kotlinx.coroutines.FlowPreview",
)

fun getFeatures(): Set<String> = setOf(
    // The project declares 8 expect class/object types (URLOpener, Startup, ClipboardUtil, ...).
    // expect/actual classes are still Beta in K2, so this silences the per-declaration warning
    // instead of rewriting every expect type as a plain top-level function.
    "expect-actual-classes",
)

val jvmToolchainVersion =  providers.gradleProperty("jvm.toolchain").get().toInt()

kotlin {
    jvmToolchain(jvmToolchainVersion)
    compilerOptions {
        val optIns = getOptIns().map { "-Xopt-in=$it" }
        val features = getFeatures().map { "-X$it" }
        freeCompilerArgs.set(optIns + features)
    }
}
