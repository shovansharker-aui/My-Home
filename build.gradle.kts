plugins {
    // AGP 9's built-in Kotlin support means org.jetbrains.kotlin.android is
    // no longer applied; the Compose Compiler plugin is still required.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
