/*
 * RealScale-Pro native engine — stable C ABI.
 *
 * This header is the ONLY contract between Flutter/Dart (via dart:ffi) and
 * the native C++ implementation. It deliberately exposes:
 *   - opaque handles (no C++ types cross the boundary),
 *   - plain C functions with C linkage,
 *   - POD structs with fixed-width fields and explicit padding policy
 *     (all structs use int32_t/int64_t/double/pointers; reserved[] fields
 *     keep the layout forward-compatible),
 *   - caller-owned input buffers and native-owned output buffers.
 *
 * ABI version history:
 *   1 — initial (Prompt 1 plan)
 *   2 — progress struct + recovery-attempt reporting (Prompt 2)
 *
 * Copyright 2026 RealScale-Pro contributors. MIT License (see LICENSES).
 */
#ifndef REALESCALE_REALESCALE_H
#define REALESCALE_REALESCALE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define RS_ABI_VERSION 2

/* ------------------------------------------------------------------ */
/* Opaque handles. Never dereference from Dart; pass them back to the */
/* API unchanged.                                                      */
/* ------------------------------------------------------------------ */
typedef struct rs_engine rs_engine;

/* ------------------------------------------------------------------ */
/* Status codes. Never removed, only appended.                        */
/* ------------------------------------------------------------------ */
typedef enum rs_status {
    RS_OK = 0,
    RS_ERR_INVALID_ARG = 1,
    RS_ERR_OUT_OF_MEMORY = 2,
    RS_ERR_ENGINE_INIT = 3,
    RS_ERR_MODEL_LOAD = 4,
    RS_ERR_MODEL_INVALID = 5,
    RS_ERR_INFERENCE = 6,
    RS_ERR_UNSUPPORTED = 7,
    RS_ERR_CANCELLED = 8,
    RS_ERR_IO = 9,
    RS_ERR_DEVICE_LIMIT = 10
} rs_status;

/* ------------------------------------------------------------------ */
/* Compute backend selection. AUTO picks GPU when the engine was      */
/* built with Vulkan and a device is present, otherwise CPU.           */
/* ------------------------------------------------------------------ */
typedef enum rs_compute {
    RS_COMPUTE_AUTO = 0,
    RS_COMPUTE_CPU = 1,
    RS_COMPUTE_GPU = 2
} rs_compute;

typedef struct rs_engine_options {
    int32_t abi_version;   /* must equal RS_ABI_VERSION                  */
    int32_t compute;       /* rs_compute                                  */
    int32_t thread_count;  /* 0 = auto (physical cores)                   */
    int32_t use_fp16;      /* -1 auto, 0 off, 1 on (storage fp16)         */
    int32_t reserved[8];
} rs_engine_options;

/* ------------------------------------------------------------------ */
/* Image. RGBA8888, row-major. `stride` is bytes per row and may be    */
/* larger than width*4. Input images are owned by the caller and are   */
/* never written to. Output images are allocated by the native layer   */
/* (stride == width*4) and released with rs_image_free().              */
/* ------------------------------------------------------------------ */
typedef struct rs_image {
    uint8_t* pixels;
    int32_t  width;
    int32_t  height;
    int32_t  stride;   /* bytes per row */
    int32_t  channels; /* must be 4 (RGBA) in this ABI version */
} rs_image;

/* ------------------------------------------------------------------ */
/* Processing stages reported through the progress callback.           */
/* ------------------------------------------------------------------ */
typedef enum rs_stage {
    RS_STAGE_QUEUED = 0,
    RS_STAGE_PREPROCESS = 1,
    RS_STAGE_INFERENCE = 2,
    RS_STAGE_BLEND = 3,
    RS_STAGE_POSTPROCESS = 4,
    RS_STAGE_DONE = 5
} rs_stage;

typedef struct rs_progress {
    int32_t stage;        /* rs_stage                                   */
    int32_t tiles_done;   /* completed tiles in current attempt         */
    int32_t tiles_total;  /* planned tiles in current attempt           */
    int32_t attempt;      /* 1 = first attempt; >1 = after recovery     */
    int32_t tile_size;    /* current tile edge in input pixels          */
    double  fraction;     /* overall 0..1 estimate                      */
} rs_progress;

