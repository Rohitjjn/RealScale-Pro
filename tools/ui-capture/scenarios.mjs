/**
 * One entry per reference state. Every scenario boots a fresh page, drives the prototype through
 * its own controls (never by poking React state), and writes one PNG + one layout JSON.
 * Order here defines the NN prefix shared by the light and dark folders.
 */

const STORAGE_KEY = 'realscale-pro:v1';

const settings = (over = {}) => ({
  theme: 'light',
  dynamicColor: false,
  haptics: true,
  launchMode: 'last',
  compute: 'auto',
  keepAwake: true,
  throttle: true,
  format: 'auto',
  keepMeta: true,
  wifiOnly: true,
  analytics: false,
  crashReports: false,
  ...over,
});

const prefs = (over = {}) => ({
  level: 'default',
  type: 'auto',
  modelId: 'auto',
  scale: 2,
  tile: 'fast',
  tileCustom: 320,
  overlap: 'auto',
  compute: 'auto',
  threads: 0,
  compression: 'auto',
  compressionQ: 90,
  format: 'default',
  formatQ: 95,
  denoise: 30,
  tta: false,
  precision: 'auto',
  keepMeta: true,
  sharpen: 0,
  resize: 'lanczos',
  color: 'keep',
  cap: 'none',
  ...over,
});

const RESULT = {
  id: 'r1',
  photoId: 'dog',
  mode: 'upscale',
  scale: 2,
  modelId: 'esrgan-general-v3',
  outW: 2160,
  outH: 2880,
  sizeMB: 5.8,
  timeSec: 42,
  format: 'JPEG',
  fileName: 'IMG_4807_RealScale_2x_20261114-1642.jpg',
  at: Date.now() - 3600_000,
};

const RESULT2 = { ...RESULT, id: 'r2', photoId: 'anime', scale: 4, modelId: 'realcugan-se4', outW: 3600, outH: 4800, sizeMB: 12.4, format: 'PNG', fileName: 'illust_0312_RealScale_4x_20261113-0921.png', at: Date.now() - 86400_000 };
const RESULT3 = { ...RESULT, id: 'r3', photoId: 'vintage', scale: 2, outW: 1280, outH: 960, sizeMB: 1.2, fileName: 'scan_family_1978_RealScale_2x_20261112-2010.jpg' };

const state = (over = {}) => ({
  onboarded: true,
  photoAccess: true,
  notifications: true,
  cameraAsked: true,
  mode: 'upscale',
  superIntroSeen: true,
  installed: ['esrgan-general-v3', 'esrgan-v3-anime', 'realcugan-se2', 'anime4k-x2'],
  results: [],
  settings: settings(over.settings),
  prefs: { upscale: prefs(over.upscale), super: prefs(over.super) },
  ...Object.fromEntries(Object.entries(over).filter(([k]) => k !== 'settings' && k !== 'upscale' && k !== 'super')),
});

const goHome = async (ctx, over) => {
  const page = await ctx.boot({ state: state(over) });
  await ctx.wait(page, 2100); // splash 1.5 s, then the shell
  // Home shows four skeletons for its first 600 ms after mount; wait for the real grid unless the
  // scenario deliberately wants the empty/limited-access state.
  if (over?.photoAccess !== false) await ctx.waitFor(page, '[aria-label^="View "]', 6000);
  return page;
};

const openConfig = async (ctx, page, photo = 'IMG_4807.jpg') => {
  await ctx.tap(page, `Upscale ${photo}`);
  await ctx.wait(page, 400);
};

const scrollMain = (page, y) => page.evaluate((y) => {
  const scroller = [...document.querySelectorAll('div')].find((d) => d.scrollHeight > d.clientHeight + 40 && ['auto', 'scroll'].includes(getComputedStyle(d).overflowY));
  if (scroller) scroller.scrollTop = y === 'end' ? scroller.scrollHeight : y;
}, y);

