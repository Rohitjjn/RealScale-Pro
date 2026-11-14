import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Bell, Check, Cpu, Gauge, HardDrive, Images, Laptop, Lock, MemoryStick, Pause, Play, ShieldCheck, Smartphone, Tablet, X, Zap } from 'lucide-react';
import { cn } from '../utils/cn';
import { DEVICE, SAMPLE_PHOTOS, STARTER_IDS, modelById } from '../lib/data';
import { fmtMB } from '../lib/plan';
import { useApp } from '../lib/store';
import { Badge, Button, IconButton, Mark, ProgressBar } from '../components/ui';

export function Onboarding() {
  const { phase } = useApp();
  return (
    <div key={phase} className="anim-fade absolute inset-0 flex flex-col bg-canvas">
      {phase === 'splash' && <Splash />}
      {phase === 'guide' && <Guide />}
      {phase === 'perm-photos' && <Primer kind="photos" />}
      {phase === 'perm-notif' && <Primer kind="notif" />}
      {phase === 'device' && <DeviceCheck />}
      {phase === 'setup' && <Setup />}
    </div>
  );
}

/* ---------- 3.1 Splash ---------- */
function Splash() {
  const { setPhase, s } = useApp();
  const onboarded = s.onboarded;
  useEffect(() => {
    const t = setTimeout(() => setPhase(onboarded ? 'app' : 'guide'), 1500);
    return () => clearTimeout(t);
  }, [setPhase, onboarded]);
  return (
    <div className="flex flex-1 flex-col items-center justify-center px-6">
      <Mark animate className="size-36 shadow-soft" />
      <p className="display mt-7 text-[2.5rem]" style={{ animation: 'rise-in 0.6s var(--spring) 0.35s both' }}>
        RealScale-Pro
      </p>
      <p className="mt-2 text-sm text-muted" style={{ animation: 'fade-in 0.5s ease 0.7s both' }}>
        On-Device Image Upscaling
      </p>
    </div>
  );
}

/* ---------- 3.2 Guide ---------- */
const pill = 'absolute top-3 rounded-full bg-stage/75 px-2.5 py-1 text-xs font-medium text-on-dark';

function ArtSharper() {
  const p = SAMPLE_PHOTOS.find((x) => x.id === 'dog') ?? SAMPLE_PHOTOS[0];
  return (
    <div className="relative aspect-[3/4] h-[min(100%,26rem)] max-w-full overflow-hidden rounded-xl bg-card">
      <img src={p.src} alt="A golden retriever. The left side is blurry, the right side is sharp after upscaling." className="absolute inset-0 size-full object-cover" />
      <img src={p.lo} alt="" aria-hidden className="anim-sweep-clip absolute inset-0 size-full object-cover" style={{ clipPath: 'inset(0 50% 0 0)' }} />
      <span aria-hidden className="anim-sweep-line absolute inset-y-0 w-0.5 -translate-x-1/2 bg-white" style={{ left: '50%' }} />
      <span className={cn(pill, 'left-3')}>Before</span>
      <span className={cn(pill, 'right-3')}>After</span>
    </div>
  );
}

function Meter({ bars }: { bars: number }) {
  return (
    <span className="flex items-end gap-1" aria-hidden>
      {[1, 2, 3].map((n) => (
        <span key={n} className={cn('w-1.5 rounded-full', n <= bars ? 'bg-ink' : 'bg-muted-soft/40')} style={{ height: 6 + n * 5 }} />
      ))}
    </span>
  );
}

function ArtDevices() {
  const rows = [
    { Icon: Smartphone, name: 'Light', note: 'Budget phones with 2 GB', bars: 1 },
    { Icon: Tablet, name: 'Standard', note: 'Most phones and laptops', bars: 2 },
    { Icon: Laptop, name: 'Powerful', note: 'Flagships and gaming PCs', bars: 3 },
  ];
  return (
    <div className="w-full space-y-3">
      {rows.map(({ Icon, name, note, bars }) => (
        <div key={name} className="flex items-center gap-4 rounded-lg bg-card p-4">
          <Icon className="size-6 shrink-0 text-ink" strokeWidth={1.6} />
          <div className="min-w-0 flex-1">
            <p className="text-base font-medium text-ink">{name}</p>
            <p className="text-sm text-body">{note}</p>
          </div>
          <Meter bars={bars} />
        </div>
      ))}
    </div>
  );
}

