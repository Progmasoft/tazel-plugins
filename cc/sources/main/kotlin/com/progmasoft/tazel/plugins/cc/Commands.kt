/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.tazel.plugins.cc

import java.nio.file.Path

/** How a compiler takes its options: `-I dir` like GCC and Clang, or `/I dir` like MSVC. */
internal enum class Flavor {
  GNU,
  MSVC,
}

/** A compiler and the programs it runs for C and for C++. */
internal enum class Compiler(
  val cProgram: String,
  val cxxProgram: String,
  val flavor: Flavor,
) {
  CLANG("clang", "clang++", Flavor.GNU),
  GCC("gcc", "g++", Flavor.GNU),
  CL("cl", "cl", Flavor.MSVC),
  CLANGCL("clang-cl", "clang-cl", Flavor.MSVC),
}

internal enum class Kind(
  val label: String,
  val block: String,
  /** The standards, as their two last digits: `3` is C++03. */
  val standards: Set<Int>,
) {
  C("C", "c", setOf(89, 90, 99, 11, 17, 18, 23)),
  CXX("C++", "cxx", setOf(98, 3, 11, 14, 17, 20, 23, 26)),
}

/** A language block, checked and fixed once the project file has written it. */
internal data class Language(
  val kind: Kind,
  val compiler: Compiler?,
  val std: Int?,
  val extensions: Boolean,
  val objc: Boolean,
  val cli: Boolean,
  val packageManager: PackageManager,
  val flags: List<String>,
  val optionalFlags: List<String>,
  val files: List<String>,
  val includes: List<String>,
  val definitions: Map<String, String?>,
) {
  /** The program of [compiler] for this language. */
  val program: String?
    get() = compiler?.let { if (kind == Kind.C) it.cProgram else it.cxxProgram }

  companion object {
    fun of(scope: CScope) =
      of(Kind.C, scope.compiler?.let { Compiler.valueOf(it.name) }, scope, cli = false)

    fun of(scope: CXXScope) =
      of(Kind.CXX, scope.compiler?.let { Compiler.valueOf(it.name) }, scope, scope.cli)

    private fun of(kind: Kind, compiler: Compiler?, scope: LanguageScope, cli: Boolean): Language {
      val language =
        Language(
          kind,
          compiler,
          scope.std,
          scope.extensions,
          scope.objc,
          cli,
          scope.packageManager,
          scope.flags.toList(),
          scope.optionalFlags.toList(),
          scope.files.toList(),
          scope.includes.toList(),
          scope.definitions.toMap(),
        )
      language.problem()?.let { throw IllegalArgumentException(it) }
      return language
    }
  }

  /** What is wrong with the block, or `null`. */
  fun problem(): String? {
    val block = kind.block
    for (path in files + includes) {
      val relative = Path.of(path).normalize()
      if (relative.isAbsolute || relative.startsWith("..")) {
        return "cc.$block: '$path' is not inside the project directory"
      }
    }
    if (files.isNotEmpty() && compiler == null) {
      return "cc.$block names files and no compiler; set compiler"
    }
    val flavor = compiler?.flavor
    if (extensions && flavor == Flavor.MSVC) return "cc.$block: extensions needs CLANG or GCC"
    if (objc && flavor == Flavor.MSVC) return "cc.$block: objc needs CLANG or GCC"
    if (cli && compiler != Compiler.CL) return "cc.cxx: cli needs the compiler CL"
    std?.let {
      if (it !in kind.standards) {
        return "cc.$block: std $it is not a standard of ${kind.label}; " +
          "use one of ${kind.standards.joinToString { "%02d".format(it) }}"
      }
    }
    return null
  }
}

/** The command lines of the build of a `cc { }` block. */
internal object Commands {
  /** The options a language block implies: its standard, Objective-C and C++/CLI. */
  fun languageFlags(language: Language): List<String> = buildList {
    val std = language.std
    when (language.compiler?.flavor) {
      Flavor.GNU -> {
        if (std != null) {
          val dialect =
            when (language.kind) {
              Kind.C -> if (language.extensions) "gnu" else "c"
              Kind.CXX -> if (language.extensions) "gnu++" else "c++"
            }
          add("-std=$dialect${"%02d".format(std)}")
        }
      }
      Flavor.MSVC -> {
        if (std != null) add(msvcStandard(language.kind, std))
        if (language.cli) add("/clr")
      }
      null -> {}
    }
  }

