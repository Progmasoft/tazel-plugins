/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

// The plugins Progmasoft publishes for the Tazel project file. Each is built by the Gradle plugin
// com.progmasoft.tazel.plugin, whose version is the Tazel it is built for.
plugins {
    kotlin("jvm") version "2.4.0" apply false
    id("com.progmasoft.tazel.plugin") version "0.2.0" apply false
    id("com.diffplug.spotless") version "8.9.0"
}

repositories { mavenCentral() }

spotless {
    kotlin {
        target("*/sources/**/*.kt")
        ktfmt().googleStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
