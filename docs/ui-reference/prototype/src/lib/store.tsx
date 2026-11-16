import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { MODELS, SAMPLE_PHOTOS, modelById, type ModeId, type Photo } from './data';
import { defaultPrefs, resultFileName, type JobPrefs, type Plan } from './plan';

/* ---------- Types ---------- */
export type Route =
  | { name: 'home' }
  | { name: 'viewer'; photoId: string; single?: boolean }
  | { name: 'config'; photoId: string }
  | { name: 'processing' }
  | { name: 'result'; resultId: string }
  | { name: 'upscales' }
  | { name: 'gallery' }
  | { name: 'models' }
  | { name: 'settings' }
  | { name: 'help' }
  | { name: 'about' };

export type Phase = 'splash' | 'guide' | 'perm-photos' | 'perm-notif' | 'device' | 'setup' | 'app';
export type HapticKind = 'light' | 'medium' | 'tick' | 'success' | 'warning';

export interface Settings {
  theme: 'system' | 'light' | 'dark';
  dynamicColor: boolean;
  haptics: boolean;
  launchMode: 'last' | ModeId;
  compute: 'auto' | 'gpu' | 'cpu';
  keepAwake: boolean;
  throttle: boolean;
  format: 'auto' | 'jpeg' | 'png' | 'webp';
  keepMeta: boolean;
  wifiOnly: boolean;
  analytics: boolean;
  crashReports: boolean;
}

export interface Result {
  id: string;
  photoId: string;
  mode: ModeId;
  scale: number;
  modelId: string;
  outW: number;
  outH: number;
  sizeMB: number;
  timeSec: number;
  format: string;
  fileName: string;
  at: number;
}

export interface Persisted {
  onboarded: boolean;
  photoAccess: boolean;
  notifications: boolean;
  cameraAsked: boolean;
  mode: ModeId;
  superIntroSeen: boolean;
  installed: string[];
  results: Result[];
  settings: Settings;
  prefs: Record<ModeId, JobPrefs>;
}

export interface Job {
  id: string;
  photoId: string;
  mode: ModeId;
  plan: Plan;
  progress: number;
  realMs: number;
  status: 'running' | 'done';
  resultId?: string;
  background: boolean;
}

interface Nav {
  stack: Route[];
  top: Route;
  dir: 'push' | 'pop' | 'fade';
  push: (r: Route) => void;
  pop: (n?: number) => void;
  replace: (r: Route) => void;
  home: () => void;
}

interface AppApi {
  s: Persisted;
  set: (patch: Partial<Persisted>) => void;
  setSettings: (patch: Partial<Settings>) => void;
  setPrefs: (mode: ModeId, patch: Partial<JobPrefs>) => void;
  phase: Phase;
  setPhase: (p: Phase) => void;
  replay: boolean;
  replayGuide: () => void;
  nav: Nav;
  toast: (msg: string) => void;
  toastMsg: { id: number; msg: string } | null;
  photos: Photo[];
  getPhoto: (id: string) => Photo | undefined;
  importFile: (file: File) => Promise<Photo>;
  haptic: (k: HapticKind) => void;
  device: HTMLElement | null;
  setDevice: (el: HTMLElement | null) => void;
  addGuard: (fn: () => void) => () => void;
  deleteResults: (ids: string[]) => void;
  resetApp: () => void;
}

interface LiveApi {
  job: Job | null;
  startJob: (photo: Photo, plan: Plan, mode: ModeId) => void;
  cancelJob: () => void;
  clearJob: () => void;
  setBackground: (b: boolean) => void;
  downloads: Record<string, number>;
  download: (id: string) => void;
  cancelDownload: (id: string) => void;
}

/* ---------- Persistence ---------- */
const KEY = 'realscale-pro:v1';

const initial = (): Persisted => ({
  onboarded: false,
  photoAccess: false,
  notifications: false,
  cameraAsked: false,
  mode: 'upscale',
  superIntroSeen: false,
  installed: [],
  results: [],
  settings: {
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
  },
  prefs: { upscale: defaultPrefs(), super: defaultPrefs() },
});

