// Generates the golden vectors that prove the Kotlin planner (core/plan/PipelinePlanner.kt) makes
// exactly the same decisions as the prototype's plan.ts.
//
// The function under test is not a re-implementation: the body is extracted verbatim from
// docs/ui-reference/prototype/src/lib/plan.ts, wrapped in a thin shim (model table + DEVICE, parsed
// out of the Kotlin catalog so both sides can never drift), bundled with esbuild and executed.
//
//   cd tools/plan-vectors && npm install && node gen.mjs
// writes app/src/test/resources/plan-golden.json and prints a summary.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import * as esbuild from 'esbuild';

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '../..');
const prototypePlan = resolve(repo, 'docs/ui-reference/prototype/src/lib/plan.ts');
const kotlinCatalog = resolve(repo, 'RealSR-NCNN-Android-GUI/app/src/main/java/com/tumuyan/ncnn/realsr/core/plan/ModelCatalog.kt');
const outJson = resolve(repo, 'RealSR-NCNN-Android-GUI/app/src/test/resources/plan-golden.json');

const planSrc = readFileSync(prototypePlan, 'utf8');
const catalogSrc = readFileSync(kotlinCatalog, 'utf8');

/** Cut a top-level `export function name(...) { ... }` block out of the source by brace matching. */
function cutFunction(src, name) {
  const start = src.indexOf(`export function ${name}`);
  if (start < 0) throw new Error(`function ${name} not found in plan.ts`);
  let i = src.indexOf('{', src.indexOf(')', start));
  let depth = 0;
  for (let j = i; j < src.length; j += 1) {
    if (src[j] === '{') depth += 1;
    else if (src[j] === '}') {
      depth -= 1;
      if (depth === 0) return src.slice(start, j + 1).replace('export function', 'function');
    }
  }
  throw new Error(`unbalanced braces in ${name}`);
}

/** Cut `export const NAME ... = [ ... ];` blocks. */
function cutBlock(src, name) {
  const start = src.indexOf(`export const ${name}`);
  if (start < 0) throw new Error(`const ${name} not found in plan.ts`);
  const end = src.indexOf('];', start);
  return src.slice(start, end + 2).replace('export const', 'const');
}

/** Cut `export const fmtX = (...) => ...;` one-liners. */
function cutArrow(src, name) {
  const re = new RegExp(`export const ${name} = [^;]+;`, 'm');
  const m = src.match(re);
  if (!m) throw new Error(`arrow ${name} not found`);
  return m[0].replace('export const', 'const');
}

// Model table straight out of the Kotlin catalog: id + seconds-per-megapixel are all makePlan reads.
const modelTable = [...catalogSrc.matchAll(/id = "([^"]+)", name = "([^"]+)"[\s\S]*?secPerMP = ([0-9.]+)/g)]
  .map(([, id, name, secPerMP]) => ({ id, name, secPerMP: Number(secPerMP), sizeMB: 0 }));
if (modelTable.length < 10) throw new Error(`parsed only ${modelTable.length} models from ModelCatalog.kt`);

// The prototype names models that the sanctioned mirror does not carry (Real-CUGAN, x4plus). The
// *rule* is kept verbatim; only the ids are translated onto the real catalog, in one place.
const ID_MAP = {
  'esrgan-general-v3': 'esrgan-general-v3', // photo, Upscale Mode: small + fast
  'realcugan-se2': 'waifu2x-anime', //          anime, Upscale Mode: small + fast
  'esrgan-x4plus': 'esrgan-remacri', //         photo, Super Resolution: heavy
  'esrgan-anime-x4': 'esrgan-anime-sharp', //   anime, Super Resolution: heavy
  'esrgan-v3-anime': 'waifu2x-anime', //        anime fallback when nothing is installed
};
const REQUIRED_RECOMMENDED = {
  'upscale:photo': 'esrgan-general-v3',
  'upscale:anime': 'waifu2x-anime',
  'super:photo': 'esrgan-remacri',
  'super:anime': 'esrgan-anime-sharp',
};