function ArtModes() {
  return (
    <div className="w-full space-y-3">
      <div className="rounded-lg bg-inverse p-5 text-on-inverse">
        <p className="eyebrow text-on-inverse-soft">Default</p>
        <p className="display mt-2 text-[1.875rem]">Upscale Mode</p>
        <p className="mt-1.5 text-sm leading-relaxed text-on-inverse-soft">Fast and simple, for everyday photos.</p>
      </div>
      <div className="rounded-lg border border-hairline p-5">
        <Badge tone="amber">Pro</Badge>
        <p className="display mt-2 text-[1.875rem]">Super Resolution</p>
        <p className="mt-1.5 text-sm leading-relaxed text-muted">Maximum quality from the original file.</p>
      </div>
    </div>
  );
}

function ArtPrivate() {
  const rows = [
    ['account', 'none'],
    ['uploads', '0 photos'],
    ['processing', 'this device'],
    ['network', 'model downloads only'],
  ];
  return (
    <div className="chrome w-full rounded-lg bg-dark p-5 font-mono text-[0.8125rem] leading-relaxed text-on-dark">
      <div className="mb-3 flex items-center gap-2 text-on-dark-soft">
        <Lock className="size-4" />
        <span>privacy.status</span>
      </div>
      {rows.map(([k, v]) => (
        <div key={k} className="flex justify-between gap-4 border-t border-on-dark/10 py-2.5">
          <span className="text-on-dark-soft">{k}</span>
          <span>{v}</span>
        </div>
      ))}
      <div className="flex items-center gap-2 border-t border-on-dark/10 pt-3 text-teal">
        <span className="size-1.5 rounded-full bg-teal" />
        works offline after setup
      </div>
    </div>
  );
}

const SLIDES: { title: string; body: string; art: ReactNode }[] = [
  { title: 'Make any photo sharper', body: 'Old, small or blurry. Pick a photo, tap Upscale and get a bigger, cleaner image.', art: <ArtSharper /> },
  { title: 'Works on every device', body: 'From a budget phone to a gaming PC. The app checks your device and adapts on its own.', art: <ArtDevices /> },
  { title: 'Two modes', body: 'Upscale Mode for quick everyday results. Super Resolution Mode when you want the very best.', art: <ArtModes /> },
  { title: 'Private and offline', body: 'Your photos never leave your device. No account, no upload, no internet after setup.', art: <ArtPrivate /> },
];

