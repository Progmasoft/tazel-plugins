/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-2.0
 */

package com.progmasoft.tazel.plugins.cc

import com.progmasoft.api.tazel.dsl.plugin.Architecture
import com.progmasoft.api.tazel.dsl.plugin.Host
import com.progmasoft.api.tazel.dsl.plugin.OperatingSystem
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CCPluginTest {
  private val program = Path.of("/usr/bin/clang")
  private val source = Path.of("/project/src/main.c")
  private val objectFile = Path.of("/work/main.c.o")

  private fun c(block: CScope.() -> Unit) = Language.of(CScope().apply(block))

  private fun cxx(block: CXXScope.() -> Unit) = Language.of(CXXScope().apply(block))

  @Test
  fun aGnuCompilerTakesDashOptions() {
    val language = c {
      compiler = CCompiler.CLANG
      std = 23
      flags("-Wall", "-Wextra")
      files("src/main.c")
      include("include")
      define("DEBUG" to null, "VERSION" to "3")
    }
    assertEquals(
      listOf(
        program.toString(),
        "-std=c23",
        "-Wall",
        "-Wextra",
        "-I",
        "include",
        "-DDEBUG",
        "-DVERSION=3",
        "-c",
        source.toString(),
        "-o",
        objectFile.toString(),
      ),
      Commands.compile(
        language,
        program,
        source,
        objectFile,
        listOf(Path.of("include")),
        emptyList(),
      ),
    )
  }

  @Test
  fun extensionsAndObjectiveCChangeTheDialect() {
    val language = cxx {
      compiler = CXXCompiler.GCC
      std = 3
      extensions = true
      objc = true
      files("main.mm")
    }
    val command = Commands.compile(language, program, source, objectFile, emptyList(), emptyList())
    assertTrue("-std=gnu++03" in command, command.toString())
    val x = command.indexOf("-x")
    assertEquals(listOf("-x", "objective-c++", source.toString()), command.subList(x, x + 3))
  }

  @Test
  fun anMsvcCompilerTakesSlashOptions() {
    val language = cxx {
      compiler = CXXCompiler.CL
      std = 23
      cli = true
      files("main.cpp")
      define("UNICODE" to null)
    }
    val command =
      Commands.compile(
        language,
        Path.of("cl.exe"),
        source,
        objectFile,
        listOf(Path.of("inc")),
        listOf("/W4"),
      )
    assertEquals(
      listOf(
        "cl.exe",
        "/nologo",
        "/std:c++latest",
        "/clr",
        "/W4",
        "/I",
        "inc",
        "/DUNICODE",
        "/c",
        "/Tp",
        source.toString(),
        "/Fo$objectFile",
      ),
      command,
    )
  }

  @Test
  fun quotesSurviveTheCommandLineOfWindows() {
    assertEquals(
      listOf("clang", "-DVERSION=\\\"3\\\"", "plain"),
      Commands.forWindows(listOf("clang", "-DVERSION=\"3\"", "plain")),
    )
    // Backslashes before a quote are doubled; others stay as they are.
    assertEquals(listOf("a\\\\\\\"b", "c\\d"), Commands.forWindows(listOf("a\\\"b", "c\\d")))
  }

  @Test
  fun msvcKnowsFewerStandards() {
    assertEquals("/std:c11", Commands.msvcStandard(Kind.C, 99))
    assertEquals("/std:c17", Commands.msvcStandard(Kind.C, 17))
    assertEquals("/std:clatest", Commands.msvcStandard(Kind.C, 23))
    assertEquals("/std:c++14", Commands.msvcStandard(Kind.CXX, 11))
    assertEquals("/std:c++20", Commands.msvcStandard(Kind.CXX, 20))
    assertEquals("/std:c++latest", Commands.msvcStandard(Kind.CXX, 26))
  }

  @Test
  fun linksWithLibraryDirectoriesAndLibraries() {
    assertEquals(
      listOf("clang++", "a.o", "b.o", "-o", "Demo", "-Llib", "-lz", "-pthread"),
      Commands.link(
        Flavor.GNU,
        Path.of("clang++"),
        listOf(Path.of("a.o"), Path.of("b.o")),
        Path.of("Demo"),
        listOf(Path.of("lib")),
        listOf("-lz"),
        listOf("-pthread"),
      ),
    )
    assertEquals(
      listOf("cl", "/nologo", "a.obj", "/FeDemo.exe", "/link", "/LIBPATH:lib", "zlib.lib"),
      Commands.link(
        Flavor.MSVC,
        Path.of("cl"),
        listOf(Path.of("a.obj")),
        Path.of("Demo.exe"),
        listOf(Path.of("lib")),
        listOf("zlib.lib"),
        emptyList(),
      ),
    )
  }

  @Test
  fun aBlockThatCannotBeBuiltIsRefusedWhenItIsWritten() {
    fun refused(block: () -> Unit) = assertFailsWith<IllegalArgumentException> { block() }.message
    assertEquals("cc.c names files and no compiler; set compiler", refused { c { files("a.c") } })
    assertEquals(
      "cc.c: extensions needs CLANG or GCC",
      refused {
        c {
          compiler = CCompiler.CL
          extensions = true
        }
      },
    )
    assertEquals(
      "cc.cxx: cli needs the compiler CL",
      refused {
        cxx {
          compiler = CXXCompiler.CLANGCL
          cli = true
        }
      },
    )
    assertEquals(
      "cc.c: '../outside.c' is not inside the project directory",
      refused {
        c {
          compiler = CCompiler.GCC
          files("../outside.c")
        }
      },
    )
    assertTrue(
      refused {
          c {
            compiler = CCompiler.GCC
            std = 20
          }
        }!!
        .startsWith("cc.c: std 20 is not a standard of C")
    )
    assertFailsWith<IllegalArgumentException> { CScope().define("not a macro" to null) }
  }

  @Test
  fun pkgConfigFilesOfConanAreReadWithoutPkgConfig() {
    val flags =
      PkgConfig.read(
        """
        # A file PkgConfigDeps writes.
        prefix=/home/conan/p/zlib
        includedir=${'$'}{prefix}/include
        libdir=${'$'}{prefix}/lib

        Name: zlib
        Cflags: -I${'$'}{includedir} -DZLIB_CONST
        Libs: -L${'$'}{libdir} -lz -pthread
        """
          .trimIndent()
      )
    assertEquals(listOf("-I/home/conan/p/zlib/include", "-DZLIB_CONST"), flags.cflags)
    assertEquals(listOf("-L/home/conan/p/zlib/lib", "-lz", "-pthread"), flags.libs)

    val gnu = PkgConfig.provided(flags.cflags, flags.libs, Flavor.GNU)
    assertEquals(listOf(Path.of("/home/conan/p/zlib/include")), gnu.includes)
    assertEquals(listOf(Path.of("/home/conan/p/zlib/lib")), gnu.libraryDirectories)
    assertEquals(listOf("-DZLIB_CONST"), gnu.compileFlags)
    assertEquals(listOf("-lz"), gnu.libraries)
    assertEquals(listOf("-pthread"), gnu.linkFlags)

    val msvc = PkgConfig.provided(flags.cflags, flags.libs, Flavor.MSVC)
    assertEquals(listOf("/DZLIB_CONST"), msvc.compileFlags)
    assertEquals(listOf("z.lib"), msvc.libraries)
    assertEquals(emptyList(), msvc.linkFlags)
  }

  @Test
  fun vcpkgTripletsFollowTheHost() {
    assertEquals("x64-windows", Triplets.vcpkg(Host(OperatingSystem.WINDOWS, Architecture.X86_64)))
    assertEquals("arm64-osx", Triplets.vcpkg(Host(OperatingSystem.MACOS, Architecture.AARCH64)))
    assertEquals("x64-linux", Triplets.vcpkg(Host(OperatingSystem.LINUX, Architecture.X86_64)))
  }

  @Test
  fun theDescriptorNamesThePluginAndItsClass() {
    val loader = javaClass.classLoader
    val descriptor = Properties()
    loader.getResourceAsStream("META-INF/tazel-plugin.properties")!!.use(descriptor::load)
    assertEquals("Progmasoft", descriptor.getProperty("publisher"))
    assertEquals("CC", descriptor.getProperty("name"))
    val service =
      loader
        .getResourceAsStream(
          "META-INF/services/com.progmasoft.api.tazel.dsl.plugin.ProjectPlugin"
        )!!
        .bufferedReader()
        .use { it.readText().trim() }
    assertEquals(CCPlugin::class.java.name, service)
  }
}
