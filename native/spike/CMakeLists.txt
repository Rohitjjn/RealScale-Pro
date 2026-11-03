cmake_minimum_required(VERSION 3.20)
project(realscale_ncnn_spike LANGUAGES CXX)

include(FetchContent)
set(NCNN_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(NCNN_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(NCNN_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
set(NCNN_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(NCNN_VULKAN OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
  ncnn
  GIT_REPOSITORY https://github.com/Tencent/ncnn.git
  GIT_TAG 6125c9f47cd14b589de0521350668cf9d3d37e3c
  GIT_SHALLOW FALSE
)
FetchContent_MakeAvailable(ncnn)

add_executable(realscale_ncnn_spike main.cpp)
target_compile_features(realscale_ncnn_spike PRIVATE cxx_std_17)
target_link_libraries(realscale_ncnn_spike PRIVATE ncnn)