function Guide() {
  const { replay, setPhase } = useApp();
  const scroller = useRef<HTMLDivElement>(null);
  const [i, setI] = useState(0);
  const last = i === SLIDES.length - 1;
  const finish = () => setPhase(replay ? 'app' : 'perm-photos');
  const go = (n: number) => {
    const el = scroller.current;
    if (!el) return;
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    el.scrollTo({ left: n * el.clientWidth, behavior: reduce ? 'auto' : 'smooth' });
  };

  return (
    <>
      <div className="safe-t shrink-0">
        <div className="flex h-14 items-center justify-between pr-3 pl-5">
          <Mark className="size-7 text-ink" />
          {replay ? (
            <IconButton label="Close guide" variant="plain" onClick={finish}>
              <X className="size-5.5" />
            </IconButton>
          ) : (
            <button type="button" onClick={finish} className="min-h-11 rounded-md px-3 text-[0.9375rem] font-medium text-muted active:bg-card">
              Skip
            </button>
          )}
        </div>
      </div>

      <div
        ref={scroller}
        className="no-scrollbar flex min-h-0 flex-1 snap-x snap-mandatory overflow-x-auto overscroll-x-contain"
        onScroll={(e) => setI(Math.round(e.currentTarget.scrollLeft / e.currentTarget.clientWidth))}
      >
        {SLIDES.map((sl, n) => (
          <section key={sl.title} aria-label={`${n + 1} of ${SLIDES.length}: ${sl.title}`} className="flex w-full shrink-0 snap-center flex-col px-6">
            <div className="flex min-h-0 flex-1 items-center justify-center py-3">{sl.art}</div>
            <div className="shrink-0 pt-3 pb-5">
              <h2 className="display text-[2.375rem]">{sl.title}</h2>
              <p className="mt-3 text-base leading-relaxed text-body">{sl.body}</p>
            </div>
          </section>
        ))}
      </div>

      <div className="safe-b shrink-0 px-6 pt-1">
        <div className="mb-4 flex items-center gap-1.5" aria-hidden>
          {SLIDES.map((sl, n) => (
            <span key={sl.title} className={cn('h-1.5 rounded-full transition-all duration-300 ease-spring', n === i ? 'w-6 bg-ink' : 'w-1.5 bg-muted-soft/50')} />
          ))}
        </div>
        <Button variant="primary" size="lg" full onClick={() => (last ? finish() : go(i + 1))}>
          {last ? (replay ? 'Done' : 'Get started') : 'Continue'}
        </Button>
      </div>
    </>
  );
}

/* ---------- 3.3 Permission primers ---------- */
function Primer({ kind }: { kind: 'photos' | 'notif' }) {
  const { set, setPhase, haptic } = useApp();
  const photos = kind === 'photos';
  const next = () => setPhase(photos ? 'perm-notif' : 'device');
  const Icon = photos ? Images : Bell;
  return (
    <>
      <div className="safe-t flex min-h-0 flex-1 flex-col overflow-y-auto px-6">
        <div className="my-auto py-6">
        <div className="flex size-16 items-center justify-center rounded-xl bg-card">
          <Icon className="size-7 text-ink" strokeWidth={1.6} />
        </div>
        <p className="eyebrow mt-8 text-muted">{photos ? 'Permission 1 of 2' : 'Permission 2 of 2 · Optional'}</p>
        <h1 className="display mt-3 text-[2.375rem]">{photos ? 'See your recent photos' : "Know when it's done"}</h1>
        <p className="mt-4 text-base leading-relaxed text-body">
          {photos
            ? 'RealScale-Pro shows your latest photos on Home, so upscaling one takes a single tap.'
            : 'Get an alert when an upscale finishes while the app is in the background.'}
        </p>
        <ul className="mt-6 space-y-3 text-[0.9375rem] leading-snug text-body">
          <li className="flex gap-3">
            <Check className="mt-0.5 size-4.5 shrink-0 text-ink" />
            <span>{photos ? 'Used only to show and open your photos.' : 'Only about your own upscales.'}</span>
          </li>
          <li className="flex gap-3">
            <ShieldCheck className="mt-0.5 size-4.5 shrink-0 text-ink" />
            <span>{photos ? 'The app never uploads, edits or deletes them.' : 'No marketing or reminders, ever.'}</span>
          </li>
        </ul>
        </div>
      </div>
      <div className="safe-b flex shrink-0 flex-col gap-2 px-6 pt-4">
        <Button
          variant="primary"
          size="lg"
          full
          onClick={() => {
            haptic('light');
            set(photos ? { photoAccess: true } : { notifications: true });
            next();
          }}
        >
          {photos ? 'Allow photo access' : 'Allow notifications'}
        </Button>
        <Button variant="ghost" full onClick={next}>
          Not now
        </Button>
      </div>
    </>
  );
}

/* ---------- 3.4 Device check ---------- */
const CHECKS = [
  { Icon: MemoryStick, label: 'Memory', value: `${DEVICE.ramGB} GB` },
  { Icon: Cpu, label: 'Processor', value: `${DEVICE.cores} cores` },
  { Icon: Zap, label: 'Graphics', value: `Vulkan ${DEVICE.vulkan}` },
  { Icon: HardDrive, label: 'Free storage', value: `${DEVICE.freeGB} GB` },
  { Icon: Gauge, label: 'Speed test', value: `${DEVICE.benchSec} s per tile` },
];