typedef void (*rs_progress_cb)(void* user_data, const rs_progress* progress);

/* ------------------------------------------------------------------ */
/* Job description.                                                    */
/*   tile_size    — input-space tile edge. 0 = engine default (auto).  */
/*   overlap      — overlap between adjacent tiles, input pixels.      */
/*                  0 = engine default (model-aware).                  */
/*   context      — extra context margin around each tile, input px.   */
/*                  0 = engine default.                                */
/*   max_retries  — memory-recovery attempts (halving tile size)       */
/*                  before giving up. Default 3 when 0.                */
/* ------------------------------------------------------------------ */
typedef struct rs_job_desc {
    int32_t scale;       /* 2..4, must match the loaded model          */
    int32_t tile_size;
    int32_t tile_overlap;
    int32_t context_margin;
    int32_t max_retries;
    int32_t reserved[4];
} rs_job_desc;

/* Information about the loaded model, filled by rs_engine_load_model. */
typedef struct rs_model_info {
    int32_t scale_hint;         /* nominal upscale factor               */
    int32_t input_tile_hint;    /* recommended input tile edge          */
    int64_t param_bytes;
    int64_t bin_bytes;
    int32_t loaded;             /* 1 when a model is loaded             */
} rs_model_info;

/* Runtime device/backend report. Fields are -1 when unknown.          */
typedef struct rs_device_info {
    int32_t gpu_available;   /* 1 when Vulkan device usable, else 0    */
    int32_t using_gpu;       /* backend actually in use                */
    int32_t thread_count;
    int32_t fp16_storage;    /* 1 when fp16 storage is active          */
} rs_device_info;

/* ------------------------------------------------------------------ */
/* Lifecycle                                                            */
/* ------------------------------------------------------------------ */

/* Returns RS_ABI_VERSION of the loaded library. */
int32_t rs_abi_version(void);

/* Creates an engine. Returns NULL on failure (see rs_last_error). */
rs_engine* rs_engine_create(const rs_engine_options* options);

void rs_engine_destroy(rs_engine* engine);

/* Loads an ncnn model pair (.param + .bin). The paths must be absolute
 * or resolvable from the process working directory. Thread-safe: one
 * model per engine; reload replaces the previous model. */
rs_status rs_engine_load_model(rs_engine* engine,
                               const char* param_path,
                               const char* bin_path,
                               rs_model_info* out_info /* optional */);

rs_status rs_engine_model_info(const rs_engine* engine, rs_model_info* out_info);

rs_status rs_engine_device_info(const rs_engine* engine, rs_device_info* out_info);

/* ------------------------------------------------------------------ */
/* Memory planning helper (no allocation performed).                    */
/* Returns a conservative peak-RAM estimate in bytes for running `job` */
/* on an input of the given size, or -1 on invalid arguments.           */
/* ------------------------------------------------------------------ */
int64_t rs_estimate_peak_memory(int32_t in_w, int32_t in_h,
                                int32_t scale, int32_t tile_size);

/* ------------------------------------------------------------------ */
/* Execution.                                                           */
/*                                                                       */
/* rs_run is synchronous; call it from a background Dart isolate.       */
/* `cancel_flag`, when non-NULL, is polled between tiles: setting it    */
/* to a non-zero value aborts promptly and returns RS_ERR_CANCELLED.   */
/* The progress callback may be invoked from the calling thread only.   */
/* No partial image is ever returned as success.                        */
/* ------------------------------------------------------------------ */
rs_status rs_run(rs_engine* engine,
                 const rs_image* input,
                 const rs_job_desc* job,
                 rs_image** out_output,
                 rs_progress_cb progress,
                 void* progress_user,
                 const volatile int32_t* cancel_flag);

/* Frees an output image previously returned by rs_run. */
void rs_image_free(rs_image* image);

/* Thread-local error message for the last failed call on this thread.
 * Valid until the next native call on the same thread. Never NULL.    */
const char* rs_last_error(void);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* REALESCALE_REALESCALE_H */