function load(): Persisted {
  const base = initial();
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return base;
    const p = JSON.parse(raw) as Partial<Persisted>;
    const merged: Persisted = {
      ...base,
      ...p,
      settings: { ...base.settings, ...p.settings },
      prefs: { upscale: { ...base.prefs.upscale, ...p.prefs?.upscale }, super: { ...base.prefs.super, ...p.prefs?.super } },
      installed: (p.installed ?? []).filter((id) => MODELS.some((m) => m.id === id)),
      // Imported photos live only for the session, so their results cannot be restored.
      results: (p.results ?? []).filter((r) => SAMPLE_PHOTOS.some((ph) => ph.id === r.photoId)),
    };
    if (merged.settings.launchMode !== 'last') merged.mode = merged.settings.launchMode;
    return merged;
  } catch {
    return base;
  }
}

/** Per-session view state that should survive navigation but not an app reset. */
export const session = { homeScroll: 0, recentsLoaded: false };

const uid = () => Math.random().toString(36).slice(2, 10);
const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v));

const AppCtx = createContext<AppApi | null>(null);
const LiveCtx = createContext<LiveApi | null>(null);

export function useApp(): AppApi {
  const v = useContext(AppCtx);
  if (!v) throw new Error('useApp outside provider');
  return v;
}
export function useLive(): LiveApi {
  const v = useContext(LiveCtx);
  if (!v) throw new Error('useLive outside provider');
  return v;
}