const shim = `
type ImageType = 'photo' | 'anime';
type ModeId = 'upscale' | 'super';
type Photo = { name: string; w: number; h: number; sizeMB: number; ext: 'jpg' | 'png' | 'webp'; type: ImageType; lowConfidence?: boolean };
const DEVICE = { tier: 2, label: 'Standard', ramGB: 8, cores: 8, gpu: 'Adreno 730', vulkan: '1.3', freeGB: 41.2, totalGB: 128,
  benchSec: 0.42, inputLimitMP: 4, outputCapMP: 64, superCapMP: 128 };
const MODELS = ${JSON.stringify(modelTable)};
const ID_MAP: Record<string, string> = ${JSON.stringify(ID_MAP)};
const modelById = (id: string) => MODELS.find((m: any) => m.id === (ID_MAP[id] ?? id)) ?? MODELS[0];
${cutBlock(planSrc, 'TILE_PRESETS')}
${cutFunction(planSrc, 'recommendedId').replace('function recommendedId(', 'function recommendedIdRaw(')}
const recommendedId = (mode: ModeId, type: ImageType) => ID_MAP[recommendedIdRaw(mode, type)] ?? recommendedIdRaw(mode, type);
${cutFunction(planSrc, 'makePlan')}
${cutArrow(planSrc, 'fmtDims')}
${cutArrow(planSrc, 'fmtMP')}
${cutArrow(planSrc, 'fmtMB')}
${cutFunction(planSrc, 'fmtEta')}
${cutFunction(planSrc, 'fmtLeft')}
${cutFunction(planSrc, 'resultFileName')}
export { makePlan, recommendedId, DEVICE, MODELS, fmtDims, fmtMP, fmtMB, fmtEta, fmtLeft, resultFileName };
`;

const tmp = resolve(here, '.plan-shim.ts');
writeFileSync(tmp, shim);
const bundled = await esbuild.build({
  entryPoints: [tmp],
  bundle: true,
  format: 'cjs',
  platform: 'node',
  write: false,
  logLevel: 'silent',
});
const require = createRequire(import.meta.url);
const modPath = resolve(here, '.plan-shim.cjs');
writeFileSync(modPath, bundled.outputFiles[0].text);
const ref = require(modPath);

// ---------------------------------------------------------------------------------------------
// The input grid. Every dimension the planner branches on is crossed at least once.
// ---------------------------------------------------------------------------------------------
const PHOTOS = [
  { id: 'small-jpg', w: 1200, h: 800, sizeMB: 2.4, ext: 'jpg', type: 'photo' },
  { id: 'portrait-jpg', w: 1080, h: 1440, sizeMB: 1.6, ext: 'jpg', type: 'photo' },
  { id: 'tiny-jpg', w: 640, h: 480, sizeMB: 0.3, ext: 'jpg', type: 'photo' },
  { id: 'small-file-jpg', w: 960, h: 1280, sizeMB: 0.9, ext: 'jpg', type: 'photo' },
  { id: 'huge-jpg', w: 6000, h: 4000, sizeMB: 18.4, ext: 'jpg', type: 'photo' },
  { id: 'png-anime', w: 900, h: 1200, sizeMB: 1.2, ext: 'png', type: 'anime' },
  { id: 'webp-anime', w: 2048, h: 1365, sizeMB: 1.9, ext: 'webp', type: 'anime' },
  { id: 'big-portrait-jpg', w: 1512, h: 2016, sizeMB: 3.1, ext: 'jpg', type: 'photo' },
  { id: 'wide-jpg', w: 3000, h: 1000, sizeMB: 2.1, ext: 'jpg', type: 'photo' },
];

