<!-- SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com> -->
<!-- SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-2.0 -->

# Tazel plugins of Progmasoft

The plugins Progmasoft publishes for the project file of [Tazel](https://github.com/Progmasoft/tazel), the build system of
Tala. They are on ViGet, from where Tazel downloads them:

| Plugin | Block | What it builds |
| --- | --- | --- |
| [Progmasoft.CC](https://viget.progmasoft.com/dslplugins/tazel/Progmasoft/CC) | `cc { }` | C and C++ sources, with Clang, GCC, MSVC or clang-cl |
| [Progmasoft.CMake](https://viget.progmasoft.com/dslplugins/tazel/Progmasoft/CMake) | `cmake { }` | a CMake project of the project directory |

## CC

```kotlin
plugins {
  plugin("Progmasoft") {
    name = "CC"
    version = "0.1.1"
  }
}

project { name = "Greeter" }

cc {
  enabled = true // false unless set

  c {
    compiler = CCompiler.CLANG // CLANG, CL, CLANGCL or GCC; required when the block has files
    std = 23 // the default of the compiler unless set
    // extensions = true      // the GNU dialect; needs CLANG or GCC
    // objc = true            // Objective-C; needs CLANG or GCC
    flags("-Wall", "-Wextra")
    flagsIfSupported("-Wshadow") // passed only when the compiler accepts it
    files("src/greeting.c")
    include("include") // -I include
    define("DEBUG" to null, "VERSION" to "3") // -DDEBUG -DVERSION=3
    packageManager = PackageManager.VCPKG // VCPKG, CONAN or NONE (the default)
  }

  cxx {
    compiler = CXXCompiler.CLANG
    std = 23
    // cli = true             // C++/CLI; needs CL
    files("src/main.cpp")
    include("include")
  }
}
```

`tazel build` compiles every file of both blocks and links them into one executable, named after the project, in the
output directory of the build mode (`build/debug` or `build/release`). C++ objects are linked with the C++ driver. A
`CMakeLists.txt` plays no part in it.

- With `CL` or `CLANGCL`, options are written the MSVC way (`/W4`), and `std` maps to the nearest `/std:` MSVC knows.
- A flag of `flagsIfSupported` is tried once on an empty program, with warnings as errors, and passed only when the
  compiler accepts it; the probe prints nothing.
- `PackageManager.VCPKG` runs `vcpkg install` in manifest mode (`vcpkg.json` beside the project file) into the build
  directory, and compiles and links with what it installed; its DLLs are copied beside the executable. vcpkg is taken
  from `PATH` or `VCPKG_ROOT`.
- `PackageManager.CONAN` runs `conan install` with the `PkgConfigDeps` generator (`conanfile.txt` or `conanfile.py`
  beside the project file) and compiles and links with the flags of the `.pc` files it writes.

## CMake

```kotlin
plugins {
  plugin("Progmasoft") {
    name = "CMake"
    version = "0.1.1"
  }
}

cmake {
  enabled = true // false unless set; a CMakeLists.txt must lie beside the project file
  define("ENABLE_SIMD" to "ON", "BUILD_TESTS" to "OFF") // -DENABLE_SIMD=ON -DBUILD_TESTS=OFF
}
```

`tazel build` configures the project into a build directory of its own below `.tazel/build`, with `CMAKE_BUILD_TYPE`
set to the build mode, and builds it. Executables and libraries are written to the output directory of the build mode;
a definition of the project file replaces any of these settings. CMake's own `CMAKE_GENERATOR` environment variable
chooses the generator, for example `Ninja`.

## Building

Each plugin is built by the Gradle plugin `com.progmasoft.tazel.plugin`, which takes the Tazel API from
`https://maven.progmasoft.com/tazel` and writes the descriptor of the plugin from its `tazelPlugin { }` block.

```sh
./gradlew check
```

`-PtazelCheckout=../tazel` builds against a checkout of Tazel instead of the released Gradle plugin and API.

## License

MPL-2.0 with the Progmasoft exception; see [LICENSE.txt](LICENSE.txt).
