/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-2.0
 */

// Building against an unreleased Tazel: -PtazelCheckout=../tazel takes the Gradle plugin and
// tazel-api from that checkout instead of the Gradle Plugin Portal and maven.progmasoft.com.
val tazelCheckout: String? = providers.gradleProperty("tazelCheckout").orNull

pluginManagement {
    providers.gradleProperty("tazelCheckout").orNull?.let { includeBuild(it) }
    repositories {
        gradlePluginPortal()
        // The Gradle plugin of Tazel is also published beside tazel-api, so builds do not depend on the
        // Portal alone.
        maven("https://maven.progmasoft.com/tazel") {
            content { includeGroupByRegex("com\\.progmasoft\\.tazel(\\..*)?") }
        }
    }
}

rootProject.name = "tazel-plugins"

tazelCheckout?.let { includeBuild(it) }

include("cc", "cmake")