function DeviceCheck() {
  const { setPhase, haptic } = useApp();
  const [n, setN] = useState(0);
  const done = n >= CHECKS.length;
  useEffect(() => {
    if (done) {
      haptic('success');
      return;
    }
    const t = setTimeout(() => setN((v) => v + 1), 500);
    return () => clearTimeout(t);
  }, [n, done, haptic]);

  return (
    <>
      <div className="safe-t flex min-h-0 flex-1 flex-col overflow-y-auto px-6 pb-2 *:shrink-0">
        <div className="pt-10">
          <p className="eyebrow text-muted">Device check</p>
          <h1 className="display mt-3 text-[2.375rem]" aria-live="polite">
            {done ? 'Your device is ready.' : 'Checking your device'}
          </h1>
        </div>
        <div className="mt-7 divide-y divide-hairline-soft overflow-hidden rounded-lg border border-hairline">
          {CHECKS.map(({ Icon, label, value }, i) => (
            <div key={label} className="flex min-h-13 items-center gap-3 px-4">
              <Icon className={cn('size-5 shrink-0 transition-colors', i <= n ? 'text-ink' : 'text-muted-soft')} strokeWidth={1.7} />
              <span className={cn('flex-1 text-[0.9375rem] font-medium transition-colors', i <= n ? 'text-ink' : 'text-muted-soft')}>{label}</span>
              {i < n ? (
                <span className="anim-fade flex items-center gap-2 text-sm text-body">
                  <span className="font-mono text-[0.8125rem]">{value}</span>
                  <Check className="size-4 text-ink" />
                </span>
              ) : i === n ? (
                <span className="skeleton h-3 w-20 rounded-full" aria-label="Checking" />
              ) : null}
            </div>
          ))}
        </div>
        {done && (
          <div className="anim-rise mt-4 rounded-lg bg-card p-5">
            <p className="eyebrow text-body">Performance</p>
            <p className="display mt-2 text-[2.125rem]">{DEVICE.label}</p>
            <div className="mt-4 grid grid-cols-3 gap-1.5" aria-hidden>
              {['Light', 'Standard', 'Powerful'].map((l, i) => (
                <div key={l}>
                  <div className={cn('h-1.5 rounded-full', i <= DEVICE.tier - 1 ? 'bg-ink' : 'bg-muted-soft/35')} />
                  <p className={cn('mt-1.5 text-xs', l === DEVICE.label ? 'font-medium text-ink' : 'text-body')}>{l}</p>
                </div>
              ))}
            </div>
            <p className="mt-4 text-sm leading-relaxed text-body">The right model and settings are picked for you automatically.</p>
          </div>
        )}
      </div>
      <div className="safe-b shrink-0 px-6 pt-4">
        <Button variant="primary" size="lg" full disabled={!done} onClick={() => setPhase('setup')}>
          {done ? 'Continue' : 'Checking…'}
        </Button>
      </div>
    </>
  );
}

/* ---------- 3.5 Setup and Starter Pack download ---------- */
const STARTER = STARTER_IDS.map(modelById);
const TOTAL = STARTER.reduce((sum, m) => sum + m.sizeMB, 0);
const SPEED = 7.4;

