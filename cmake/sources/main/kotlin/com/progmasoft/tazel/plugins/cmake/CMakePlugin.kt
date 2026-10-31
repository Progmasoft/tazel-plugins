/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-2.0
 */

package com.progmasoft.tazel.plugins.cmake

import com.progmasoft.api.tazel.dsl.TazelPluginDsl
import com.progmasoft.api.tazel.dsl.plugin.BuildContext
import com.progmasoft.api.tazel.dsl.plugin.BuildMode
import com.progmasoft.api.tazel.dsl.plugin.PluginExtensions
import com.progmasoft.api.tazel.dsl.plugin.ProjectPlugin
import com.progmasoft.api.tazel.dsl.plugin.ProjectPluginContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * Progmasoft.CMake: the `cmake { }` block, which builds a CMake project of the project directory.
 */
class CMakePlugin : ProjectPlugin {
  override fun apply(context: ProjectPluginContext) = context.registerExtension("cmake")
}

/** The `cmake { }` block. */
@TazelPluginDsl
class CMakeScope internal constructor() {
  /** Whether the project is built with CMake; `false` unless set. */
  var enabled: Boolean = false

  internal val definitions = linkedMapOf<String, String>()

  /**
   * Sets cache variables of the configuration: `define("BUILD_TESTS" to "OFF")` passes
   * `-DBUILD_TESTS=OFF`. A later definition of the same name replaces an earlier one.
   */
  fun define(vararg definitions: Pair<String, String>) {
    for ((name, value) in definitions) {
      require(name.matches(CACHE_NAME)) { "cmake.define: '$name' is not a CMake variable name" }
      this.definitions[name] = value
    }
  }

  private companion object {
    val CACHE_NAME = Regex("[A-Za-z_][A-Za-z0-9_.+-]*")
  }
}

/**
 * Builds the CMake project of the project directory, whose `CMakeLists.txt` lies beside the project
 * file: `tazel build` configures it into a build directory of its own and builds it in the build
 * mode of the project, with executables and libraries written to the output directory.
 */
fun cmake(block: CMakeScope.() -> Unit) {
  val settings = CMakeScope().apply(block)
  // Asked even when the block is off, so a block without its plugin is always an error.
  val scope = PluginExtensions.require("cmake")
  if (!settings.enabled) return
  require(Files.isRegularFile(scope.projectRoot.resolve("CMakeLists.txt"))) {
    "cmake { enabled = true } needs a CMakeLists.txt beside the project file"
  }
  val build = CMakeBuild(settings.definitions.toMap())
  scope.buildStep("build") { context -> build.run(context) }
}

/** What `tazel build` runs for a `cmake { }` block. */
internal class CMakeBuild(private val definitions: Map<String, String>) {
  fun run(context: BuildContext) {
    val cmake = context.findProgram("cmake") ?: context.fail("cmake was not found on PATH")
    val configuration = configurationOf(context.buildMode)
    context.status("Configuring", "CMake ($configuration)")
    val configured =
      context.run(
        configureCommand(
          cmake,
          context.projectRoot,
          context.workDirectory,
          context.outputDirectory,
          configuration,
          definitions,
        )
      )
    if (configured != 0)
      context.fail("cmake could not configure the project (exit code $configured)")
    context.status("Building", "CMake ($configuration)")
    val built = context.run(buildCommand(cmake, context.workDirectory, configuration))
    if (built != 0) context.fail("cmake could not build the project (exit code $built)")
  }

  companion object {
    /** The configuration of CMake that a build mode of Tazel stands for. */
    fun configurationOf(mode: BuildMode) =
      when (mode) {
        BuildMode.DEBUG -> "Debug"
        BuildMode.RELEASE -> "Release"
      }

    /**
     * Configures [source] into [build]. Executables and libraries go to [output], for generators
     * with one configuration and for those with several; a definition of the project file replaces
     * any of these.
     */
    fun configureCommand(
      cmake: Path,
      source: Path,
      build: Path,
      output: Path,
      configuration: String,
      definitions: Map<String, String>,
    ): List<String> {
      val outputs = buildMap {
        put("CMAKE_BUILD_TYPE", configuration)
        for (kind in listOf("RUNTIME", "LIBRARY", "ARCHIVE")) {
          put("CMAKE_${kind}_OUTPUT_DIRECTORY", output.toString())
          put("CMAKE_${kind}_OUTPUT_DIRECTORY_${configuration.uppercase()}", output.toString())
        }
      }
      val all = outputs + definitions
      return listOf(cmake.toString(), "-S", source.toString(), "-B", build.toString()) +
        all.map { (name, value) -> "-D$name=$value" }
    }

    fun buildCommand(cmake: Path, build: Path, configuration: String) =
      listOf(cmake.toString(), "--build", build.toString(), "--config", configuration)
  }
}
