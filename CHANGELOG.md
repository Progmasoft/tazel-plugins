<!-- SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com> -->
<!-- SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-2.0 -->

# Changelog

Each plugin has a version of its own; a section names the plugin and its version.

## CC 0.1.0 and CMake 0.1.0 - 2026-10-29

The first plugins, for Tazel 0.2.0 and plugin API 2.

- CC: the `cc { }` block with `c { }` and `cxx { }`: Clang, GCC, MSVC and clang-cl, standards, the GNU dialects,
  Objective-C, C++/CLI, flags and probed flags, includes, macros, and vcpkg or Conan for dependencies. One executable,
  named after the project, in the output directory.
- CMake: the `cmake { }` block: configure and build in the build mode of the project, with cache variables from
  `define`, and outputs in the output directory.
