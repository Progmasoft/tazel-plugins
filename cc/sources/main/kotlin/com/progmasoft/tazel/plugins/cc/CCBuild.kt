/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

package com.progmasoft.tazel.plugins.cc

import com.progmasoft.api.tazel.dsl.plugin.BuildContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.extension
import kotlin.io.path.name

/** What `tazel build` runs for a `cc { }` block: compile every source, then link them. */
internal class CCBuild(private val languages: List<Language>) {
  fun run(context: BuildContext) {
    val used = languages.filter { it.files.isNotEmpty() }
    val programs = used.associateWith { language ->
      context.findProgram(language.program!!)
        ?: context.fail("${language.program} was not found on PATH")
    }
    val flavor = used.first().compiler!!.flavor
    if (used.any { it.compiler!!.flavor != flavor }) {
      context.fail("cc.c and cc.cxx use compilers that take their options differently")
    }

    val packages = Packages(context, flavor)
    val dependencies =
      used.map { it.packageManager }.distinct().associateWith { manager -> packages.of(manager) }

    val objects = mutableListOf<Path>()
    for (language in used) {
      val program = programs.getValue(language)
      val provided = dependencies.getValue(language.packageManager)
      val supported = probe(context, language, program)
      val includes = language.includes.map { context.projectRoot.resolve(it).normalize() }
      for (file in language.files) {
        val source = context.projectRoot.resolve(file).normalize()
        if (!Files.isRegularFile(source))
          context.fail("cc.${language.kind.block}: $file does not exist")
        val objectFile =
          context.workDirectory
            .resolve("objects")
            .resolve(language.kind.block)
            .resolve(Path.of(file).normalize().toString() + Commands.objectExtension(flavor))
        Files.createDirectories(objectFile.parent)
        context.status("Compiling", "$file (${language.kind.label})")
        val exitCode =
          context.runOnHost(
            Commands.compile(
              language,
              program,
              source,
              objectFile,
              includes + provided.includes,
              supported + provided.compileFlags,
            )
          )
        if (exitCode != 0) context.fail("${language.program} could not compile $file")
        objects.add(objectFile)
      }
    }

    // C++ objects need the C++ driver to link with its runtime; C alone links with the C driver.
    val linker = used.lastOrNull { it.kind == Kind.CXX } ?: used.first()
    val name = context.project.name ?: context.projectRoot.fileName.toString()
    val output = context.outputDirectory.resolve(name + context.host.executableExtension)
    Files.createDirectories(output.parent)
    val provided = dependencies.values
    context.status("Linking", output.name)
    val linked =
      context.runOnHost(
        Commands.link(
          flavor,
          programs.getValue(linker),
          objects,
          output,
          provided.flatMap { it.libraryDirectories }.distinct(),
          provided.flatMap { it.libraries }.distinct(),
          provided.flatMap { it.linkFlags }.distinct(),
        )
      )
    if (linked != 0) context.fail("${linker.program} could not link $name")
    // A shared library of a dependency has to lie beside the executable to be found.
    for (library in provided.flatMap { it.runtimeFiles }.distinct()) {
      Files.copy(library, output.resolveSibling(library.name), REPLACE_EXISTING)
    }
  }

  /** The flags of `flagsIfSupported` that the compiler accepts, each tried on an empty program. */
  private fun probe(context: BuildContext, language: Language, program: Path): List<String> {
    if (language.optionalFlags.isEmpty()) return emptyList()
    val flavor = language.compiler!!.flavor
    val directory = Files.createDirectories(context.workDirectory.resolve("probe"))
    val source =
      directory.resolve(if (language.kind == Kind.C) "probe.c" else "probe.cpp").also {
        Files.writeString(it, "int main(void) { return 0; }\n")
      }
    val objectFile = directory.resolve("probe" + Commands.objectExtension(flavor))
    return language.optionalFlags.filter { flag ->
      val command =
        Commands.compile(
          language.copy(flags = emptyList(), definitions = emptyMap(), objc = false),
          program,
          source,
          objectFile,
          emptyList(),
          Commands.strictness(flavor) + flag,
        )
      context.captureOnHost(command, directory).exitCode == 0
    }
  }
}

/** [BuildContext.run] with the command adapted to the host; see [Commands.forWindows]. */
internal fun BuildContext.runOnHost(command: List<String>): Int =
  run(if (host.os.name == "WINDOWS") Commands.forWindows(command) else command)

/** [BuildContext.capture] with the command adapted to the host. */
internal fun BuildContext.captureOnHost(command: List<String>, directory: Path) =
  capture(if (host.os.name == "WINDOWS") Commands.forWindows(command) else command, directory)