  /**
   * The `/std:` option of MSVC for a standard. MSVC knows fewer standards than GCC and Clang: an
   * older one takes its oldest, a newer one `latest`.
   */
  fun msvcStandard(kind: Kind, std: Int): String =
    when (kind) {
      Kind.C ->
        when (std) {
          17,
          18 -> "/std:c17"
          23 -> "/std:clatest"
          else -> "/std:c11"
        }
      Kind.CXX ->
        when (std) {
          17 -> "/std:c++17"
          20 -> "/std:c++20"
          23,
          26 -> "/std:c++latest"
          else -> "/std:c++14"
        }
    }

  /** The options that make a compiler refuse an option it does not know, for a probe. */
  fun strictness(flavor: Flavor): List<String> =
    when (flavor) {
      Flavor.GNU -> listOf("-Werror")
      Flavor.MSVC -> listOf("/WX")
    }

  /** Compiles one source into one object. */
  fun compile(
    language: Language,
    program: Path,
    source: Path,
    objectFile: Path,
    includes: List<Path>,
    extraFlags: List<String>,
  ): List<String> {
    val flavor = language.compiler!!.flavor
    val definitions =
      language.definitions.map { (name, value) ->
        val prefix = if (flavor == Flavor.GNU) "-D" else "/D"
        if (value == null) "$prefix$name" else "$prefix$name=$value"
      }
    return when (flavor) {
      Flavor.GNU ->
        listOf(program.toString()) +
          languageFlags(language) +
          language.flags +
          extraFlags +
          includes.flatMap { listOf("-I", it.toString()) } +
          definitions +
          listOf("-c") +
          (if (language.objc) {
            listOf("-x", if (language.kind == Kind.C) "objective-c" else "objective-c++")
          } else {
            emptyList()
          }) +
          listOf(source.toString(), "-o", objectFile.toString())
      Flavor.MSVC ->
        listOf(program.toString(), "/nologo") +
          languageFlags(language) +
          language.flags +
          extraFlags +
          includes.flatMap { listOf("/I", it.toString()) } +
          definitions +
          listOf(
            "/c",
            if (language.kind == Kind.C) "/Tc" else "/Tp",
            source.toString(),
            "/Fo$objectFile",
          )
    }
  }

  /** Links objects into an executable. */
  fun link(
    flavor: Flavor,
    program: Path,
    objects: List<Path>,
    output: Path,
    libraryDirectories: List<Path>,
    libraries: List<String>,
    linkFlags: List<String>,
  ): List<String> =
    when (flavor) {
      Flavor.GNU ->
        listOf(program.toString()) +
          objects.map(Path::toString) +
          listOf("-o", output.toString()) +
          libraryDirectories.map { "-L$it" } +
          libraries +
          linkFlags
      Flavor.MSVC ->
        listOf(program.toString(), "/nologo") +
          objects.map(Path::toString) +
          listOf("/Fe$output", "/link") +
          libraryDirectories.map { "/LIBPATH:$it" } +
          libraries +
          linkFlags
    }

  /**
   * A command as Windows has to receive it. The JVM passes an argument with a quote in it, such as
   * `-DVERSION="3"`, without escaping the quote, and the C runtime of the compiler then drops it;
   * such an argument is escaped by the rules of that runtime: a quote becomes `\"`, and the
   * backslashes before it are doubled.
   */
  fun forWindows(command: List<String>): List<String> = command.map { argument ->
    if ('"' !in argument) return@map argument
    buildString {
      var backslashes = 0
      for (character in argument) {
        when (character) {
          '\\' -> backslashes++
          '"' -> {
            append("\\".repeat(backslashes * 2 + 1)).append('"')
            backslashes = 0
          }
          else -> {
            append("\\".repeat(backslashes)).append(character)
            backslashes = 0
          }
        }
      }
      append("\\".repeat(backslashes))
    }
  }

  /** The extension of an object file of [flavor]. */
  fun objectExtension(flavor: Flavor) = if (flavor == Flavor.MSVC) ".obj" else ".o"
}
