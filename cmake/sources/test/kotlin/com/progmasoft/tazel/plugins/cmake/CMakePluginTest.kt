/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.tazel.plugins.cmake

import com.progmasoft.api.tazel.dsl.plugin.BuildMode
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CMakePluginTest {
  private val cmake = Path.of("/usr/bin/cmake")
  private val root = Path.of("/project")
  private val work = Path.of("/project/.tazel/build/Progmasoft.CMake/build")
  private val output = Path.of("/project/build/release")

  @Test
  fun configuresIntoTheWorkDirectoryWithTheOutputsOfTheProject() {
    val command =
      CMakeBuild.configureCommand(
        cmake,
        root,
        work,
        output,
        CMakeBuild.configurationOf(BuildMode.RELEASE),
        linkedMapOf("ENABLED_SIMD" to "ON", "BUILD_TESTS" to "OFF"),
      )
    assertEquals(
      listOf(cmake.toString(), "-S", root.toString(), "-B", work.toString()),
      command.take(5),
    )
    val definitions = command.drop(5)
    assertEquals("-DCMAKE_BUILD_TYPE=Release", definitions.first())
    assertEquals(
      listOf("-DENABLED_SIMD=ON", "-DBUILD_TESTS=OFF"),
      definitions.takeLast(2),
    )
    for (kind in listOf("RUNTIME", "LIBRARY", "ARCHIVE")) {
      assert("-DCMAKE_${kind}_OUTPUT_DIRECTORY=$output" in definitions)
      assert("-DCMAKE_${kind}_OUTPUT_DIRECTORY_RELEASE=$output" in definitions)
    }
  }

  @Test
  fun aDefinitionOfTheProjectReplacesAnOutputDirectory() {
    val command =
      CMakeBuild.configureCommand(
        cmake,
        root,
        work,
        output,
        "Debug",
        mapOf("CMAKE_RUNTIME_OUTPUT_DIRECTORY" to "/elsewhere"),
      )
    assertEquals(
      listOf("-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=/elsewhere"),
      command.filter { it.startsWith("-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=") },
    )
  }

  @Test
  fun buildsInTheConfigurationOfTheMode() {
    assertEquals(
      listOf(cmake.toString(), "--build", work.toString(), "--config", "Debug"),
      CMakeBuild.buildCommand(cmake, work, CMakeBuild.configurationOf(BuildMode.DEBUG)),
    )
  }

  @Test
  fun aDefinitionNeedsAVariableName() {
    assertFailsWith<IllegalArgumentException> { CMakeScope().define("not a name" to "1") }
    val scope = CMakeScope()
    scope.define("A" to "1", "A" to "2")
    assertEquals(mapOf("A" to "2"), scope.definitions)
  }

  @Test
  fun theDescriptorNamesThePluginAndItsClass() {
    val loader = javaClass.classLoader
    val descriptor = Properties()
    loader.getResourceAsStream("META-INF/tazel-plugin.properties")!!.use(descriptor::load)
    assertEquals("Progmasoft", descriptor.getProperty("publisher"))
    assertEquals("CMake", descriptor.getProperty("name"))
    assertEquals("com.progmasoft.tazel.plugins.cmake.*", descriptor.getProperty("imports"))
    val service =
      loader
        .getResourceAsStream(
          "META-INF/services/com.progmasoft.api.tazel.dsl.plugin.ProjectPlugin"
        )!!
        .bufferedReader()
        .use { it.readText().trim() }
    assertEquals(CMakePlugin::class.java.name, service)
  }
}