/** What a package manager provides to compile and link with. */
internal data class Provided(
  val includes: List<Path> = emptyList(),
  val compileFlags: List<String> = emptyList(),
  val libraryDirectories: List<Path> = emptyList(),
  val libraries: List<String> = emptyList(),
  val linkFlags: List<String> = emptyList(),
  val runtimeFiles: List<Path> = emptyList(),
)

/** Installs the dependencies of the project with vcpkg or Conan, into the work directory. */
internal class Packages(
  private val context: BuildContext,
  private val flavor: Flavor,
) {
  fun of(manager: PackageManager): Provided =
    when (manager) {
      PackageManager.NONE -> Provided()
      PackageManager.VCPKG -> vcpkg()
      PackageManager.CONAN -> conan()
    }

  private val debug: Boolean
    get() = context.buildMode.name == "DEBUG"

  private fun vcpkg(): Provided {
    if (!Files.isRegularFile(context.projectRoot.resolve("vcpkg.json"))) {
      context.fail("PackageManager.VCPKG needs a vcpkg.json beside the project file")
    }
    val vcpkg =
      context.findProgram("vcpkg")
        ?: System.getenv("VCPKG_ROOT")
          ?.let { Path.of(it).resolve("vcpkg" + context.host.executableExtension) }
          ?.takeIf(Files::isRegularFile)
        ?: context.fail("vcpkg was found neither on PATH nor in VCPKG_ROOT")
    val triplet = Triplets.vcpkg(context.host)
    val installed = context.workDirectory.resolve("vcpkg_installed")
    context.status("Installing", "the vcpkg dependencies ($triplet)")
    val exitCode =
      context.runOnHost(
        listOf(
          vcpkg.toString(),
          "install",
          "--x-manifest-root=${context.projectRoot}",
          "--x-install-root=$installed",
          "--triplet=$triplet",
        )
      )
    if (exitCode != 0) context.fail("vcpkg could not install the dependencies")
    val prefix = installed.resolve(triplet).let { if (debug) it.resolve("debug") else it }
    val libraryDirectory = prefix.resolve("lib")
    val libraryExtensions = if (flavor == Flavor.MSVC) setOf("lib") else setOf("a", "so", "dylib")
    return Provided(
      includes = listOf(installed.resolve(triplet).resolve("include")),
      libraryDirectories = listOf(libraryDirectory),
      libraries =
        filesIn(libraryDirectory) { it.extension in libraryExtensions }.map(Path::toString),
      runtimeFiles = filesIn(prefix.resolve("bin")) { it.extension == "dll" },
    )
  }

  private fun conan(): Provided {
    val root = context.projectRoot
    if (
      !Files.isRegularFile(root.resolve("conanfile.txt")) &&
        !Files.isRegularFile(root.resolve("conanfile.py"))
    ) {
      context.fail(
        "PackageManager.CONAN needs a conanfile.txt or conanfile.py beside the project file"
      )
    }
    val conan = context.findProgram("conan") ?: context.fail("conan was not found on PATH")
    val output = context.workDirectory.resolve("conan")
    val buildType = if (debug) "Debug" else "Release"
    context.status("Installing", "the Conan dependencies ($buildType)")
    val exitCode =
      context.runOnHost(
        listOf(
          conan.toString(),
          "install",
          root.toString(),
          "--output-folder=$output",
          "--build=missing",
          "-g",
          "PkgConfigDeps",
          "-s",
          "build_type=$buildType",
        )
      )
    if (exitCode != 0) context.fail("conan could not install the dependencies")
    val flags =
      filesIn(output) { it.extension == "pc" }.map { PkgConfig.read(Files.readString(it)) }
    return PkgConfig.provided(flags.flatMap { it.cflags }, flags.flatMap { it.libs }, flavor)
  }

  private fun filesIn(directory: Path, accept: (Path) -> Boolean): List<Path> =
    if (!Files.isDirectory(directory)) emptyList()
    else Files.list(directory).use { paths -> paths.filter(accept).sorted().toList() }
}

/** The triplets of vcpkg for the hosts Tazel runs on. */
internal object Triplets {
  fun vcpkg(host: com.progmasoft.api.tazel.dsl.plugin.Host): String {
    val architecture =
      when (host.architecture.name) {
        "X86_64" -> "x64"
        "AARCH64" -> "arm64"
        "ARMV7H" -> "arm"
        else -> "x64"
      }
    val system =
      when (host.os.name) {
        "WINDOWS" -> "windows"
        "MACOS" -> "osx"
        "FREEBSD" -> "freebsd"
        "OPENBSD" -> "openbsd"
        else -> "linux"
      }
    return "$architecture-$system"
  }
}
