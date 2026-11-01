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
    name = "CC"
    pluginClass = "com.progmasoft.tazel.plugins.cc.CCPlugin"
    imports = listOf("com.progmasoft.tazel.plugins.cc.*")
    description = "Compiles and links C and C++ sources with Clang, GCC, MSVC or clang-cl, with vcpkg or Conan for dependencies."
    authors = listOf("Progmasoft <support@progmasoft.com>")
    license = "MPL-2.0 WITH AdditionRef-Progmasoft-Exception-2.0"
    homepage = "https://viget.progmasoft.com/dslplugins/tazel/Progmasoft/CC"
    repository = "https://github.com/Progmasoft/tazel-plugins"
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.test { useJUnitPlatform() }
