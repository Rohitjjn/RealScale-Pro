# RealScale-Pro native engine shim (ncnn).
# Links against a prebuilt ncnn fetched by fetch_ncnn.sh (see scripts).
# Android: wired through android/app/build.gradle externalNativeBuild.
# Windows: cmake -B build -A x64 && cmake --build build --config Release
cmake_minimum_required(VERSION 3.14)
project(realscale_ncnn LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# NCNN_DIR must point at an extracted ncnn release (prebuilt or built).
if(NOT NCNN_DIR)
  message(FATAL_ERROR "Set -DNCNN_DIR=/path/to/ncnn (with lib/cmake/ncnn)")
endif()

find_package(ncnn REQUIRED PATHS ${NCNN_DIR}/lib/cmake/ncnn NO_DEFAULT_PATH)

add_library(realscale_ncnn SHARED realscale_ncnn.cpp)

target_link_libraries(realscale_ncnn PRIVATE ncnn)

if(ANDROID)
  target_link_libraries(realscale_ncnn PRIVATE android log)
  # Ship Vulkan support on Android; ncnn falls back to CPU automatically
  # when no Vulkan device is usable.
  target_compile_definitions(realscale_ncnn PRIVATE RS_ANDROID=1)
endif()

if(WIN32)
  set_target_properties(realscale_ncnn PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY_RELEASE ${CMAKE_BINARY_DIR}
    OUTPUT_NAME "realscale_ncnn")
endif()

target_compile_options(realscale_ncnn PRIVATE -O2)
