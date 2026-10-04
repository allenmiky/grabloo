package myPlugins

plugins {
    kotlin("jvm")
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
    "kotlinx.coroutines.FlowPreview",
)

fun getFeatures(): Set<String> = setOf(
    // Keep in sync with kotlinMultiplatform.gradle.kts: same expect/actual Beta policy.
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
