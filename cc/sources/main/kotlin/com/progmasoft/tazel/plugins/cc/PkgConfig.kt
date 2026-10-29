/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.tazel.plugins.cc

import java.nio.file.Path

/** The flags one `.pc` file gives: what `pkg-config --cflags` and `--libs` would print. */
internal data class PkgConfigFlags(
  val cflags: List<String>,
  val libs: List<String>,
)

/**
 * Reads the `.pc` files the PkgConfigDeps generator of Conan writes, without the `pkg-config`
 * program: variables (`name=value`), the `Cflags:` and `Libs:` fields and `${name}` references.
 * `Requires` is not followed; Conan writes a file for every package and component, and all of them
 * are read.
 */
internal object PkgConfig {
  private val variable = Regex("^([A-Za-z0-9_.]+)\\s*=(.*)$")
  private val field = Regex("^([A-Za-z0-9_.]+)\\s*:(.*)$")
  private val reference = Regex("\\$\\{([A-Za-z0-9_.]+)}")

  fun read(text: String): PkgConfigFlags {
    val variables = linkedMapOf<String, String>()
    val fields = linkedMapOf<String, String>()
    for (raw in text.lines()) {
      val line = raw.substringBefore('#').trim()
      if (line.isEmpty()) continue
      variable.matchEntire(line)?.let { match ->
        variables[match.groupValues[1]] = expand(match.groupValues[2].trim(), variables)
      }
        ?: field.matchEntire(line)?.let { match ->
          fields[match.groupValues[1]] = expand(match.groupValues[2].trim(), variables)
        }
    }
    return PkgConfigFlags(split(fields["Cflags"]), split(fields["Libs"]))
  }

  /** Turns the flags of `.pc` files into what the compiler and the linker of [flavor] take. */
  fun provided(cflags: List<String>, libs: List<String>, flavor: Flavor): Provided {
    val includes = mutableListOf<Path>()
    val compile = mutableListOf<String>()
    for (flag in cflags.distinct()) {
      when {
        flag.startsWith("-I") -> includes.add(Path.of(flag.removePrefix("-I")))
        flag.startsWith("-D") -> compile += if (flavor == Flavor.MSVC) "/D" + flag.drop(2) else flag
        flavor == Flavor.GNU -> compile += flag
      }
    }
    val directories = mutableListOf<Path>()
    val libraries = mutableListOf<String>()
    val link = mutableListOf<String>()
    for (flag in libs.distinct()) {
      when {
        flag.startsWith("-L") -> directories.add(Path.of(flag.removePrefix("-L")))
        flag.startsWith("-l") ->
          libraries += if (flavor == Flavor.MSVC) flag.drop(2) + ".lib" else flag
        flavor == Flavor.GNU -> link += flag
      }
    }
    return Provided(includes, compile, directories, libraries, link)
  }

  private fun expand(value: String, variables: Map<String, String>): String =
    reference.replace(value) { match -> variables[match.groupValues[1]].orEmpty() }

  private fun split(value: String?): List<String> =
    value?.split(Regex("\\s+"))?.filter(String::isNotEmpty).orEmpty()
}