export const SCENARIOS = [
  /* ---------------- Onboarding ---------------- */
  {
    name: 'onboarding-splash',
    run: async (ctx) => {
      const page = await ctx.boot({ state: state({ onboarded: false, photoAccess: false, notifications: false }) });
      await ctx.wait(page, 1100);
      await ctx.snap(page, 'onboarding-splash');
    },
  },
  {
    name: 'guide-1-sharper',
    run: async (ctx) => {
      const page = await ctx.boot({ state: state({ onboarded: false }) });
      await ctx.wait(page, 1900);
      await ctx.snap(page, 'guide-1-sharper');
    },
  },
  {
    name: 'guide-2-devices',
    run: async (ctx) => {
      const page = await ctx.boot({ state: state({ onboarded: false }) });
      await ctx.wait(page, 1900);
      await ctx.tap(page, 'Continue');
      await ctx.wait(page, 600);
      await ctx.snap(page, 'guide-2-devices');
    },
  },
  {
    name: 'guide-3-modes',
    run: async (ctx) => {
      const page = await ctx.boot({ state: state({ onboarded: false }) });
      await ctx.wait(page, 1900);
      await ctx.tap(page, 'Continue');
      await ctx.wait(page, 500);
      await ctx.tap(page, 'Continue');
      await ctx.wait(page, 600);
      await ctx.snap(page, 'guide-3-modes');
    },
  },
  {
    name: 'guide-4-private',
    run: async (ctx) => {
      const page = await ctx.boot({ state: state({ onboarded: false }) });
      await ctx.wait(page, 1900);
      for (let i = 0; i < 3; i++) {
        await ctx.tap(page, 'Continue');
        await ctx.wait(page, 500);
      }
      await ctx.snap(page, 'guide-4-private');
    },
  },
  {
    name: 'permission-photos',
    run: async (ctx) => {
      const page = await ctx.boot({ state: state({ onboarded: false }) });
      await ctx.wait(page, 1900);
      await ctx.tap(page, 'Skip');
      await ctx.wait(page, 500);
      await ctx.snap(page, 'permission-photos');
    },
  },
  {
    name: 'permission-notifications',
    run: async (ctx) => {
      const page = await ctx.boot({ state: state({ onboarded: false }) });
      await ctx.wait(page, 1900);
      await ctx.tap(page, 'Skip');
      await ctx.wait(page, 500);
      await ctx.tap(page, 'Not now');
      await ctx.wait(page, 500);
      await ctx.snap(page, 'permission-notifications');
    },
  },
  {
    name: 'device-check-running',
    run: async (ctx) => {
      const page = await ctx.boot({ state: state({ onboarded: false }) });
      await ctx.wait(page, 1900);
      await ctx.tap(page, 'Skip');
      await ctx.wait(page, 400);
      await ctx.tap(page, 'Not now');
      await ctx.wait(page, 400);
      await ctx.tap(page, 'Not now');
      await ctx.wait(page, 1000);
      await ctx.snap(page, 'device-check-running');
    },
  },
  {
    name: 'device-check-ready',
    run: async (ctx) => {
      const page = await ctx.boot({ state: state({ onboarded: false }) });
      await ctx.wait(page, 1900);
      await ctx.tap(page, 'Skip');
      await ctx.wait(page, 400);
      await ctx.tap(page, 'Not now');
      await ctx.wait(page, 400);
      await ctx.tap(page, 'Not now');
      await ctx.wait(page, 3400);
      await ctx.snap(page, 'device-check-ready');
    },
  },
  {
    name: 'setup-progress',
    run: async (ctx) => {
      const page = await ctx.boot({ state: state({ onboarded: false }) });
      await ctx.wait(page, 1900);
      await ctx.tap(page, 'Skip');
      await ctx.wait(page, 400);
      await ctx.tap(page, 'Not now');
      await ctx.wait(page, 400);
      await ctx.tap(page, 'Not now');
      await ctx.wait(page, 3400);
      await ctx.tap(page, 'Continue');
      await ctx.wait(page, 1200);
      await ctx.snap(page, 'setup-progress');
    },
  },
  {
    name: 'setup-paused',
    run: async (ctx) => {
      const page = await ctx.boot({ state: state({ onboarded: false }) });
      await ctx.wait(page, 1900);
      await ctx.tap(page, 'Skip');
      await ctx.wait(page, 400);
      await ctx.tap(page, 'Not now');
      await ctx.wait(page, 400);
      await ctx.tap(page, 'Not now');
      await ctx.wait(page, 3400);
      await ctx.tap(page, 'Continue');
      await ctx.wait(page, 1400);
      await ctx.tap(page, 'Pause download');
      await ctx.wait(page, 400);
      await ctx.snap(page, 'setup-paused');
    },
  },
  {
    name: 'setup-complete',
    run: async (ctx) => {
      const page = await ctx.boot({ state: state({ onboarded: false }) });
      await ctx.wait(page, 1900);
      await ctx.tap(page, 'Skip');
      await ctx.wait(page, 400);
      await ctx.tap(page, 'Not now');
      await ctx.wait(page, 400);
      await ctx.tap(page, 'Not now');
      await ctx.wait(page, 3400);
      await ctx.tap(page, 'Continue');
      await ctx.wait(page, 4200);
      await ctx.snap(page, 'setup-complete');
    },
  },

  /* ---------------- Home ---------------- */
  {
    name: 'home-top',
    run: async (ctx) => {
      const page = await goHome(ctx, { results: [RESULT, RESULT2, RESULT3] });
      await ctx.snap(page, 'home-top');
    },
  },
  {
    name: 'home-scrolled',
    run: async (ctx) => {
      const page = await goHome(ctx, { results: [RESULT, RESULT2, RESULT3] });
      await scrollMain(page, 520);
      await ctx.wait(page, 300);
      await ctx.snap(page, 'home-scrolled');
    },
  },
  {
    name: 'home-more-sheet',
    run: async (ctx) => {
      const page = await goHome(ctx, { results: [RESULT] });
      await ctx.tap(page, 'More');
      await ctx.wait(page, 500);
      await ctx.snap(page, 'home-more-sheet');
    },
  },
  {
    name: 'home-camera-sheet',
    run: async (ctx) => {
      const page = await goHome(ctx, { results: [RESULT], cameraAsked: false });
      await ctx.tap(page, 'Camera');
      await ctx.wait(page, 500);
      await ctx.snap(page, 'home-camera-sheet');
    },
  },
  {
    name: 'home-empty-recents',
    run: async (ctx) => {
      const page = await goHome(ctx, { photoAccess: false });
      await ctx.snap(page, 'home-empty-recents');
    },
  },
  {
    name: 'home-super-mode',
    run: async (ctx) => {
      const page = await goHome(ctx, { mode: 'super', results: [RESULT] });
      await ctx.snap(page, 'home-super-mode');
    },
  },

  /* ---------------- Gallery ---------------- */
  {
    name: 'gallery-photos',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'Gallery Albums and all photos');
      await ctx.wait(page, 600);
      await ctx.snap(page, 'gallery-photos');
    },
  },
  {
    name: 'gallery-albums',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'Gallery Albums and all photos');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'albums');
      await ctx.wait(page, 400);
      await ctx.snap(page, 'gallery-albums');
    },
  },
  {
    name: 'gallery-album-camera',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'Gallery Albums and all photos');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'albums');
      await ctx.wait(page, 400);
      await ctx.tap(page, 'Camera');
      await ctx.wait(page, 400);
      await ctx.snap(page, 'gallery-album-camera');
    },
  },
  {
    name: 'gallery-no-access',
    run: async (ctx) => {
      const page = await goHome(ctx, { photoAccess: false });
      await ctx.tap(page, 'Gallery Albums and all photos');
      await ctx.wait(page, 600);
      await ctx.snap(page, 'gallery-no-access');
    },
  },

  /* ---------------- Viewer ---------------- */
  {
    name: 'viewer-photo',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'View IMG_4807.jpg');
      await ctx.wait(page, 600);
      await ctx.snap(page, 'viewer-photo');
    },
  },
  {
    name: 'viewer-controls-hidden',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'View IMG_4807.jpg');
      await ctx.wait(page, 600);
      const box = await page.evaluate(() => {
        const el = [...document.querySelectorAll('img')].find((i) => i.alt === 'IMG_4807.jpg');
        const r = el.getBoundingClientRect();
        return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
      });
      await page.mouse.click(box.x, box.y);
      await ctx.wait(page, 700);
      await ctx.snap(page, 'viewer-controls-hidden');
    },
  },
  {
    name: 'viewer-zoomed',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'View IMG_4807.jpg');
      await ctx.wait(page, 600);
      const box = await page.evaluate(() => {
        const el = [...document.querySelectorAll('img')].find((i) => i.alt === 'IMG_4807.jpg');
        const r = el.getBoundingClientRect();
        return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
      });
      await page.mouse.click(box.x, box.y);
      await ctx.wait(page, 120);
      await page.mouse.click(box.x, box.y);
      await ctx.wait(page, 700);
      await ctx.snap(page, 'viewer-zoomed');
    },
  },
  {
    name: 'viewer-details-sheet',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'View IMG_4807.jpg');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'Details');
      await ctx.wait(page, 600);
      await ctx.snap(page, 'viewer-details-sheet');
    },
  },
  {
    name: 'viewer-share-sheet',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'View IMG_4807.jpg');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'Share');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'viewer-share-sheet');
    },
  },
  {
    name: 'viewer-mode-switch-sheet',
    run: async (ctx) => {
      const page = await goHome(ctx, { superIntroSeen: false });
      await ctx.tap(page, 'View IMG_4807.jpg');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'Mode: Upscale Mode');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'viewer-mode-switch-sheet');
    },
  },

  /* ---------------- Config ---------------- */
  {
    name: 'config-default',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await openConfig(ctx, page);
      await ctx.wait(page, 600);
      await ctx.snap(page, 'config-default');
    },
  },
  {
    name: 'config-default-analyzing',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await openConfig(ctx, page);
      await ctx.wait(page, 60);
      await ctx.snap(page, 'config-default-analyzing');
    },
  },
  {
    name: 'config-manual',
    run: async (ctx) => {
      const page = await goHome(ctx, { upscale: { level: 'manual' } });
      await openConfig(ctx, page);
      await ctx.wait(page, 600);
      await ctx.snap(page, 'config-manual');
    },
  },
  {
    name: 'config-manual-wanted-model',
    run: async (ctx) => {
      const page = await goHome(ctx, { mode: 'upscale', installed: ['esrgan-general-v3', 'esrgan-v3-anime'], upscale: { level: 'manual', type: 'photo' } });
      await openConfig(ctx, page);
      await ctx.wait(page, 600);
      await scrollMain(page, 420);
      await ctx.snap(page, 'config-manual-wanted-model');
    },
  },
  {
    name: 'config-advanced',
    run: async (ctx) => {
      const page = await goHome(ctx, { upscale: { level: 'advanced' } });
      await openConfig(ctx, page);
      await ctx.wait(page, 600);
      await ctx.snap(page, 'config-advanced');
    },
  },
  {
    name: 'config-advanced-scrolled',
    run: async (ctx) => {
      const page = await goHome(ctx, { upscale: { level: 'advanced', tile: 'custom', compute: 'cpu' } });
      await openConfig(ctx, page);
      await ctx.wait(page, 600);
      await scrollMain(page, 900);
      await ctx.snap(page, 'config-advanced-scrolled');
    },
  },
  {
    name: 'config-advanced-bottom',
    run: async (ctx) => {
      const page = await goHome(ctx, { upscale: { level: 'advanced' } });
      await openConfig(ctx, page);
      await ctx.wait(page, 600);
      await scrollMain(page, 2000);
      await ctx.snap(page, 'config-advanced-bottom');
    },
  },
  {
    name: 'config-super',
    run: async (ctx) => {
      const page = await goHome(ctx, { mode: 'super', installed: [...state().installed, 'esrgan-x4plus'], super: { level: 'manual', scale: 4 } });
      await openConfig(ctx, page);
      await ctx.wait(page, 700);
      await ctx.snap(page, 'config-super');
    },
  },
  {
    name: 'config-super-advanced',
    run: async (ctx) => {
      const page = await goHome(ctx, { mode: 'super', installed: [...state().installed, 'esrgan-x4plus'], super: { level: 'advanced', scale: 5 } });
      await openConfig(ctx, page);
      await ctx.wait(page, 700);
      await scrollMain(page, 1200);
      await ctx.snap(page, 'config-super-advanced');
    },
  },
  {
    name: 'config-model-picker',
    run: async (ctx) => {
      const page = await goHome(ctx, { upscale: { level: 'manual' } });
      await openConfig(ctx, page);
      await ctx.wait(page, 600);
      await scrollMain(page, 300);
      await ctx.tap(page, 'Model:');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'config-model-picker');
    },
  },
  {
    name: 'config-large-image',
    run: async (ctx) => {
      const page = await goHome(ctx, { upscale: { level: 'manual', scale: 4 } });
      // A 6000 x 4000 photo (24 MP) is above the 4 MP comfort limit.
      await ctx.tap(page, 'Upscale DSC_0147.jpg');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'config-large-image');
    },
  },
  {
    name: 'config-warning-amber',
    run: async (ctx) => {
      const page = await goHome(ctx, { installed: [...state().installed, 'esrgan-anime-x4'], upscale: { level: 'advanced', type: 'anime', scale: 4, modelId: 'esrgan-anime-x4', compute: 'cpu', tta: true } });
      await ctx.tap(page, 'Upscale illust_0312.png');
      await ctx.wait(page, 700);
      await scrollMain(page, 2000);
      await ctx.tap(page, 'Upscale', { last: true });
      await ctx.wait(page, 800);
      await ctx.snap(page, 'config-warning-amber');
    },
  },
  {
    name: 'config-warning-red',
    run: async (ctx) => {
      const page = await goHome(ctx, { upscale: { level: 'advanced', scale: 5 } });
      await ctx.tap(page, 'Upscale DSC_0147.jpg');
      await ctx.wait(page, 700);
      await ctx.tap(page, 'Upscale', { last: true });
      await ctx.wait(page, 800);
      await ctx.snap(page, 'config-warning-red');
    },
  },
  {
    name: 'config-preview-crop',
    run: async (ctx) => {
      const page = await goHome(ctx, {
        mode: 'super',
        installed: [...state().installed, 'esrgan-x4plus'],
        super: { level: 'advanced', scale: 4 },
      });
      await ctx.tap(page, 'Upscale IMG_4755.jpg');
      await ctx.wait(page, 700);
      await scrollMain(page, 1300);
      await ctx.tap(page, 'Preview crop');
      await ctx.wait(page, 1800);
      await ctx.snap(page, 'config-preview-crop');
    },
  },
  {
    name: 'config-swap-sheet',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await openConfig(ctx, page);
      await ctx.wait(page, 600);
      await ctx.tap(page, 'Choose a different image');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'config-swap-sheet');
    },
  },

  /* ---------------- Processing ---------------- */
  {
    name: 'processing-start',
    run: async (ctx) => {
      const page = await goHome(ctx, { upscale: { level: 'manual', scale: 4 } });
      await ctx.tap(page, 'Upscale IMG_4652.jpg');
      await ctx.wait(page, 700);
      await ctx.tap(page, 'Upscale', { last: true });
      await ctx.wait(page, 900);
      await ctx.snap(page, 'processing-start');
    },
  },
  {
    name: 'processing-mid',
    run: async (ctx) => {
      const page = await goHome(ctx, { upscale: { level: 'manual', scale: 4 } });
      await ctx.tap(page, 'Upscale IMG_4652.jpg');
      await ctx.wait(page, 700);
      await ctx.tap(page, 'Upscale', { last: true });
      await ctx.wait(page, 9000);
      await ctx.snap(page, 'processing-mid');
    },
  },
  {
    name: 'processing-near-end',
    run: async (ctx) => {
      const page = await goHome(ctx, { upscale: { level: 'manual', scale: 4 } });
      await ctx.tap(page, 'Upscale IMG_4652.jpg');
      await ctx.wait(page, 700);
      await ctx.tap(page, 'Upscale', { last: true });
      await ctx.wait(page, 17000);
      await ctx.snap(page, 'processing-near-end');
    },
  },
  {
    name: 'processing-cancel-confirm',
    run: async (ctx) => {
      const page = await goHome(ctx, { upscale: { level: 'manual', scale: 4 } });
      await ctx.tap(page, 'Upscale IMG_4652.jpg');
      await ctx.wait(page, 700);
      await ctx.tap(page, 'Upscale', { last: true });
      await ctx.wait(page, 6000);
      await ctx.tap(page, 'Cancel');
      await ctx.wait(page, 600);
      await ctx.snap(page, 'processing-cancel-confirm');
    },
  },
  {
    name: 'job-strip-background',
    run: async (ctx) => {
      const page = await goHome(ctx, { upscale: { level: 'manual', scale: 4 } });
      await ctx.tap(page, 'Upscale IMG_4652.jpg');
      await ctx.wait(page, 700);
      await ctx.tap(page, 'Upscale', { last: true });
      await ctx.wait(page, 5000);
      await ctx.tap(page, 'Continue in background');
      await ctx.wait(page, 900);
      await ctx.snap(page, 'job-strip-background');
    },
  },

  /* ---------------- Result ---------------- */
  {
    name: 'result',
    run: async (ctx) => {
      const page = await goHome(ctx, { results: [RESULT, RESULT2, RESULT3] });
      await ctx.tap(page, 'My Upscales');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'IMG_4807.jpg, upscaled 2 times');
      await ctx.wait(page, 800);
      await ctx.snap(page, 'result');
    },
  },
  {
    name: 'result-slider-left',
    run: async (ctx) => {
      const page = await goHome(ctx, { results: [RESULT, RESULT2, RESULT3] });
      await ctx.tap(page, 'My Upscales');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'IMG_4807.jpg, upscaled 2 times');
      await ctx.wait(page, 900);
      const box = await page.evaluate(() => {
        const el = document.querySelector('[role="slider"]').closest('div');
        const r = el.getBoundingClientRect();
        return { x: r.left + r.width * 0.2, y: r.top + r.height / 2 };
      });
      await page.mouse.move(box.x, box.y);
      await page.mouse.down();
      await page.mouse.move(box.x + 2, box.y);
      await page.mouse.up();
      await ctx.wait(page, 600);
      await ctx.snap(page, 'result-slider-left');
    },
  },
  {
    name: 'result-details-sheet',
    run: async (ctx) => {
      const page = await goHome(ctx, { results: [RESULT, RESULT2, RESULT3] });
      await ctx.tap(page, 'My Upscales');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'IMG_4807.jpg, upscaled 2 times');
      await ctx.wait(page, 800);
      await ctx.tap(page, 'More options');
      await ctx.wait(page, 500);
      await ctx.tap(page, 'Details');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'result-details-sheet');
    },
  },
  {
    name: 'result-more-sheet',
    run: async (ctx) => {
      const page = await goHome(ctx, { results: [RESULT, RESULT2, RESULT3] });
      await ctx.tap(page, 'My Upscales');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'IMG_4807.jpg, upscaled 2 times');
      await ctx.wait(page, 800);
      await ctx.tap(page, 'More options');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'result-more-sheet');
    },
  },
  {
    name: 'result-delete-confirm',
    run: async (ctx) => {
      const page = await goHome(ctx, { results: [RESULT, RESULT2, RESULT3] });
      await ctx.tap(page, 'My Upscales');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'IMG_4807.jpg, upscaled 2 times');
      await ctx.wait(page, 800);
      await ctx.tap(page, 'More options');
      await ctx.wait(page, 500);
      await ctx.tap(page, 'Delete result');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'result-delete-confirm');
    },
  },
  {
    name: 'result-toast',
    run: async (ctx) => {
      const page = await goHome(ctx, { results: [RESULT, RESULT2, RESULT3] });
      await ctx.tap(page, 'My Upscales');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'IMG_4807.jpg, upscaled 2 times');
      await ctx.wait(page, 800);
      await ctx.tap(page, 'More options');
      await ctx.wait(page, 500);
      await ctx.tap(page, 'Delete result');
      await ctx.wait(page, 500);
      await ctx.tap(page, 'Delete result', { last: true });
      await ctx.wait(page, 600);
      await ctx.snap(page, 'result-toast');
    },
  },

  /* ---------------- My Upscales ---------------- */
  {
    name: 'upscales-grid',
    run: async (ctx) => {
      const page = await goHome(ctx, { results: [RESULT, RESULT2, RESULT3] });
      await ctx.tap(page, 'My Upscales');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'upscales-grid');
    },
  },
  {
    name: 'upscales-select',
    run: async (ctx) => {
      const page = await goHome(ctx, { results: [RESULT, RESULT2, RESULT3] });
      await ctx.tap(page, 'My Upscales');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'Select');
      await ctx.wait(page, 400);
      await ctx.tap(page, 'illust_0312.png, upscaled 4 times');
      await ctx.wait(page, 400);
      await ctx.snap(page, 'upscales-select');
    },
  },
  {
    name: 'upscales-empty',
    run: async (ctx) => {
      const page = await goHome(ctx, { results: [] });
      await ctx.tap(page, 'My Upscales');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'upscales-empty');
    },
  },

  /* ---------------- Model Manager ---------------- */
  {
    name: 'models-all',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'Models');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'models-all');
    },
  },
  {
    name: 'models-anime',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'Models');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'anime');
      await ctx.wait(page, 400);
      await ctx.snap(page, 'models-anime');
    },
  },
  {
    name: 'models-downloading',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'Models');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'Download Real-ESRGAN x4plus');
      await ctx.wait(page, 900);
      await ctx.snap(page, 'models-downloading');
    },
  },
  {
    name: 'models-delete-confirm',
    run: async (ctx) => {
      const page = await goHome(ctx, { installed: [...state().installed, 'esrgan-x4plus'] });
      await ctx.tap(page, 'Models');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'Delete Real-ESRGAN x4plus');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'models-delete-confirm');
    },
  },
  {
    name: 'models-scrolled',
    run: async (ctx) => {
      const page = await goHome(ctx, { installed: [...state().installed, 'esrgan-x4plus'] });
      await ctx.tap(page, 'Models');
      await ctx.wait(page, 600);
      await scrollMain(page, 800);
      await ctx.snap(page, 'models-scrolled');
    },
  },

  /* ---------------- Settings / Help / About ---------------- */
  {
    name: 'settings',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'Settings');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'settings');
    },
  },
  {
    name: 'settings-scrolled',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'Settings');
      await ctx.wait(page, 600);
      await scrollMain(page, 900);
      await ctx.snap(page, 'settings-scrolled');
    },
  },
  {
    name: 'settings-bottom',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'Settings');
      await ctx.wait(page, 600);
      await scrollMain(page, 'end');
      await ctx.snap(page, 'settings-bottom');
    },
  },
  {
    name: 'settings-theme-sheet',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'Settings');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'Re-run device check');
      await ctx.wait(page, 500);
      await ctx.snap(page, 'settings-recheck');
    },
  },
  {
    name: 'help-faq',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'Help and feedback');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'Why is upscaling slow on my phone?');
      await ctx.wait(page, 500);
      await ctx.snap(page, 'help-faq');
    },
  },
  {
    name: 'about',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'About and open-source licenses');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'about');
    },
  },
  {
    name: 'about-scrolled',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'About and open-source licenses');
      await ctx.wait(page, 600);
      await scrollMain(page, 700);
      await ctx.snap(page, 'about-scrolled');
    },
  },

  /* ---------------- Extra states (appended so earlier numbers stay stable) ---------------- */
  {
    name: 'home-recents-loading',
    run: async (ctx) => {
      const page = await ctx.boot({ state: state({ results: [RESULT] }) });
      await ctx.wait(page, 1760);
      await ctx.snap(page, 'home-recents-loading');
    },
  },
  {
    name: 'config-default-lowconfidence',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await openConfig(ctx, page, 'IMG_4652.jpg');
      await ctx.wait(page, 800);
      await ctx.snap(page, 'config-default-lowconfidence');
    },
  },
  {
    name: 'config-super-default',
    run: async (ctx) => {
      const page = await goHome(ctx, { mode: 'super', installed: [...state().installed, 'esrgan-x4plus'] });
      await openConfig(ctx, page, 'IMG_4755.jpg');
      await ctx.wait(page, 800);
      await ctx.snap(page, 'config-super-default');
    },
  },
  {
    name: 'config-preview-crop-running',
    run: async (ctx) => {
      const page = await goHome(ctx, { mode: 'super', installed: [...state().installed, 'esrgan-x4plus'], super: { level: 'advanced', scale: 4 } });
      await ctx.tap(page, 'Upscale IMG_4755.jpg');
      await ctx.wait(page, 700);
      await scrollMain(page, 1300);
      await ctx.tap(page, 'Preview crop');
      await ctx.wait(page, 500);
      await ctx.snap(page, 'config-preview-crop-running');
    },
  },
  {
    name: 'viewer-single',
    run: async (ctx) => {
      const page = await goHome(ctx, { results: [RESULT, RESULT2, RESULT3] });
      await ctx.tap(page, 'My Upscales');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'IMG_4807.jpg, upscaled 2 times');
      await ctx.wait(page, 700);
      await ctx.tap(page, 'Open full screen');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'viewer-single');
    },
  },
  {
    name: 'models-photo-filter',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'Models');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'photo');
      await ctx.wait(page, 400);
      await ctx.snap(page, 'models-photo-filter');
    },
  },
  {
    name: 'settings-launch-mode-sheet',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'Settings');
      await ctx.wait(page, 600);
      await ctx.tap(page, 'Default mode on launch');
      await ctx.wait(page, 700);
      await ctx.snap(page, 'settings-launch-mode-sheet');
    },
  },
  {
    name: 'toast-photo-access',
    run: async (ctx) => {
      const page = await goHome(ctx, { photoAccess: false });
      await ctx.tap(page, 'Allow photo access');
      await ctx.wait(page, 400);
      await ctx.snap(page, 'toast-photo-access');
    },
  },
  {
    name: 'toast-models-update',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'Models');
      await ctx.wait(page, 600);
      await scrollMain(page, 'end');
      await ctx.tap(page, 'Check for updates');
      await ctx.wait(page, 1500);
      await ctx.snap(page, 'toast-models-update');
    },
  },
  {
    name: 'guide-replay',
    run: async (ctx) => {
      const page = await goHome(ctx, {});
      await ctx.tap(page, 'Settings');
      await ctx.wait(page, 600);
      await scrollMain(page, 'end');
      await ctx.tap(page, 'Replay the guide');
      await ctx.wait(page, 800);
      await ctx.snap(page, 'guide-replay');
    },
  },
];