const LEVELS = ['default', 'manual', 'advanced'];
const SCALES = [2, 3, 4, 5];
const TILES = ['safe', 'balanced', 'fast', 'max', 'custom'];
const TILE_CUSTOM = [320, 200];
const COMPUTE = ['auto', 'gpu', 'cpu'];
const FORMATS = ['default', 'jpeg', 'png', 'webp'];
const COMPRESSION = ['auto', 'off', 'custom'];
const TTA = [false, true];
const PRECISION = ['auto', 'fp16', 'fp32'];
const MODEL_CHOICES = ['auto', 'esrgan-general-v3', 'anime4k-x4', 'esrgan-nomos8ksc'];
const INSTALLED_SETS = [
  { name: 'all', ids: modelTable.map((m) => m.id) },
  { name: 'starter', ids: ['esrgan-general-v3', 'waifu2x-anime', 'srmd-x2', 'anime4k-x2'] },
  { name: 'none', ids: [] },
  { name: 'partial', ids: ['esrgan-general-v3'] },
];
const LARGE = ['optimize', 'full'];
const SETTINGS_COMPUTE = ['auto', 'cpu'];
const SETTINGS_FORMAT = ['auto', 'webp'];

const prefs = (over) => ({
  level: 'default', type: 'auto', modelId: 'auto', scale: 2, tile: 'fast', tileCustom: 320,
  overlap: 'auto', compute: 'auto', threads: 0, compression: 'auto', compressionQ: 90,
  format: 'default', formatQ: 95, denoise: 30, tta: false, precision: 'auto', keepMeta: true,
  sharpen: 0, resize: 'lanczos', color: 'keep', cap: 'none', ...over,
});

const vectors = [];
function push(photo, mode, p, installedSet, opts) {
  const plan = ref.makePlan(photo, mode, p, installedSet.ids, opts);
  vectors.push({
    input: {
      photo: { w: photo.w, h: photo.h, sizeMB: photo.sizeMB, ext: photo.ext, type: photo.type },
      mode,
      prefs: p,
      installed: installedSet.name,
      installedIds: installedSet.ids,
      opts,
    },
    expected: {
      level: plan.level, type: plan.type, scale: plan.scale, modelId: plan.model.id,
      isAuto: plan.isAuto, wantedId: plan.wanted ? plan.wanted.id : null, tilePx: plan.tilePx,
      compute: plan.compute, inW: plan.inW, inH: plan.inH, inMP: plan.inMP,
      overLimit: plan.overLimit, optimized: plan.optimized, outW: plan.outW, outH: plan.outH,
      outMP: plan.outMP, inputNote: plan.inputNote, format: plan.format, ext: plan.ext,
      etaSec: plan.etaSec, sizeMB: plan.sizeMB, cols: plan.cols, rows: plan.rows, tiles: plan.tiles,
      extraResize: plan.extraResize, check: plan.check,
    },
  });
}

const O = { large: 'optimize', compute: 'auto', format: 'auto' };
// Coverage over every photo, then every advanced knob over two representative photos, so the
// golden file stays small enough to live in the repo while still crossing every branch.
const MANUAL_PHOTOS = [PHOTOS[0], PHOTOS[4], PHOTOS[5]];
const KNOB_PHOTOS = [PHOTOS[0], PHOTOS[4]];
const KNOB_SETS = [INSTALLED_SETS[0], INSTALLED_SETS[1]];
const MODES = ['upscale', 'super'];

for (const photo of PHOTOS) {
  for (const mode of MODES) {
    for (const installedSet of INSTALLED_SETS) {
      push(photo, mode, prefs({}), installedSet, O);
      push(photo, mode, prefs({}), installedSet, { large: 'full', compute: 'auto', format: 'auto' });
    }
  }
}

for (const photo of MANUAL_PHOTOS) {
  for (const mode of MODES) {
    for (const installedSet of INSTALLED_SETS) {
      for (const scale of SCALES) {
        for (const modelId of MODEL_CHOICES) {
          push(photo, mode, prefs({ level: 'manual', scale, modelId }), installedSet, O);
        }
        push(photo, mode, prefs({ level: 'manual', scale, type: photo.type === 'photo' ? 'anime' : 'photo' }), installedSet, O);
      }
    }
  }
}

