import anime from '../assets/photos/anime.jpg';
import animeLo from '../assets/photos/anime_lo.jpg';
import coffee from '../assets/photos/coffee.jpg';
import coffeeLo from '../assets/photos/coffee_lo.jpg';
import dog from '../assets/photos/dog.jpg';
import dogLo from '../assets/photos/dog_lo.jpg';
import facade from '../assets/photos/facade.jpg';
import facadeLo from '../assets/photos/facade_lo.jpg';
import friends from '../assets/photos/friends.jpg';
import friendsLo from '../assets/photos/friends_lo.jpg';
import lake from '../assets/photos/lake.jpg';
import lakeLo from '../assets/photos/lake_lo.jpg';
import market from '../assets/photos/market.jpg';
import marketLo from '../assets/photos/market_lo.jpg';
import night from '../assets/photos/night.jpg';
import nightLo from '../assets/photos/night_lo.jpg';
import portrait from '../assets/photos/portrait.jpg';
import portraitLo from '../assets/photos/portrait_lo.jpg';
import vintage from '../assets/photos/vintage.jpg';
import vintageLo from '../assets/photos/vintage_lo.jpg';

export type ModeId = 'upscale' | 'super';
export type ImageType = 'photo' | 'anime';

export interface Photo {
  id: string;
  /** Sharp asset: thumbnails, preview and the "after" side. */
  src: string;
  /** Low-resolution stand-in: the "before" side and the unprocessed tiles. */
  lo: string;
  name: string;
  w: number;
  h: number;
  sizeMB: number;
  date: string;
  type: ImageType;
  lowConfidence?: boolean;
  album: string;
  ext: 'jpg' | 'png' | 'webp';
  imported?: boolean;
}

export const SAMPLE_PHOTOS: Photo[] = [
  { id: 'friends', src: friends, lo: friendsLo, name: 'IMG_4821.jpg', w: 1200, h: 800, sizeMB: 2.4, date: 'Today, 6:42 PM', type: 'photo', album: 'Camera', ext: 'jpg' },
  { id: 'dog', src: dog, lo: dogLo, name: 'IMG_4807.jpg', w: 1080, h: 1440, sizeMB: 1.6, date: 'Today, 1:15 PM', type: 'photo', album: 'Camera', ext: 'jpg' },
  { id: 'anime', src: anime, lo: animeLo, name: 'illust_0312.png', w: 900, h: 1200, sizeMB: 1.2, date: 'Yesterday, 4:20 PM', type: 'anime', album: 'Downloads', ext: 'png' },
  { id: 'night', src: night, lo: nightLo, name: 'IMG_4790.jpg', w: 1512, h: 2016, sizeMB: 3.1, date: 'Yesterday, 9:58 PM', type: 'photo', album: 'Camera', ext: 'jpg' },
  { id: 'portrait', src: portrait, lo: portraitLo, name: 'IMG_4755.jpg', w: 1200, h: 1600, sizeMB: 2.2, date: 'Sunday, 5:03 PM', type: 'photo', album: 'Camera', ext: 'jpg' },
  { id: 'vintage', src: vintage, lo: vintageLo, name: 'scan_family_1978.jpg', w: 640, h: 480, sizeMB: 0.3, date: 'Saturday, 11:20 AM', type: 'photo', album: 'Scans', ext: 'jpg' },
  { id: 'coffee', src: coffee, lo: coffeeLo, name: 'IMG_4731.jpg', w: 960, h: 1280, sizeMB: 0.9, date: 'Saturday, 9:12 AM', type: 'photo', album: 'Camera', ext: 'jpg' },
  { id: 'market', src: market, lo: marketLo, name: 'DSC_0147.jpg', w: 6000, h: 4000, sizeMB: 18.4, date: 'Friday, 4:47 PM', type: 'photo', album: 'Camera import', ext: 'jpg' },
  { id: 'facade', src: facade, lo: facadeLo, name: 'IMG_4698.jpg', w: 1536, h: 2048, sizeMB: 2.8, date: 'Last week', type: 'photo', album: 'Camera', ext: 'jpg' },
  { id: 'lake', src: lake, lo: lakeLo, name: 'IMG_4652.jpg', w: 2048, h: 1365, sizeMB: 1.9, date: 'Last week', type: 'photo', lowConfidence: true, album: 'Camera', ext: 'jpg' },
];

/* ---------- Device profile (what the device check reports) ---------- */
export const DEVICE = {
  tier: 2 as 0 | 1 | 2 | 3,
  label: 'Standard' as 'Light' | 'Standard' | 'Powerful',
  ramGB: 8,
  cores: 8,
  gpu: 'Adreno 730',
  vulkan: '1.3',
  freeGB: 41.2,
  totalGB: 128,
  benchSec: 0.42,
  inputLimitMP: 4,
  outputCapMP: 64,
  superCapMP: 128,
};

