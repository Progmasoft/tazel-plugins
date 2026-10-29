/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.tazel.plugins.cc

import com.progmasoft.api.tazel.dsl.TazelPluginDsl
import com.progmasoft.api.tazel.dsl.plugin.PluginExtensions
import com.progmasoft.api.tazel.dsl.plugin.ProjectPlugin
import com.progmasoft.api.tazel.dsl.plugin.ProjectPluginContext

/** Progmasoft.CC: the `cc { }` block, which compiles and links C and C++ sources. */
class CCPlugin : ProjectPlugin {
  override fun apply(context: ProjectPluginContext) = context.registerExtension("cc")
}

/** The C compilers `c { compiler = ... }` takes. */
enum class CCompiler {
  CLANG,
  CL,
  CLANGCL,
  GCC,
}

/** The C++ compilers `cxx { compiler = ... }` takes. */
enum class CXXCompiler {
  CLANG,
  CL,
  CLANGCL,
  GCC,
}

/** Where the libraries of a language block come from. */
enum class PackageManager {
  /** vcpkg in manifest mode: `vcpkg.json` beside the project file. */
  VCPKG,

  /** Conan 2: `conanfile.txt` or `conanfile.py` beside the project file. */
  CONAN,
  NONE,
}

/** The settings both language blocks share. */
@TazelPluginDsl
abstract class LanguageScope internal constructor() {
  /** The standard, such as `23`; the default of the compiler unless set. */
  var std: Int? = null

  /** The GNU dialect of the standard; needs Clang or GCC. */
  var extensions: Boolean = false

  /** Objective-C, or Objective-C++ in `cxx`; needs Clang or GCC. */
  var objc: Boolean = false

  /** Where the libraries of the block come from; [PackageManager.NONE] unless set. */
  var packageManager: PackageManager = PackageManager.NONE

  internal val flags = mutableListOf<String>()
  internal val optionalFlags = mutableListOf<String>()
  internal val files = mutableListOf<String>()
  internal val includes = mutableListOf<String>()
  internal val definitions = linkedMapOf<String, String?>()

  /** Flags passed to the compiler as they are written. */
  fun flags(vararg flags: String) {
    this.flags += flags
  }

  /** Flags passed only when the compiler accepts them, which a probe of each finds out. */
  fun flagsIfSupported(vararg flags: String) {
    optionalFlags += flags
  }

  /** Sources to compile, relative to the project directory. */
  fun files(vararg files: String) {
    this.files += files
  }

  /** Directories of headers, relative to the project directory: `-I include`. */
  fun include(vararg directories: String) {
    includes += directories
  }

  /** Macros: `define("DEBUG" to null, "VERSION" to "3")` passes `-DDEBUG -DVERSION=3`. */
  fun define(vararg definitions: Pair<String, String?>) {
    for ((name, value) in definitions) {
      require(name.matches(MACRO)) { "define: '$name' is not a macro name" }
      this.definitions[name] = value
    }
  }

  private companion object {
    val MACRO = Regex("[A-Za-z_][A-Za-z0-9_]*")
  }
}

/** The `c { }` block. */
@TazelPluginDsl
class CScope internal constructor() : LanguageScope() {
  /** The compiler; required when the block has files. */
  var compiler: CCompiler? = null
}

/** The `cxx { }` block. */
@TazelPluginDsl
class CXXScope internal constructor() : LanguageScope() {
  /** The compiler; required when the block has files. */
  var compiler: CXXCompiler? = null

  /** C++/CLI; needs [CXXCompiler.CL]. */
  var cli: Boolean = false
}

/** The `cc { }` block. */
@TazelPluginDsl
class CCScope internal constructor() {
  /** Whether the project is built by this block; `false` unless set. */
  var enabled: Boolean = false

  internal var c: CScope? = null
  internal var cxx: CXXScope? = null

  fun c(block: CScope.() -> Unit) {
    c = (c ?: CScope()).apply(block)
  }

  fun cxx(block: CXXScope.() -> Unit) {
    cxx = (cxx ?: CXXScope()).apply(block)
  }
}

/**
 * Compiles the C and C++ sources the block names and links them into one executable, named after
 * the project, in the output directory. A `CMakeLists.txt` plays no part in it.
 */
fun cc(block: CCScope.() -> Unit) {
  val settings = CCScope().apply(block)
  val scope = PluginExtensions.require("cc")
  if (!settings.enabled) return
  val languages = listOfNotNull(settings.c?.let(Language::of), settings.cxx?.let(Language::of))
  require(languages.any { it.files.isNotEmpty() }) { "cc { enabled = true } names no source file" }
  val build = CCBuild(languages)
  scope.buildStep("build") { context -> build.run(context) }
}
