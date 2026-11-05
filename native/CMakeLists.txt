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

if(ANDROID AND EXISTS "${NCNN_DIR}/${ANDROID_ABI}/lib/cmake/ncnn")
  set(NCNN_CMAKE_DIR "${NCNN_DIR}/${ANDROID_ABI}/lib/cmake/ncnn")
else()
  set(NCNN_CMAKE_DIR "${NCNN_DIR}/lib/cmake/ncnn")
endif()

find_package(ncnn REQUIRED PATHS "${NCNN_CMAKE_DIR}" NO_DEFAULT_PATH)

add_library(realscale_ncnn SHARED realscale_ncnn.cpp)
target_link_libraries(realscale_ncnn PRIVATE ncnn)

if(ENABLE_VULKAN)
  target_compile_definitions(realscale_ncnn PRIVATE RS_ENABLE_VULKAN=1)
else()
  target_compile_definitions(realscale_ncnn PRIVATE RS_ENABLE_VULKAN=0)
endif()

if(ANDROID)
  target_link_libraries(realscale_ncnn PRIVATE log android)
  # Link OpenMP statically so APK needs no standalone libomp.so at runtime,
  # and align ELF segments to 16 KB for Android 15/16 devices.
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
