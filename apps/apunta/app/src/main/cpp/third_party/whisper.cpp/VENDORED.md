# whisper.cpp (vendored)

- Upstream: https://github.com/ggml-org/whisper.cpp
- Version: **1.8.5** (MIT License, see `LICENSE`)
- What is included: `CMakeLists.txt`, `cmake/`, `include/`, `src/` (without CoreML/OpenVINO)
  and `ggml/` with only the CPU backend. GPU/NPU backends (CUDA, Vulkan, Metal,
  OpenCL, SYCL, Hexagon, ...) were removed because Apunta only uses the CPU.
- No source file was modified.

To update: replace this folder with the same subset of a newer release and run the
app's CI (`.github/workflows/apunta-android.yml`).