function Setup() {
  const { set, setPhase, haptic } = useApp();
  const [mb, setMb] = useState(0);
  const [paused, setPaused] = useState(false);
  const [complete, setComplete] = useState(false);
  const downloaded = mb >= TOTAL;

  useEffect(() => {
    if (paused || downloaded) return;
    let last = performance.now();
    const t = setInterval(() => {
      const now = performance.now();
      const dt = (now - last) / 1000;
      last = now;
      setMb((v) => Math.min(TOTAL, v + dt * SPEED));
    }, 120);
    return () => clearInterval(t);
  }, [paused, downloaded]);

  useEffect(() => {
    if (!downloaded) return;
    const t = setTimeout(() => {
      setComplete(true);
      haptic('success');
    }, 700);
    return () => clearTimeout(t);
  }, [downloaded, haptic]);

  let offset = 0;
  const rows = STARTER.map((m) => {
    const a = offset;
    const b = (offset += m.sizeMB);
    let status: 'queued' | 'loading' | 'verifying' | 'done' = 'queued';
    if (complete || mb >= b + 2.2) status = 'done';
    else if (mb >= b) status = 'verifying';
    else if (mb > a) status = 'loading';
    return { m, status, pct: Math.round(((mb - a) / m.sizeMB) * 100) };
  });
  const left = Math.max(1, Math.ceil((TOTAL - mb) / SPEED));

  return (
    <>
      <div className="safe-t flex min-h-0 flex-1 flex-col overflow-y-auto px-6 *:shrink-0">
        <div className="pt-10">
          <p className="eyebrow text-muted">{complete ? 'Setup complete' : 'Setup'}</p>
          <h1 className="display mt-3 text-[2.375rem]" aria-live="polite">
            {complete ? (
              "You're all set."
            ) : (
              <>
                Setting up <span className="whitespace-nowrap">RealScale-Pro</span>
              </>
            )}
          </h1>
          <p className="mt-3 text-base leading-relaxed text-body">
            {complete ? 'The Starter Pack is installed. Everything now works offline.' : 'Downloading the Starter Pack over Wi-Fi so upscaling works offline.'}
          </p>
        </div>

        <div className="mt-6 divide-y divide-hairline-soft overflow-hidden rounded-lg border border-hairline">
          {rows.map(({ m, status, pct }) => (
            <div key={m.id} className="flex min-h-13 items-center gap-3 px-4 py-2">
              <div className="min-w-0 flex-1">
                <p className={cn('text-[0.9375rem] leading-snug font-medium', status === 'queued' ? 'text-muted' : 'text-ink')}>{m.name}</p>
                <p className="font-mono text-xs text-muted">{fmtMB(m.sizeMB)}</p>
              </div>
              <span className="tnum shrink-0 text-right text-[0.8125rem]">
                {status === 'queued' && <span className="text-muted">Queued</span>}
                {status === 'loading' && <span className="font-mono text-ink">{pct}%</span>}
                {status === 'verifying' && <span className="text-body">Verifying</span>}
                {status === 'done' && (
                  <span className="anim-fade inline-flex items-center gap-1.5 text-ink">
                    <Check className="size-4" />
                    Done
                  </span>
                )}
              </span>
            </div>
          ))}
        </div>

        <div className="mt-6 pb-4">
          <div className="flex items-end justify-between gap-3">
            <p className="display tnum text-[2.75rem]">{Math.floor((mb / TOTAL) * 100)}%</p>
            <p className="tnum pb-1.5 text-right text-[0.8125rem] leading-snug text-muted">
              {mb.toFixed(1)} of {TOTAL.toFixed(1)} MB
              <br />
              {complete ? 'Verified' : downloaded ? 'Verifying files' : paused ? 'Paused' : `${SPEED} MB/s · ${left} s left`}
            </p>
          </div>
          <ProgressBar value={mb / TOTAL} label="Starter Pack download" className="mt-3" />
        </div>
      </div>

      <div className="safe-b shrink-0 px-6 pt-4">
        {complete ? (
          <Button
            variant="primary"
            size="lg"
            full
            className="anim-rise"
            onClick={() => {
              set({ onboarded: true, installed: STARTER_IDS });
              setPhase('app');
            }}
          >
            Start upscaling
          </Button>
        ) : (
          <Button size="lg" full disabled={downloaded} onClick={() => setPaused((v) => !v)} icon={paused ? <Play className="size-4.5" /> : <Pause className="size-4.5" />}>
            {paused ? 'Resume download' : 'Pause download'}
          </Button>
        )}
      </div>
    </>
  );
}
