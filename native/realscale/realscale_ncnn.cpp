#include <algorithm>
#include <atomic>
#include <cstdint>
#include <fstream>
#include <memory>
#include <string>
#include <vector>

#include "net.h"

#if defined(_WIN32)
#define REALSCALE_API __declspec(dllexport)
#else
#define REALSCALE_API __attribute__((visibility("default")))
#endif

namespace {
std::atomic<int> completed_tiles{0};
std::atomic<int> total_tiles{0};
std::atomic<bool> cancel_requested{false};

bool read_ppm(const char* path, int& width, int& height,
              std::vector<unsigned char>& pixels) {
  std::ifstream input(path, std::ios::binary);
  std::string magic;
  int maximum = 0;
  if (!(input >> magic >> width >> height >> maximum) || magic != "P6" ||
      maximum != 255 || width < 64 || height < 64) {
    return false;
  }
  input.get();
  const uint64_t input_bytes = static_cast<uint64_t>(width) * height * 3;
  if (input_bytes > 128ULL * 1024ULL * 1024ULL) return false;
  pixels.resize(static_cast<size_t>(input_bytes));
  return static_cast<bool>(input.read(reinterpret_cast<char*>(pixels.data()),
                                      pixels.size()));
}

bool write_ppm(const char* path, int width, int height,
               const std::vector<unsigned char>& pixels) {
  std::ofstream output(path, std::ios::binary);
  output << "P6\n" << width << ' ' << height << "\n255\n";
  output.write(reinterpret_cast<const char*>(pixels.data()), pixels.size());
  return static_cast<bool>(output);
}
}  // namespace

extern "C" {
REALSCALE_API int realscale_progress() { return completed_tiles.load(); }
REALSCALE_API int realscale_total() { return total_tiles.load(); }
REALSCALE_API void realscale_cancel() { cancel_requested.store(true); }

// Return values: 0 success, 1 cancelled, 2 input, 3 model, 4 inference, 5 output.
REALSCALE_API int realscale_upscale(const char* parameter_path,
                                    const char* model_path,
                                    const char* input_path,
                                    const char* output_path,
                                    int tile_size,
                                    int thread_count) {
  cancel_requested.store(false);
  completed_tiles.store(0);
  int width = 0;
  int height = 0;
  std::vector<unsigned char> input;
  if (!read_ppm(input_path, width, height, input)) return 2;

  ncnn::Net network;
  network.opt.num_threads = std::max(1, thread_count);
  network.opt.use_vulkan_compute = false;
  if (network.load_param(parameter_path) != 0 ||
      network.load_model(model_path) != 0) {
    return 3;
  }

  tile_size = std::max(64, tile_size);
  const int across = (width + tile_size - 1) / tile_size;
  const int down = (height + tile_size - 1) / tile_size;
  total_tiles.store(across * down);
  const uint64_t output_bytes =
      static_cast<uint64_t>(width) * 2 * height * 2 * 3;
  if (output_bytes > 256ULL * 1024ULL * 1024ULL) return 6;
  std::vector<unsigned char> output(static_cast<size_t>(output_bytes));

  for (int tile_y = 0; tile_y < down; ++tile_y) {
    for (int tile_x = 0; tile_x < across; ++tile_x) {
      if (cancel_requested.load()) return 1;
      const int x = tile_x * tile_size;
      const int y = tile_y * tile_size;
      const int tile_width = std::min(tile_size, width - x);
      const int tile_height = std::min(tile_size, height - y);
      ncnn::Mat tile = ncnn::Mat::from_pixels_roi(
          input.data(), ncnn::Mat::PIXEL_RGB, width, height, x, y,
          tile_width, tile_height);
      ncnn::Mat padded;
      ncnn::copy_make_border(tile, padded, 7, 7, 7, 7,
                             ncnn::BORDER_REPLICATE, 0.f, network.opt);
      const float normalize[3] = {1.f / 255.f, 1.f / 255.f, 1.f / 255.f};
      padded.substract_mean_normalize(nullptr, normalize);
      ncnn::Extractor extractor = network.create_extractor();
      if (extractor.input("Input1", padded) != 0) return 4;
      ncnn::Mat result;
      if (extractor.extract("Eltwise4", result) != 0) return 4;
      const float denormalize[3] = {255.f, 255.f, 255.f};
      result.substract_mean_normalize(nullptr, denormalize);
      std::vector<unsigned char> tile_output(
          static_cast<size_t>(result.w) * result.h * 3);
      result.to_pixels(tile_output.data(), ncnn::Mat::PIXEL_RGB);
      const int copy_width = std::min(result.w, tile_width * 2);
      const int copy_height = std::min(result.h, tile_height * 2);
      for (int row = 0; row < copy_height; ++row) {
        const size_t source_offset = static_cast<size_t>(row) * result.w * 3;
        const size_t destination_offset =
            (static_cast<size_t>(y * 2 + row) * width * 2 + x * 2) * 3;
        std::copy_n(tile_output.data() + source_offset,
                    static_cast<size_t>(copy_width) * 3,
                    output.data() + destination_offset);
      }
      completed_tiles.fetch_add(1);
    }
  }
  return write_ppm(output_path, width * 2, height * 2, output) ? 0 : 5;
}
}  // extern "C"
