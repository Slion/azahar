// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("com.android.library") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

tasks.register("clean").configure {
    delete(rootProject.layout.buildDirectory)
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 built-in Kotlin: pin the Kotlin Gradle plugin version used by the build.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.10.2")
    }
}
