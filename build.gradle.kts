buildscript {
    dependencies {
        // AGP 9 has built-in Kotlin with a runtime dependency on an older Kotlin Gradle plugin;
        // this raises it to the catalog's `kotlin` so it matches the compose/serialization plugins.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