/* ---------- Provider ---------- */
export function AppProvider({ children }: { children: ReactNode }) {
  const [s, setS] = useState<Persisted>(load);
  const [phase, setPhase] = useState<Phase>('splash');
  const [replay, setReplay] = useState(false);
  const [stack, setStack] = useState<Route[]>([{ name: 'home' }]);
  const [dir, setDir] = useState<Nav['dir']>('fade');
  const [toastMsg, setToastMsg] = useState<{ id: number; msg: string } | null>(null);
  const [imported, setImported] = useState<Photo[]>([]);
  const [device, setDevice] = useState<HTMLElement | null>(null);
  const [job, setJob] = useState<Job | null>(null);
  const [downloads, setDownloads] = useState<Record<string, number>>({});

  useEffect(() => {
    try {
      localStorage.setItem(KEY, JSON.stringify(s));
    } catch {
      /* storage unavailable: the session still works */
    }
  }, [s]);

  /* Theme */
  useEffect(() => {
    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    const apply = () => {
      const t = s.settings.theme === 'system' ? (mql.matches ? 'dark' : 'light') : s.settings.theme;
      document.documentElement.dataset.theme = t;
      document.querySelector('meta[name="theme-color"]')?.setAttribute('content', t === 'dark' ? '#181715' : '#faf9f5');
    };
    apply();
    mql.addEventListener('change', apply);
    return () => mql.removeEventListener('change', apply);
  }, [s.settings.theme]);

  /* Haptics */
  const hapticsOn = useRef(true);
  hapticsOn.current = s.settings.haptics;
  const haptic = useCallback((k: HapticKind) => {
    if (!hapticsOn.current) return;
    const pattern = { light: 8, medium: 18, tick: 6, success: [14, 70, 24], warning: [30, 50, 30] }[k];
    try {
      navigator.vibrate?.(pattern);
    } catch {
      /* not supported */
    }
  }, []);

  /* Toast */
  const toast = useCallback((msg: string) => setToastMsg({ id: Date.now() + Math.random(), msg }), []);
  useEffect(() => {
    if (!toastMsg) return;
    const t = setTimeout(() => setToastMsg(null), 3000);
    return () => clearTimeout(t);
  }, [toastMsg]);

  /* Navigation: an in-app stack mirrored into browser history so the system back gesture works. */
  const stackRef = useRef(stack);
  stackRef.current = stack;
  const hist = useRef({ depth: 0, skip: 0 });
  const guards = useRef<(() => void)[]>([]);

  const push = useCallback((r: Route) => {
    setDir('push');
    setStack((st) => [...st, r]);
    try {
      window.history.pushState({ rs: 1 }, '');
      hist.current.depth++;
    } catch {
      /* sandboxed frame: in-app back still works */
    }
  }, []);

  const pop = useCallback((n = 1) => {
    const count = Math.min(n, stackRef.current.length - 1);
    if (count <= 0) return;
    setDir('pop');
    setStack((st) => st.slice(0, Math.max(1, st.length - count)));
    const k = Math.min(count, hist.current.depth);
    if (k > 0) {
      hist.current.depth -= k;
      hist.current.skip++;
      try {
        window.history.go(-k);
      } catch {
        hist.current.skip--;
      }
    }
  }, []);

  const replace = useCallback((r: Route) => {
    setDir('fade');
    setStack((st) => [...st.slice(0, -1), r]);
  }, []);

  const home = useCallback(() => pop(stackRef.current.length - 1), [pop]);

  useEffect(() => {
    const onPop = () => {
      const h = hist.current;
      if (h.skip > 0) {
        h.skip--;
        return;
      }
      h.depth = Math.max(0, h.depth - 1);
      const guard = guards.current[guards.current.length - 1];
      if (guard) {
        try {
          window.history.pushState({ rs: 1 }, '');
          h.depth++;
        } catch {
          /* ignore */
        }
        guard();
        return;
      }
      setDir('pop');
      setStack((st) => (st.length > 1 ? st.slice(0, -1) : st));
    };
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);

  const addGuard = useCallback((fn: () => void) => {
    guards.current.push(fn);
    return () => {
      guards.current = guards.current.filter((g) => g !== fn);
    };
  }, []);

  /* Photos */
  const photos = useMemo(() => [...imported, ...SAMPLE_PHOTOS], [imported]);
  const getPhoto = useCallback((id: string) => photos.find((p) => p.id === id), [photos]);

  const importFile = useCallback(async (file: File): Promise<Photo> => {
    const src = URL.createObjectURL(file);
    const img = new Image();
    await new Promise<void>((res, rej) => {
      img.onload = () => res();
      img.onerror = () => rej(new Error('unsupported'));
      img.src = src;
    });
    const w = img.naturalWidth;
    const h = img.naturalHeight;
    if (w < 64 || h < 64) {
      URL.revokeObjectURL(src);
      throw new Error('tiny');
    }
    // Low-resolution stand-in used for the "before" side while the engine is simulated.
    const k = 200 / Math.max(w, h);
    const c = document.createElement('canvas');
    c.width = Math.max(1, Math.round(w * k));
    c.height = Math.max(1, Math.round(h * k));
    c.getContext('2d')?.drawImage(img, 0, 0, c.width, c.height);
    let lo = src;
    try {
      lo = c.toDataURL('image/jpeg', 0.6);
    } catch {
      /* keep the original */
    }
    const lower = file.name.toLowerCase();
    const photo: Photo = {
      id: `import-${uid()}`,
      src,
      lo,
      name: file.name || 'photo.jpg',
      w,
      h,
      sizeMB: Math.max(0.1, Math.round((file.size / 1048576) * 10) / 10),
      date: 'Just now',
      type: 'photo',
      album: 'Imported',
      ext: lower.endsWith('.png') ? 'png' : lower.endsWith('.webp') ? 'webp' : 'jpg',
      imported: true,
    };
    setImported((list) => [photo, ...list]);
    return photo;
  }, []);

  /* State helpers */
  const set = useCallback((patch: Partial<Persisted>) => setS((p) => ({ ...p, ...patch })), []);
  const setSettings = useCallback((patch: Partial<Settings>) => setS((p) => ({ ...p, settings: { ...p.settings, ...patch } })), []);
  const setPrefs = useCallback(
    (mode: ModeId, patch: Partial<JobPrefs>) => setS((p) => ({ ...p, prefs: { ...p.prefs, [mode]: { ...p.prefs[mode], ...patch } } })),
    [],
  );
  const deleteResults = useCallback((ids: string[]) => setS((p) => ({ ...p, results: p.results.filter((r) => !ids.includes(r.id)) })), []);

  const replayGuide = useCallback(() => {
    setReplay(true);
    setPhase('guide');
  }, []);

  const resetApp = useCallback(() => {
    try {
      localStorage.removeItem(KEY);
    } catch {
      /* ignore */
    }
    session.homeScroll = 0;
    session.recentsLoaded = false;
    setJob(null);
    setDownloads({});
    setImported([]);
    setReplay(false);
    home();
    setS(initial());
    setPhase('splash');
  }, [home]);

  /* ---------- Job engine (simulated; mirrors the Job Manager contract) ---------- */
  const jobId = job?.id;
  const jobStatus = job?.status;
  useEffect(() => {
    if (!jobId || jobStatus !== 'running') return;
    let last = performance.now();
    const t = setInterval(() => {
      const now = performance.now();
      const dt = now - last;
      last = now;
      setJob((j) => {
        if (!j || j.status !== 'running') return j;
        // Long jobs briefly slow down, as a hot phone would.
        const slow = j.plan.etaSec > 90 && j.progress > 0.5 && j.progress < 0.7 ? 0.55 : 1;
        return { ...j, progress: Math.min(1, j.progress + (dt / j.realMs) * slow) };
      });
    }, 80);
    return () => clearInterval(t);
  }, [jobId, jobStatus]);

  const quarter = job && job.status === 'running' ? Math.floor(job.progress * 4) : 0;
  useEffect(() => {
    if (quarter >= 1 && quarter <= 3) haptic('tick');
  }, [quarter, haptic]);

  useEffect(() => {
    if (!job || job.status !== 'running' || job.progress < 1) return;
    const photo = photos.find((p) => p.id === job.photoId);
    const { plan } = job;
    const at = Date.now();
    const result: Result = {
      id: uid(),
      photoId: job.photoId,
      mode: job.mode,
      scale: plan.scale,
      modelId: plan.model.id,
      outW: plan.outW,
      outH: plan.outH,
      sizeMB: plan.sizeMB,
      timeSec: plan.etaSec,
      format: plan.format,
      fileName: photo ? resultFileName(photo, plan.scale, plan.ext, new Date(at)) : `RealScale_${plan.scale}x.${plan.ext}`,
      at,
    };
    setS((p) => ({ ...p, results: [result, ...p.results] }));
    setJob((j) => (j ? { ...j, status: 'done', resultId: result.id } : j));
    haptic('success');
  }, [job, photos, haptic]);

  const startJob = useCallback((photo: Photo, plan: Plan, mode: ModeId) => {
    setJob({
      id: uid(),
      photoId: photo.id,
      mode,
      plan,
      progress: 0,
      // Short jobs run in real time; long ones are time-compressed for the prototype.
      realMs: clamp(plan.etaSec, 7, 13) * 1000,
      status: 'running',
      background: false,
    });
  }, []);
  const cancelJob = useCallback(() => setJob(null), []);
  const clearJob = useCallback(() => setJob(null), []);
  const setBackground = useCallback((b: boolean) => setJob((j) => (j ? { ...j, background: b } : j)), []);

  /* ---------- Model downloads (simulated) ---------- */
  const downloading = Object.keys(downloads).length > 0;
  useEffect(() => {
    if (!downloading) return;
    let last = performance.now();
    const t = setInterval(() => {
      const now = performance.now();
      const dt = now - last;
      last = now;
      setDownloads((d) => {
        const next: Record<string, number> = {};
        for (const id of Object.keys(d)) {
          const duration = clamp(modelById(id).sizeMB * 45, 1400, 6500);
          next[id] = Math.min(1, d[id] + dt / duration);
        }
        return next;
      });
    }, 100);
    return () => clearInterval(t);
  }, [downloading]);

  useEffect(() => {
    const done = Object.keys(downloads).filter((id) => downloads[id] >= 1);
    if (!done.length) return;
    setDownloads((d) => {
      const next = { ...d };
      done.forEach((id) => delete next[id]);
      return next;
    });
    setS((p) => ({ ...p, installed: [...new Set([...p.installed, ...done])] }));
    haptic('success');
    toast(`${modelById(done[0]).name} installed`);
  }, [downloads, haptic, toast]);

  const download = useCallback((id: string) => setDownloads((d) => (id in d ? d : { ...d, [id]: 0 })), []);
  const cancelDownload = useCallback(
    (id: string) =>
      setDownloads((d) => {
        const next = { ...d };
        delete next[id];
        return next;
      }),
    [],
  );

  const app = useMemo<AppApi>(
    () => ({
      s, set, setSettings, setPrefs, phase, setPhase, replay, replayGuide,
      nav: { stack, top: stack[stack.length - 1], dir, push, pop, replace, home },
      toast, toastMsg, photos, getPhoto, importFile, haptic, device, setDevice, addGuard, deleteResults, resetApp,
    }),
    [s, set, setSettings, setPrefs, phase, replay, replayGuide, stack, dir, push, pop, replace, home, toast, toastMsg, photos, getPhoto, importFile, haptic, device, addGuard, deleteResults, resetApp],
  );

  const live = useMemo<LiveApi>(
    () => ({ job, startJob, cancelJob, clearJob, setBackground, downloads, download, cancelDownload }),
    [job, startJob, cancelJob, clearJob, setBackground, downloads, download, cancelDownload],
  );

  return (
    <AppCtx.Provider value={app}>
      <LiveCtx.Provider value={live}>{children}</LiveCtx.Provider>
    </AppCtx.Provider>
  );
}