for (const photo of KNOB_PHOTOS) {
  for (const mode of MODES) {
    for (const installedSet of KNOB_SETS) {
      for (const tile of TILES) {
        for (const tileCustom of tile === 'custom' ? TILE_CUSTOM : [320]) {
          push(photo, mode, prefs({ level: 'advanced', scale: 4, tile, tileCustom }), installedSet, O);
        }
      }
      for (const compute of COMPUTE) {
        for (const format of FORMATS) {
          for (const compression of COMPRESSION) {
            for (const tta of TTA) {
              push(photo, mode, prefs({ level: 'advanced', scale: 4, compute, format, compression, tta }), installedSet, O);
            }
          }
        }
      }
      for (const precision of PRECISION) {
        push(photo, mode, prefs({ level: 'advanced', scale: 5, precision }), installedSet, O);
      }
      for (const settingsCompute of SETTINGS_COMPUTE) {
        for (const settingsFormat of SETTINGS_FORMAT) {
          push(photo, mode, prefs({ level: 'advanced', scale: 4 }), installedSet,
            { large: 'optimize', compute: settingsCompute, format: settingsFormat });
        }
      }
      for (const large of LARGE) {
        push(photo, mode, prefs({ level: 'manual', scale: 4 }), installedSet, { large, compute: 'auto', format: 'auto' });
      }
    }
  }
}

const recommended = {};
for (const mode of ['upscale', 'super']) {
  for (const type of ['photo', 'anime']) recommended[`${mode}:${type}`] = ref.recommendedId(mode, type);
}

const formats = [];
for (const [w, h] of [[1200, 800], [6000, 4000], [100, 100], [1512, 2016]]) {
  formats.push({ fn: 'fmtDims', args: [w, h], expected: ref.fmtDims(w, h) });
}
for (const mp of [0.96, 9.99, 10, 23.7, 0.5, 64.0]) {
  formats.push({ fn: 'fmtMP', args: [mp], expected: ref.fmtMP(mp) });
}
for (const mb of [0.1, 4.4, 99.9, 100, 999, 1000, 2400]) {
  formats.push({ fn: 'fmtMB', args: [mb], expected: ref.fmtMB(mb) });
}
for (const sec of [1, 5, 7, 12, 58, 59, 61, 90, 120, 3599, 3600, 5400]) {
  formats.push({ fn: 'fmtEta', args: [sec], expected: ref.fmtEta(sec) });
}
for (const sec of [0.4, 1, 48, 59, 60, 61, 125, 130, 3599]) {
  formats.push({ fn: 'fmtLeft', args: [sec], expected: ref.fmtLeft(sec) });
}
for (const [name, scale, ext, at] of [
  ['IMG_4807.jpg', 2, 'jpg', [2026, 10, 14, 16, 42]],
  ['illust_0312.png', 4, 'png', [2026, 0, 3, 7, 5]],
  ['noext', 3, 'webp', [2025, 11, 31, 23, 59]],
]) {
  formats.push({
    fn: 'resultFileName',
    args: [name, scale, ext, at],
    expected: ref.resultFileName({ name }, scale, ext, new Date(at[0], at[1], at[2], at[3], at[4])),
  });
}

mkdirSync(dirname(outJson), { recursive: true });
writeFileSync(
  outJson,
  `${JSON.stringify({
    generatedFrom: 'docs/ui-reference/prototype/src/lib/plan.ts',
    prototypePlanSha256: null,
    recommended,
    plans: vectors,
    formats,
  }, null, 1)}\n`,
  'utf8',
);
console.log(`wrote ${vectors.length} plan vectors + ${formats.length} format vectors`);
console.log('recommended:', recommended);
if (Object.keys(REQUIRED_RECOMMENDED).some((k) => recommended[k] !== REQUIRED_RECOMMENDED[k])) {
  console.error('WARNING: recommended ids differ from what the Kotlin catalog expects:', REQUIRED_RECOMMENDED);
}
