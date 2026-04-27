// Top-level build file — plugin declarations only (no dependencies here) except for
// project-wide quality tools that need to run from the root.
plugins {
    alias(libs.plugins.android.application)  apply false
    alias(libs.plugins.android.library)      apply false
    alias(libs.plugins.kotlin.android)       apply false
    alias(libs.plugins.kotlin.compose)       apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt)                 apply false
    alias(libs.plugins.ksp)                  apply false
    alias(libs.plugins.google.services)      apply false
    // Applied at root so `./gradlew detekt` scans all submodule Kotlin sources.
    alias(libs.plugins.detekt)
}

// Raise test JVM heap for all modules — Compose + Hilt + MockK classloading
// can exhaust the default 512 MB in feature modules with heavy dependencies.
subprojects {
    tasks.withType<Test>().configureEach {
        maxHeapSize = "1g"
    }
}

detekt {
    config.setFrom("$projectDir/config/detekt.yml")
    source.setFrom(
        fileTree(".") {
            include("**/src/main/kotlin/**/*.kt")
            exclude("**/build/**")
        }
    )
    parallel = true
    autoCorrect = false
}