/* ---------- Model catalog ----------
   Only the models bundled with RealSR-NCNN-Android (tumuyan), matching its
   preset list (style_array). Real-CUGAN denoise presets (conservative,
   noise0-noise3) are exposed through the denoise slider. The Anime4K blur
   and ACNet variants ship inside the two Anime4K entries. The repo's one
   non-upscaling preset (ESRGAN-DeMosaic, 1x) is not listed. */
export type PackId = 'starter' | 'esrgan' | 'cugan' | 'anime4k';
export type ModelKind = 'photo' | 'anime';

export interface Model {
  id: string;
  name: string;
  desc: string;
  kind: ModelKind;
  scales: number[];
  engine: 'ncnn' | 'MNN';
  sizeMB: number;
  speed: 'Very fast' | 'Fast' | 'Medium' | 'Slow';
  badge: 'Fast' | 'Balanced' | 'Best quality';
  minTier: 0 | 1 | 2;
  pack: PackId;
  license: string;
  /** Seconds per output megapixel on the reference device. */
  secPerMP: number;
  denoise?: boolean;
  blocked?: string;
  required?: boolean;
}

export const MODELS: Model[] = [
  // Starter: small, fast models installed during setup
  { id: 'esrgan-general-v3', name: 'Real-ESRGAN general v3', desc: 'Everyday photos, compact and fast', kind: 'photo', scales: [4], engine: 'ncnn', sizeMB: 8.8, speed: 'Very fast', badge: 'Fast', minTier: 0, pack: 'starter', license: 'BSD-3-Clause', secPerMP: 3, required: true },
  { id: 'esrgan-v3-anime', name: 'Real-ESRGAN v3 anime', desc: 'Anime and illustration, fast', kind: 'anime', scales: [2, 3, 4], engine: 'ncnn', sizeMB: 3.4, speed: 'Very fast', badge: 'Fast', minTier: 0, pack: 'starter', license: 'BSD-3-Clause', secPerMP: 2.6, required: true },
  { id: 'realcugan-se2', name: 'Real-CUGAN SE 2×', desc: 'Anime with clean lines', kind: 'anime', scales: [2], engine: 'ncnn', sizeMB: 9.6, speed: 'Fast', badge: 'Balanced', minTier: 0, pack: 'starter', license: 'MIT', secPerMP: 5, denoise: true, required: true },
  { id: 'anime4k-x2', name: 'Anime4K 2×', desc: 'Anime, fastest. ACNet included', kind: 'anime', scales: [2], engine: 'ncnn', sizeMB: 1.2, speed: 'Very fast', badge: 'Fast', minTier: 0, pack: 'starter', license: 'MIT', secPerMP: 1.6, required: true },

  // Real-ESRGAN family
  { id: 'esrgan-x4plus', name: 'Real-ESRGAN x4plus', desc: 'High-quality photos', kind: 'photo', scales: [4], engine: 'ncnn', sizeMB: 64.7, speed: 'Medium', badge: 'Best quality', minTier: 1, pack: 'esrgan', license: 'BSD-3-Clause', secPerMP: 22 },
  { id: 'esrgan-anime-x4', name: 'Real-ESRGAN anime x4', desc: 'Anime and art, high quality', kind: 'anime', scales: [4], engine: 'ncnn', sizeMB: 17.9, speed: 'Medium', badge: 'Best quality', minTier: 1, pack: 'esrgan', license: 'BSD-3-Clause', secPerMP: 12 },
  { id: 'esrgan-v2-anime', name: 'Real-ESRGAN v2 anime', desc: 'Anime, earlier generation', kind: 'anime', scales: [2, 4], engine: 'ncnn', sizeMB: 4.8, speed: 'Fast', badge: 'Balanced', minTier: 0, pack: 'esrgan', license: 'BSD-3-Clause', secPerMP: 6 },
  { id: 'esrgan-nomos8ksc', name: 'Real-ESRGAN Nomos8kSC', desc: 'Detailed photos and textures', kind: 'photo', scales: [4], engine: 'ncnn', sizeMB: 33.4, speed: 'Medium', badge: 'Best quality', minTier: 2, pack: 'esrgan', license: 'To be verified', secPerMP: 18 },
  { id: 'esrgan-nomos8ksc-mnn', name: 'Nomos8kSC (MNN)', desc: 'Nomos8kSC on the MNN engine', kind: 'photo', scales: [4], engine: 'MNN', sizeMB: 33.9, speed: 'Medium', badge: 'Best quality', minTier: 2, pack: 'esrgan', license: 'To be verified', secPerMP: 16 },
  { id: 'esrgan-sourcebook', name: 'Real-ESRGAN SourceBook', desc: 'Scanned books and documents', kind: 'photo', scales: [2], engine: 'ncnn', sizeMB: 16.8, speed: 'Medium', badge: 'Balanced', minTier: 1, pack: 'esrgan', license: 'To be verified', secPerMP: 14 },
  { id: 'esrgan-moesr', name: 'ESRGAN MoeSR', desc: 'Japanese illustration style', kind: 'anime', scales: [4], engine: 'ncnn', sizeMB: 16.6, speed: 'Medium', badge: 'Balanced', minTier: 1, pack: 'esrgan', license: 'To be verified', secPerMP: 14 },

  // Real-CUGAN family (denoise presets become the denoise slider)
  { id: 'realcugan-se4', name: 'Real-CUGAN SE 4×', desc: 'Anime at 4×', kind: 'anime', scales: [4], engine: 'ncnn', sizeMB: 9.6, speed: 'Fast', badge: 'Balanced', minTier: 0, pack: 'cugan', license: 'MIT', secPerMP: 7, denoise: true },
  { id: 'realcugan-pro2', name: 'Real-CUGAN Pro 2×', desc: 'Anime, pro weights', kind: 'anime', scales: [2], engine: 'ncnn', sizeMB: 12.4, speed: 'Fast', badge: 'Best quality', minTier: 1, pack: 'cugan', license: 'MIT', secPerMP: 8, denoise: true },
  { id: 'realcugan-pro3', name: 'Real-CUGAN Pro 3×', desc: 'Anime, pro weights at 3×', kind: 'anime', scales: [3], engine: 'ncnn', sizeMB: 12.4, speed: 'Fast', badge: 'Best quality', minTier: 1, pack: 'cugan', license: 'MIT', secPerMP: 9, denoise: true },

  // Anime4K family
  { id: 'anime4k-x4', name: 'Anime4K 4×', desc: 'Anime at 4×, fastest', kind: 'anime', scales: [4], engine: 'ncnn', sizeMB: 1.2, speed: 'Very fast', badge: 'Fast', minTier: 0, pack: 'anime4k', license: 'MIT', secPerMP: 2.2 },
];

