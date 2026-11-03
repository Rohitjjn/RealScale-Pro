#include <algorithm>
#include <chrono>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

#include "cpu.h"
#include "net.h"

namespace {
bool read_ppm(const std::string& path, int& width, int& height,
              std::vector<unsigned char>& pixels) {
  std::ifstream input(path, std::ios::binary);
  std::string magic;
  int maximum = 0;
  if (!(input >> magic >> width >> height >> maximum) || magic != "P6" ||
      maximum != 255 || width < 1 || height < 1) {
    return false;
  }
  input.get();
  pixels.resize(static_cast<size_t>(width) * height * 3);
  return static_cast<bool>(input.read(reinterpret_cast<char*>(pixels.data()),
                                      pixels.size()));
}

bool write_ppm(const std::string& path, int width, int height,
               const std::vector<unsigned char>& pixels) {
  std::ofstream output(path, std::ios::binary);
  output << "P6\n" << width << ' ' << height << "\n255\n";
  output.write(reinterpret_cast<const char*>(pixels.data()), pixels.size());
  return static_cast<bool>(output);
}
}  // namespace

int main(int argc, char** argv) {
  if (argc != 5) {
    std::cerr << "usage: realscale_ncnn_spike MODEL.param MODEL.bin INPUT.ppm OUTPUT.ppm\n";
    return 2;
  }

  int width = 0;
  int height = 0;
  std::vector<unsigned char> pixels;
  if (!read_ppm(argv[3], width, height, pixels)) {
    std::cerr << "Could not read the input PPM image.\n";
    return 3;
  }

  ncnn::Net network;
  network.opt.num_threads = std::max(1, ncnn::get_big_cpu_count());
  network.opt.use_vulkan_compute = false;
  if (network.load_param(argv[1]) != 0 || network.load_model(argv[2]) != 0) {
    std::cerr << "Could not load the model files.\n";
    return 4;
  }

  ncnn::Mat image = ncnn::Mat::from_pixels(
      pixels.data(), ncnn::Mat::PIXEL_RGB, width, height);
  ncnn::Mat normalized;
  ncnn::copy_make_border(image, normalized, 7, 7, 7, 7,
                         ncnn::BORDER_REPLICATE, 0.f, network.opt);
  const float scale[3] = {1.f / 255.f, 1.f / 255.f, 1.f / 255.f};
  normalized.substract_mean_normalize(nullptr, scale);

  const auto started = std::chrono::steady_clock::now();
  ncnn::Extractor extractor = network.create_extractor();
  if (extractor.input("Input1", normalized) != 0) {
    std::cerr << "Model input is incompatible.\n";
    return 5;
  }
  ncnn::Mat result;
  if (extractor.extract("Eltwise4", result) != 0) {
    std::cerr << "Model output is incompatible.\n";
    return 6;
  }
  const auto ended = std::chrono::steady_clock::now();

  const float denormalize[3] = {255.f, 255.f, 255.f};
  result.substract_mean_normalize(nullptr, denormalize);
  std::vector<unsigned char> output(static_cast<size_t>(result.w) * result.h * 3);
  result.to_pixels(output.data(), ncnn::Mat::PIXEL_RGB);
  if (!write_ppm(argv[4], result.w, result.h, output)) {
    std::cerr << "Could not write the output image.\n";
    return 7;
  }

  const auto milliseconds =
      std::chrono::duration_cast<std::chrono::milliseconds>(ended - started).count();
  std::cout << "backend=cpu input=" << width << 'x' << height << " output="
            << result.w << 'x' << result.h << " inference_ms=" << milliseconds
            << " threads=" << network.opt.num_threads << '\n';
  return 0;
}
