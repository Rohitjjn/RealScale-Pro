
type ImageType = 'photo' | 'anime';
type ModeId = 'upscale' | 'super';
type Photo = { name: string; w: number; h: number; sizeMB: number; ext: 'jpg' | 'png' | 'webp'; type: ImageType; lowConfidence?: boolean };
const DEVICE = { tier: 2, label: 'Standard', ramGB: 8, cores: 8, gpu: 'Adreno 730', vulkan: '1.3', freeGB: 41.2, totalGB: 128,
  benchSec: 0.42, inputLimitMP: 4, outputCapMP: 64, superCapMP: 128 };
const MODELS = [{"id":"esrgan-general-v3","name":"Real-ESRGAN general v3","secPerMP":3,"sizeMB":0},{"id":"waifu2x-anime","name":"waifu2x anime","secPerMP":4,"sizeMB":0},{"id":"srmd-x2","name":"SRMD 2×","secPerMP":4,"sizeMB":0},{"id":"anime4k-x2","name":"Anime4K 2×","secPerMP":1.6,"sizeMB":0},{"id":"esrgan-anime-sharp-lite","name":"AnimeSharp Lite x4","secPerMP":8,"sizeMB":0},{"id":"esrgan-anime-sharp","name":"AnimeSharp x4","secPerMP":12,"sizeMB":0},{"id":"esrgan-remacri","name":"Remacri x4","secPerMP":14,"sizeMB":0},{"id":"esrgan-ultramix","name":"UltraMix Balanced x4","secPerMP":13,"sizeMB":0},{"id":"esrgan-nomos8ksc","name":"Nomos8kSC x4","secPerMP":18,"sizeMB":0},{"id":"esrgan-sourcebook","name":"SourceBook x2","secPerMP":14,"sizeMB":0},{"id":"mnn-moesr","name":"MoeSR illustration (MNN)","secPerMP":14,"sizeMB":0},{"id":"mnn-nomos8ksc","name":"Nomos8kSC (MNN)","secPerMP":16,"sizeMB":0},{"id":"waifu2x-cunet","name":"waifu2x CUnet","secPerMP":6,"sizeMB":0},{"id":"srmd-nf-x2","name":"SRMD 2× (no denoise)","secPerMP":4,"sizeMB":0},{"id":"anime4k-x4","name":"Anime4K 4×","secPerMP":2.2,"sizeMB":0}];
const ID_MAP: Record<string, string> = {"esrgan-general-v3":"esrgan-general-v3","realcugan-se2":"waifu2x-anime","esrgan-x4plus":"esrgan-remacri","esrgan-anime-x4":"esrgan-anime-sharp","esrgan-v3-anime":"waifu2x-anime"};
const modelById = (id: string) => MODELS.find((m: any) => m.id === (ID_MAP[id] ?? id)) ?? MODELS[0];
const TILE_PRESETS: { id: TilePreset; label: string; px: number; mem: string; factor: number; recommended?: boolean; disabled?: string }[] = [
  { id: 'safe', label: 'Safe', px: 128, mem: '0.4 GB', factor: 1.3 },
  { id: 'balanced', label: 'Balanced', px: 256, mem: '0.9 GB', factor: 1.12 },
  { id: 'fast', label: 'Fast', px: 384, mem: '1.6 GB', factor: 1, recommended: true },
  { id: 'max', label: 'Maximum', px: 0, mem: 'No tiling', factor: 0.92, disabled: 'Maximum needs a Powerful device with 12 GB or more memory.' },
];
function recommendedIdRaw(mode: ModeId, type: ImageType): string {
  if (mode === 'upscale') return type === 'photo' ? 'esrgan-general-v3' : 'realcugan-se2';
  return type === 'photo' ? 'esrgan-x4plus' : 'esrgan-anime-x4';
}
const recommendedId = (mode: ModeId, type: ImageType) => ID_MAP[recommendedIdRaw(mode, type)] ?? recommendedIdRaw(mode, type);
function makePlan(
  photo: Photo,
  mode: ModeId,
  prefs: JobPrefs,
  installed: string[],
  /** `compute` and `format` are the app-wide preferences from Settings. */
  opts: { large?: 'optimize' | 'full'; compute?: 'auto' | 'gpu' | 'cpu'; format?: 'auto' | 'jpeg' | 'png' | 'webp' } = {},
): Plan {
  const level = prefs.level;
  const manual = level !== 'default';
  const adv = level === 'advanced';
  const has = (id: string) => installed.includes(id);

  const type: ImageType = manual && prefs.type !== 'auto' ? prefs.type : photo.type;
  const scale = manual ? prefs.scale : 2;

  // Model
  const wantedId = recommendedId(mode, type);
  const fallbackId = type === 'photo' ? 'esrgan-general-v3' : 'esrgan-v3-anime';
  let model: Model;
  let isAuto = true;
  let wanted: Model | undefined;
  if (manual && prefs.modelId !== 'auto' && has(prefs.modelId)) {
    model = modelById(prefs.modelId);
    isAuto = false;
  } else if (has(wantedId)) {
    model = modelById(wantedId);
  } else {
    model = modelById(fallbackId);
    wanted = modelById(wantedId);
  }

  // Tiles and compute
  const preset = TILE_PRESETS.find((t) => t.id === prefs.tile);
  const tilePx = !adv ? 384 : prefs.tile === 'custom' ? prefs.tileCustom : (preset?.px ?? 384);
  const tileFactor = !adv ? 1 : prefs.tile === 'custom' ? (tilePx >= 384 ? 1 : tilePx >= 256 ? 1.12 : tilePx >= 128 ? 1.3 : 1.6) : (preset?.factor ?? 1);
  const computePref = adv && prefs.compute !== 'auto' ? prefs.compute : (opts.compute ?? 'auto');
  const compute: 'GPU' | 'CPU' = computePref === 'cpu' ? 'CPU' : 'GPU';

  // Input (section 5.2 and 8.6)
  const rawMP = (photo.w * photo.h) / 1e6;
  const overLimit = mode === 'upscale' && rawMP > DEVICE.inputLimitMP;
  const optimized = overLimit && (!manual || opts.large !== 'full');
  let inW = photo.w;
  let inH = photo.h;
  if (optimized) {
    const k = Math.sqrt(DEVICE.inputLimitMP / rawMP);
    inW = Math.round(photo.w * k);
    inH = Math.round(photo.h * k);
  }
  const outW = inW * scale;
  const outH = inH * scale;
  const outMP = (outW * outH) / 1e6;

  let inputNote: string;
  if (mode === 'super') inputNote = 'Original file, no compression';
  else if (optimized) inputNote = `Large photo optimized for your device (${Math.round(rawMP)} MP to ${DEVICE.inputLimitMP} MP)`;
  else {
    const c = adv ? prefs.compression : 'auto';
    if (c === 'off') inputNote = 'Not compressed';
    else if (c === 'custom') inputNote = `Re-encoded at ${prefs.compressionQ}% quality`;
    else inputNote = photo.sizeMB > 2 ? 'Re-encoded at 90% quality (file is over 2 MB)' : 'Used as is (2 MB or smaller)';
  }

  // Output format (section 7.3)
  const source: Plan['format'] = photo.ext === 'png' ? 'PNG' : 'JPEG';
  const formatPref = adv && prefs.format !== 'default' ? prefs.format : opts.format && opts.format !== 'auto' ? opts.format : null;
  const chosen = formatPref ? ({ jpeg: 'JPEG', png: 'PNG', webp: 'WebP' } as const)[formatPref] : null;
  const format: Plan['format'] = chosen ?? (mode === 'super' ? 'PNG' : source);
  const ext = ({ JPEG: 'jpg', PNG: 'png', WebP: 'webp' } as const)[format];

  // Time estimate
  let sec = outMP * model.secPerMP * tileFactor * (compute === 'CPU' ? 4 : 1);
  if (adv && prefs.tta) sec *= 8;
  if (adv && prefs.precision === 'fp32') sec *= 1.25;
  if (scale === 5) sec += 1 + outMP * 0.05;
  sec += 2;
  const etaSec = Math.max(5, Math.round(sec));

  const perMP = format === 'PNG' ? 2.4 : format === 'WebP' ? 0.45 : 0.8;
  const sizeMB = Math.max(0.1, Math.round(outMP * perMP * 10) / 10);

  const cols = tilePx ? Math.max(1, Math.ceil(inW / tilePx)) : 1;
  const rows = tilePx ? Math.max(1, Math.ceil(inH / tilePx)) : 1;

  // Feasibility (section 6.5 and 8.6)
  const cap = mode === 'upscale' ? DEVICE.outputCapMP : DEVICE.superCapMP;
  let check: Check = { level: 'green' };
  if (outMP > cap + 0.01) {
    let maxScale: number | null = null;
    for (const s of [4, 3, 2]) {
      if (s < scale && (inW * inH * s * s) / 1e6 <= cap) {
        maxScale = s;
        break;
      }
    }
    check = {
      level: 'red',
      title: 'This is too large for your device.',
      body: `${fmtDims(outW, outH)} is ${Math.round(outMP)} MP. Your device can safely produce up to ${cap} MP in this mode.`,
      maxScale,
    };
  } else if (format === 'WebP' && Math.max(outW, outH) > 16383) {
    check = { level: 'amber', kind: 'format', title: 'Too large for WebP.', body: 'WebP supports up to 16,383 px per side. PNG has no such limit.' };
  } else if (etaSec > 600) {
    check = {
      level: 'amber',
      kind: 'time',
      title: `This will take about ${fmtEta(etaSec)} on your device.`,
      body: 'You can keep going, or make it faster with a lower scale or a lighter model.',
    };
  }

  return {
    level, type, scale, model, isAuto, wanted, tilePx, compute,
    inW, inH, inMP: (inW * inH) / 1e6, overLimit, optimized,
    outW, outH, outMP, inputNote, format, ext, etaSec, sizeMB,
    cols, rows, tiles: cols * rows,
    extraResize: scale === 5, check,
  };
}
const fmtDims = (w: number, h: number) => `${w} × ${h}`;
const fmtMP = (mp: number) => `${mp >= 10 ? Math.round(mp) : mp.toFixed(1)} MP`;
const fmtMB = (mb: number) => (mb >= 1000 ? `${(mb / 1000).toFixed(1)} GB` : `${mb >= 100 ? Math.round(mb) : mb.toFixed(1)} MB`);
function fmtEta(sec: number): string {
  if (sec < 60) return `${Math.max(5, Math.round(sec / 5) * 5)} s`;
  if (sec < 3600) return `${Math.round(sec / 60)} min`;
  return `${(sec / 3600).toFixed(1)} h`;
}
function fmtLeft(sec: number): string {
  const s = Math.max(1, Math.ceil(sec));
  if (s < 60) return `${s} s`;
  const m = Math.floor(s / 60);
  const r = Math.round((s % 60) / 5) * 5;
  return r ? `${m} min ${r} s` : `${m} min`;
}
function resultFileName(photo: Photo, scale: number, ext: string, at = new Date()): string {
  const base = photo.name.replace(/\.[^.]+$/, '');
  const p = (n: number) => String(n).padStart(2, '0');
  const stamp = `${at.getFullYear()}${p(at.getMonth() + 1)}${p(at.getDate())}-${p(at.getHours())}${p(at.getMinutes())}`;
  return `${base}_RealScale_${scale}x_${stamp}.${ext}`;
}
export { makePlan, recommendedId, DEVICE, MODELS, fmtDims, fmtMP, fmtMB, fmtEta, fmtLeft, resultFileName };