export const STARTER_IDS = MODELS.filter((m) => m.pack === 'starter').map((m) => m.id);

export const PACKS: { id: PackId; name: string; note: string }[] = [
  { id: 'starter', name: 'Starter', note: 'Small, fast models installed during setup.' },
  { id: 'esrgan', name: 'Real-ESRGAN', note: 'Photo and anime models from the Real-ESRGAN family.' },
  { id: 'cugan', name: 'Real-CUGAN', note: 'Anime models. Denoise presets are under the denoise slider.' },
  { id: 'anime4k', name: 'Anime4K', note: 'Shader-based anime upscaling, includes ACNet variants.' },
];

export const modelById = (id: string) => MODELS.find((m) => m.id === id) ?? MODELS[0];

export type Fit = { tone: 'good' | 'ok' | 'no'; label: string };
export function fitOf(m: Model): Fit {
  if (m.blocked) return { tone: 'no', label: 'Not available' };
  if (m.minTier > DEVICE.tier) return { tone: 'no', label: 'Needs a Powerful device' };
  if (m.speed === 'Medium' || m.speed === 'Slow') return { tone: 'ok', label: 'Runs OK on your device' };
  return { tone: 'good', label: 'Great on your device' };
}

export const MODE_NAME: Record<ModeId, string> = { upscale: 'Upscale Mode', super: 'Super Resolution' };

export const TIPS = [
  '4× works best on photos under 1000 px.',
  'Your original photo is never changed.',
  'Anime and illustrations get their own model automatically.',
  'Results are saved to Pictures/RealScale-Pro.',
  'Anime4K is the fastest choice for anime and illustrations.',
  'Everything runs on this device. No upload, no account.',
];

export const FAQ = [
  { q: 'Why is upscaling slow on my phone?', a: 'Every pixel is rebuilt on your device instead of a server. Bigger photos, higher scales and heavier models take longer. Upscale Mode with the Default level is the fastest path.' },
  { q: 'What is a tile?', a: 'A big image is cut into small squares, each square is enhanced, then they are joined again. Smaller tiles use less memory, which is how the app avoids crashes on phones with little RAM.' },
  { q: 'Why does 5× look a little different?', a: 'Models work at 2×, 3× or 4×. For 5×, the app runs 4× and then resizes the result up by 125% with light sharpening, so there is one extra resize step.' },
  { q: 'Do my photos leave my device?', a: 'No. There is no account and no cloud processing. The network is used only to download models and check for model updates.' },
  { q: 'Which mode should I use?', a: 'Upscale Mode is right for everyday photos on any device. Super Resolution Mode keeps the original file untouched and uses heavier models, so it suits powerful devices and detailed work.' },
];

export const LICENSES = [
  { name: 'ncnn', license: 'BSD-3-Clause', use: 'Primary on-device engine' },
  { name: 'MNN', license: 'Apache-2.0', use: 'Engine for MNN-format models' },
  { name: 'Real-ESRGAN', license: 'BSD-3-Clause', use: 'Photo and anime upscaling' },
  { name: 'Real-CUGAN', license: 'MIT', use: 'Anime upscaling' },
  { name: 'Anime4KCPP', license: 'MIT', use: 'Anime4K upscaling' },
  { name: 'ImageMagick', license: 'Apache-2.0', use: 'Image conversion' },
];
