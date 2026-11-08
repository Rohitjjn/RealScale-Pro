# RealScale-Pro — native engine CMake build.
#
# Targets:
#   librealscale_native   — shared library loaded by Flutter through FFI
#                           (Android: bundled into the APK; Linux: used
#                           by flutter_test integration tests).
#   rscli                 — host-only CLI harness (real end-to-end runs).
#   rs_native_tests       — host-only native unit tests.
#
# Options:
#   RS_BUILD_NCNN (ON)  — build the real ncnn inference engine. When
#                         OFF the ABI still builds but rs_run returns
#                         RS_ERR_UNSUPPORTED (interface-only builds).
#   RS_NCNN_DIR          — path to an ncnn source tree or a directory
#                          containing an installed ncnn (with
#                          lib/cmake/ncnn). Defaults to the pinned
#                          checkout fetched by tools/fetch_ncnn.sh.
#   RS_BUILD_TESTS (host only) — build the native test binaries.
#   RS_BUILD_CLI  (host only) — build rscli.
#   RS_TEST_HOOKS (host tests only) — enable failure-injection hooks.
#
# Copyright 2026 RealScale-Pro contributors. MIT License.
cmake_minimum_required(VERSION 3.16)

project(realscale_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

option(RS_BUILD_NCNN "Build the ncnn inference engine" ON)
option(RS_REQUIRE_NCNN "Fail the configure step when ncnn cannot be found (Android release builds set this from Gradle)" OFF)
option(RS_BUILD_TESTS "Build native tests (host only)" OFF)
option(RS_BUILD_CLI "Build the rscli harness (host only)" OFF)
option(RS_TEST_HOOKS "Enable test failure-injection hooks" OFF)
option(RS_NCNN_VULKAN "The linked ncnn was built with Vulkan support" OFF)
set(RS_NCNN_DIR "" CACHE PATH "ncnn source or install directory")
set(RS_NCNN_ANDROID_DIR "" CACHE PATH "ncnn-android prebuilt tree (per-ABI subdirs)")

set(RS_CORE_SOURCES
  src/tile_layout.cpp
  src/feather_blend.cpp
  src/error.cpp
  src/abi.cpp
)

if(RS_BUILD_NCNN)
  list(APPEND RS_CORE_SOURCES src/engine_ncnn.cpp)

  # Locate ncnn, in priority order:
  #   1. RS_NCNN_LIB + RS_NCNN_INCLUDE — explicit prebuilt static lib
  #      (fast; used by the host verification build and by Android).
  #   2. RS_NCNN_DIR with an install tree (lib/cmake/ncnn).
  #   3. RS_NCNN_DIR as a source tree -> subordinate build (slow, last
  #      resort; never rebuilds when a prebuilt lib is available).
  set(RS_NCNN_LIB "" CACHE FILEPATH "path to a prebuilt libncnn.a")
  set(RS_NCNN_INCLUDE "" CACHE PATH "path to the ncnn src/ (contains net.h)")

  # Android: derive the per-ABI prebuilt paths from RS_NCNN_ANDROID_DIR.
  # The official ncnn android prebuilts ship as
  #   <dir>/<abi>/lib/libncnn.a (+ glslang/SPIRV family for Vulkan)
  #   <dir>/<abi>/include/ncnn/*.h
  # We link the raw archives instead of the imported CMake target because
  # that target propagates -fno-exceptions/-fno-rtti to consumers, which
  # our exception-based recovery engine cannot tolerate.
  if(NOT RS_NCNN_LIB AND RS_NCNN_ANDROID_DIR AND ANDROID)
    set(_rs_android_abi "${CMAKE_ANDROID_ARCH_ABI}")
    if(NOT _rs_android_abi)
      set(_rs_android_abi "$ENV{ANDROID_ABI}")
    endif()
    set(_rs_ncnn_prefix "${RS_NCNN_ANDROID_DIR}/${_rs_android_abi}")
    if(EXISTS "${_rs_ncnn_prefix}/lib/libncnn.a")
      set(RS_NCNN_LIB "${_rs_ncnn_prefix}/lib/libncnn.a")
      # The official prebuilts keep the headers in <abi>/include/ncnn, a plain
      # install tree in <abi>/include. Support both instead of failing with
      # "net.h not found".
      if(EXISTS "${_rs_ncnn_prefix}/include/ncnn/net.h")
        set(RS_NCNN_INCLUDE "${_rs_ncnn_prefix}/include/ncnn")
      else()
        set(RS_NCNN_INCLUDE "${_rs_ncnn_prefix}/include")
      endif()
      # Vulkan builds also need the glslang/SPIRV static family. Link every
      # archive that ships next to libncnn.a (newer releases split/rename
      # them) except libncnn.a itself, which is already handled above.
      file(GLOB _rs_ncnn_extra "${_rs_ncnn_prefix}/lib/*.a")
      set(RS_NCNN_VULKAN_EXTRA_LIBS "")
      foreach(_rs_a IN LISTS _rs_ncnn_extra)
        get_filename_component(_rs_n "${_rs_a}" NAME)
        if(NOT _rs_n STREQUAL "libncnn.a")
          list(APPEND RS_NCNN_VULKAN_EXTRA_LIBS "${_rs_a}")
        endif()
      endforeach()
      if(RS_NCNN_VULKAN_EXTRA_LIBS)
        set(RS_NCNN_VULKAN ON)
      endif()
      message(STATUS "realscale_native: android ncnn prebuilt (${_rs_android_abi})")
    else()
      message(STATUS
        "realscale_native: no ncnn prebuilt for ${_rs_android_abi} under "
        "${RS_NCNN_ANDROID_DIR} — this ABI is built interface-only")
    endif()
  endif()

  if(RS_NCNN_LIB AND RS_NCNN_INCLUDE AND EXISTS "${RS_NCNN_LIB}")
    set(RS_NCNN_LIBRARIES "${RS_NCNN_LIB}")
    set(RS_NCNN_INCLUDE_DIRS "${RS_NCNN_INCLUDE}")
    set(RS_HAS_NCNN ON)
    message(STATUS "realscale_native: using prebuilt ncnn: ${RS_NCNN_LIB}")
  else()
    set(_ncnn_root "${RS_NCNN_DIR}")
    if(NOT _ncnn_root)
      get_filename_component(_default_root
        "${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/ncnn" ABSOLUTE)
      set(_ncnn_root "${_default_root}")
    endif()
    find_package(ncnn QUIET PATHS "${_ncnn_root}" "${_ncnn_root}/install"
                 "${_ncnn_root}/build/install" NO_DEFAULT_PATH)
    if(ncnn_FOUND)
      set(RS_NCNN_LIBRARIES ncnn)
      set(RS_NCNN_INCLUDE_DIRS "")
      set(RS_HAS_NCNN ON)
      message(STATUS "realscale_native: using installed ncnn config")
    elseif(EXISTS "${_ncnn_root}/CMakeLists.txt")
      set(NCNN_VULKAN OFF CACHE BOOL "" FORCE)
      set(NCNN_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
      set(NCNN_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
      set(NCNN_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
      set(NCNN_PYTHON_TOOLS OFF CACHE BOOL "" FORCE)
      set(NCNN_SHARED_LIB OFF CACHE BOOL "" FORCE)
      add_subdirectory("${_ncnn_root}" "${CMAKE_BINARY_DIR}/ncnn-build"
        EXCLUDE_FROM_ALL)
      set(RS_NCNN_LIBRARIES ncnn)
      set(RS_NCNN_INCLUDE_DIRS "${_ncnn_root}/src")
      set(RS_HAS_NCNN ON)
    else()
      message(WARNING
        "ncnn not found at ${_ncnn_root} — building interface-only engine.")
      set(RS_HAS_NCNN OFF)
    endif()
  endif()
endif()

if(NOT RS_HAS_NCNN AND RS_BUILD_NCNN)
  if(RS_REQUIRE_NCNN)
    message(FATAL_ERROR
      "ncnn was requested (RS_REQUIRE_NCNN=ON) but could not be located. "
      "Fetch the pinned prebuilts with `bash native/fetch_ncnn.sh android` "
      "(or host ncnn with `bash native/fetch_ncnn.sh linux`) and re-run.")
  endif()
  list(APPEND RS_CORE_SOURCES src/engine_stub.cpp)
endif()

add_library(realscale_native SHARED ${RS_CORE_SOURCES})
target_include_directories(realscale_native
  PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/include"
  PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/src")
target_compile_definitions(realscale_native PRIVATE RS_ABI_VERSION_DEF=1)

if(RS_HAS_NCNN)
  target_include_directories(realscale_native PRIVATE ${RS_NCNN_INCLUDE_DIRS})
  if(RS_NCNN_VULKAN_EXTRA_LIBS)
    # ncnn + its glslang/SPIRV archives reference each other in both
    # directions, and static archives are resolved left-to-right exactly once.
    # Wrapping the whole set in a linker group makes the order irrelevant
    # (and survives ncnn releases that rename or re-split those archives).
    target_link_libraries(realscale_native PRIVATE
      -Wl,--start-group
      ${RS_NCNN_LIBRARIES}
      ${RS_NCNN_VULKAN_EXTRA_LIBS}
      -Wl,--end-group)
  else()
    target_link_libraries(realscale_native PRIVATE ${RS_NCNN_LIBRARIES})
  endif()
  if(RS_NCNN_VULKAN)
    # Enables the engine's Vulkan code paths (GPU probe + optional GPU
    # compute with CPU fallback). The linked ncnn must be a Vulkan build.
    target_compile_definitions(realscale_native PRIVATE NCNN_VULKAN=1)
  endif()
  # ncnn host builds may be compiled with OpenMP (official Android
  # prebuilts are); link it only when present.
  find_package(OpenMP QUIET)
  if(OpenMP_FOUND)
    target_link_libraries(realscale_native PRIVATE OpenMP::OpenMP_CXX)
  endif()
  target_compile_definitions(realscale_native PRIVATE RS_HAS_NCNN=1)
  if(RS_BUILD_TESTS)
    # Test builds compile the failure-injection hooks into the shared
    # library so recovery paths can be exercised. Production Android
    # builds never set RS_BUILD_TESTS.
    target_compile_definitions(realscale_native PRIVATE RS_TEST_HOOKS=1)
  endif()
else()
  # Interface-only fallback: the ABI reports the engine as unsupported.
  message(STATUS "realscale_native: interface-only build (no ncnn)")
endif()

if(ANDROID)
  target_link_libraries(realscale_native PRIVATE log android jnigraphics dl)
  # ncnn android prebuilts are compiled with OpenMP; the NDK only ships
  # the static runtime, so link it statically (no libomp.so at runtime).
  # max-page-size=16384 aligns the ELF segments for Android 15/16 devices
  # with 16 KB pages (required by Google Play from November 2025 for
  # apps targeting Android 15+).
  target_link_options(realscale_native PRIVATE
    -fopenmp -static-openmp
    -Wl,-z,max-page-size=16384)
  set_target_properties(realscale_native PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
endif()

# ------------------------------------------------------------------ #
# Host-only extras.
# ------------------------------------------------------------------ #
if(NOT ANDROID)
  if(RS_BUILD_CLI)
    add_executable(rscli tools/rscli.cpp)
    target_link_libraries(rscli PRIVATE realscale_native)
    target_include_directories(rscli PRIVATE
      "${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/stb")
  endif()

  if(RS_BUILD_TESTS)
    set(RS_STB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/stb")
    foreach(t test_tile_layout test_feather_blend test_engine)
      add_executable(${t} tests/${t}.cpp)
      target_include_directories(${t} PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/src" ${RS_STB_DIR})
      if(t STREQUAL "test_engine")
        target_link_libraries(${t} PRIVATE realscale_native)
        target_compile_definitions(${t} PRIVATE RS_TEST_HOOKS=1)
      else()
        target_sources(${t} PRIVATE src/tile_layout.cpp src/feather_blend.cpp)
      endif()
    endforeach()
  endif()
endif()
