cmake_minimum_required(VERSION 3.18)
project(realscale_ncnn LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

option(ENABLE_VULKAN "Enable ncnn Vulkan GPU backend when available" ON)
option(BUILD_SMOKE_TEST "Build native CLI smoke test binary" OFF)

if(NOT DEFINED NCNN_DIR OR "${NCNN_DIR}" STREQUAL "")
  if(ANDROID AND EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/third_party/ncnn-android")
    set(NCNN_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/ncnn-android")
  elseif(UNIX AND NOT APPLE AND EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/third_party/ncnn-linux")
    set(NCNN_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/ncnn-linux")
  elseif(WIN32 AND EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/third_party/ncnn-windows")
    set(NCNN_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/ncnn-windows")
  endif()
endif()

if(NOT DEFINED NCNN_DIR OR "${NCNN_DIR}" STREQUAL "")
  message(FATAL_ERROR
    "NCNN_DIR not set and no prebuilt ncnn found under third_party/. "
    "Run: bash native/fetch_ncnn.sh <android|linux|windows>")
endif()

# --- Locate ncnn ---------------------------------------------------------------
# Prebuilt Android archives place the CMake package under <abi>/lib/cmake/ncnn,
# desktop archives under lib/cmake/ncnn.
set(NCNN_CMAKE_DIR "")
foreach(_cand
    "${NCNN_DIR}/${ANDROID_ABI}/lib/cmake/ncnn"
    "${NCNN_DIR}/${CMAKE_ANDROID_ARCH_ABI}/lib/cmake/ncnn"
    "${NCNN_DIR}/lib/cmake/ncnn")
  if(EXISTS "${_cand}" AND NCNN_CMAKE_DIR STREQUAL "")
    set(NCNN_CMAKE_DIR "${_cand}")
  endif()
endforeach()

# Header location: prebuilt Android archives put headers under <abi>/include,
# desktop archives under include/. Added to our own target explicitly so the
# build never depends on a package config's baked-in (and often stale) paths.
#
# The shim includes <net.h> / <gpu.h> exactly the way ncnn's own config does,
# so the `include/ncnn` directory is the one that must be on the search path.
# The plain `include` directory is added too so <ncnn/net.h> also resolves.
set(NCNN_INCLUDE_BASE "")
foreach(_cand
    "${NCNN_DIR}/${ANDROID_ABI}/include"
    "${NCNN_DIR}/${CMAKE_ANDROID_ARCH_ABI}/include"
    "${NCNN_DIR}/include")
  if(EXISTS "${_cand}" AND NCNN_INCLUDE_BASE STREQUAL "")
    set(NCNN_INCLUDE_BASE "${_cand}")
  endif()
endforeach()
if(EXISTS "${NCNN_INCLUDE_BASE}/ncnn/net.h")
  set(NCNN_INCLUDE_DIR "${NCNN_INCLUDE_BASE}/ncnn")
else()
  set(NCNN_INCLUDE_DIR "${NCNN_INCLUDE_BASE}")
endif()
message(STATUS "ncnn headers: ${NCNN_INCLUDE_DIR}")

set(NCNN_HAVE_PACKAGE FALSE)
if(NOT NCNN_CMAKE_DIR STREQUAL "")
  # QUIET (not REQUIRED): some prebuilt configs fail their own dependency
  # probe (a Vulkan-enabled ncnn whose Vulkan lookup misses on Android). In
  # that case we fall back to importing the archive by hand below instead of
  # breaking the whole APK build.
  find_package(ncnn QUIET PATHS "${NCNN_CMAKE_DIR}" NO_DEFAULT_PATH)
  if(TARGET ncnn)
    set(NCNN_HAVE_PACKAGE TRUE)
  elseif(TARGET ncnn::ncnn)
    # Namespaced export: bridge it to the plain name the shim links against.
    add_library(ncnn INTERFACE IMPORTED GLOBAL)
    set_target_properties(ncnn PROPERTIES
      INTERFACE_LINK_LIBRARIES ncnn::ncnn)
    set(NCNN_HAVE_PACKAGE TRUE)
  endif()
endif()

if(NOT NCNN_HAVE_PACKAGE)
  set(NCNN_LIBRARY "")
  foreach(_cand
      "${NCNN_DIR}/${ANDROID_ABI}/lib/libncnn.a"
      "${NCNN_DIR}/${CMAKE_ANDROID_ARCH_ABI}/lib/libncnn.a"
      "${NCNN_DIR}/lib/libncnn.a"
      "${NCNN_DIR}/${ANDROID_ABI}/lib/libncnn.so"
      "${NCNN_DIR}/lib/libncnn.so")
    if(EXISTS "${_cand}" AND NCNN_LIBRARY STREQUAL "")
      set(NCNN_LIBRARY "${_cand}")
    endif()
  endforeach()
  if(NCNN_LIBRARY STREQUAL "")
    message(FATAL_ERROR
      "Could not locate an ncnn CMake package or library under ${NCNN_DIR}. "
      "Run: bash native/fetch_ncnn.sh <android|linux|windows>")
  endif()

  message(STATUS "ncnn: manual import ${NCNN_LIBRARY}")

  # System libs the prebuilt ncnn was linked against.
  set(NCNN_EXTRA_LIBS "")
  find_package(Threads QUIET)
  if(TARGET Threads::Threads)
    list(APPEND NCNN_EXTRA_LIBS Threads::Threads)
  endif()
  find_library(_ncnn_z z)
  if(_ncnn_z)
    list(APPEND NCNN_EXTRA_LIBS "${_ncnn_z}")
  endif()
  if(ANDROID)
    list(APPEND NCNN_EXTRA_LIBS log android jnigraphics)
    if(ENABLE_VULKAN)
      # NDK stub: present from API 24, which is our minSdk (26).
      find_library(_ncnn_vulkan vulkan)
      if(_ncnn_vulkan)
        list(APPEND NCNN_EXTRA_LIBS "${_ncnn_vulkan}")
      endif()
    endif()
  endif()

  # A Vulkan-enabled ncnn is statically linked against glslang + SPIRV-Tools.
  # Those archives ship next to libncnn.a, so pick up every one of them in
  # dependency order (glslang pulls symbols from its own helpers).
  set(_ncnn_lib_dir "")
  foreach(_cand
      "${NCNN_DIR}/${ANDROID_ABI}/lib"
      "${NCNN_DIR}/${CMAKE_ANDROID_ARCH_ABI}/lib"
      "${NCNN_DIR}/lib")
    if(EXISTS "${_cand}" AND _ncnn_lib_dir STREQUAL "")
      set(_ncnn_lib_dir "${_cand}")
    endif()
  endforeach()
  foreach(_name
      SPIRV-Tools-opt
      SPIRV-Tools
      SPIRV
      glslang
      OGLCompiler
      OSDependent
      MachineIndependent
      GenericCodeGen)
    if(EXISTS "${_ncnn_lib_dir}/lib${_name}.a")
      list(APPEND NCNN_EXTRA_LIBS "${_ncnn_lib_dir}/lib${_name}.a")
    endif()
  endforeach()
  # Anything else that ships alongside (newer ncnn splits libs differently).
  file(GLOB _ncnn_other_archives "${_ncnn_lib_dir}/*.a")
  foreach(_a IN LISTS _ncnn_other_archives)
    get_filename_component(_n "${_a}" NAME_WE)
    if(NOT _n STREQUAL "ncnn" AND NOT "${_a}" IN_LIST NCNN_EXTRA_LIBS)
      list(APPEND NCNN_EXTRA_LIBS "${_a}")
    endif()
  endforeach()

  add_library(ncnn STATIC IMPORTED GLOBAL)
  set_target_properties(ncnn PROPERTIES
    IMPORTED_LOCATION "${NCNN_LIBRARY}"
    INTERFACE_LINK_LIBRARIES "${NCNN_EXTRA_LIBS}"
    INTERFACE_COMPILE_FEATURES "cxx_std_11")
else()
  message(STATUS "ncnn: CMake package at ${NCNN_CMAKE_DIR}")
endif()

file(
  APPEND "${CMAKE_CURRENT_BINARY_DIR}/ncnn_report.txt"
  "NCNN_HAVE_PACKAGE=${NCNN_HAVE_PACKAGE}\n"
  "NCNN_INCLUDE_DIR=${NCNN_INCLUDE_DIR}\n"
  "NCNN_LIBRARY=${NCNN_LIBRARY}\n"
  "NCNN_EXTRA_LIBS=${NCNN_EXTRA_LIBS}\n"
)

add_library(realscale_ncnn SHARED realscale_ncnn.cpp)
target_link_libraries(realscale_ncnn PRIVATE ncnn)
if(NOT NCNN_INCLUDE_DIR STREQUAL "")
  target_include_directories(realscale_ncnn PRIVATE "${NCNN_INCLUDE_DIR}")
endif()
if(NOT NCNN_INCLUDE_BASE STREQUAL "")
  target_include_directories(realscale_ncnn PRIVATE "${NCNN_INCLUDE_BASE}")
endif()

if(ENABLE_VULKAN)
  target_compile_definitions(realscale_ncnn PRIVATE RS_ENABLE_VULKAN=1)
else()
  target_compile_definitions(realscale_ncnn PRIVATE RS_ENABLE_VULKAN=0)
endif()

if(ANDROID)
  target_link_libraries(realscale_ncnn PRIVATE log android)
  # Link OpenMP statically so the APK needs no standalone libomp.so at
  # runtime, and align ELF segments to 16 KB for Android 15/16 devices.
  target_link_options(realscale_ncnn PRIVATE
    "-fopenmp"
    "-static-openmp"
    "-Wl,-z,max-page-size=16384"
  )
endif()

set_target_properties(realscale_ncnn PROPERTIES
  OUTPUT_NAME "realscale_ncnn"
  C_VISIBILITY_PRESET hidden
  CXX_VISIBILITY_PRESET hidden
)

if(BUILD_SMOKE_TEST AND NOT ANDROID)
  add_executable(realscale_ncnn_smoke smoke_test.cpp)
  target_link_libraries(realscale_ncnn_smoke PRIVATE realscale_ncnn)
endif()
