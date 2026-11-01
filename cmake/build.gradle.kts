/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-2.0
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("com.progmasoft.tazel.plugin")
}

version = "0.1.1"

repositories { mavenCentral() }

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        allWarningsAsErrors = true
    }
}

sourceSets {
    main { kotlin.srcDir("sources/main/kotlin") }
    test { kotlin.srcDir("sources/test/kotlin") }
}

dependencies {
    tazelApi()
    testImplementation(kotlin("test"))
}

tazelPlugin {
    publisher = "Progmasoft"
    name = "CMake"
    pluginClass = "com.progmasoft.tazel.plugins.cmake.CMakePlugin"
    imports = listOf("com.progmasoft.tazel.plugins.cmake.*")
    description = "Configures and builds a CMake project from the Tazel project file."
    authors = listOf("Progmasoft <support@progmasoft.com>")
    license = "MPL-2.0 WITH AdditionRef-Progmasoft-Exception-2.0"
    homepage = "https://viget.progmasoft.com/dslplugins/tazel/Progmasoft/CMake"
    repository = "https://github.com/Progmasoft/tazel-plugins"
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.test { useJUnitPlatform() }
