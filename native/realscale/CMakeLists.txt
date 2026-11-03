cmake_minimum_required(VERSION 3.20)
project(realscale_ncnn LANGUAGES CXX)

set(CMAKE_POLICY_VERSION_MINIMUM 3.5 CACHE STRING "Minimum dependency policy" FORCE)
include(FetchContent)
set(NCNN_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(NCNN_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(NCNN_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
set(NCNN_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(NCNN_RUNTIME_CPU OFF CACHE BOOL "" FORCE)
set(NCNN_VULKAN OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
  ncnn
  GIT_REPOSITORY https://github.com/Tencent/ncnn.git
  GIT_TAG 6125c9f47cd14b589de0521350668cf9d3d37e3c
  GIT_SHALLOW FALSE
)
FetchContent_MakeAvailable(ncnn)
if(MSVC)
  target_compile_options(ncnn PRIVATE /wd4244 /wd4267)
endif()

add_library(realscale_ncnn SHARED realscale_ncnn.cpp)
target_compile_features(realscale_ncnn PRIVATE cxx_std_17)
target_link_libraries(realscale_ncnn PRIVATE ncnn)
target_compile_definitions(realscale_ncnn PRIVATE REALSCALE_EXPORTS)
