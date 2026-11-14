import type { CSSProperties } from 'react';
import { Check, ChevronRight } from 'lucide-react';
import { fmtLeft } from './lib/plan';
import { AppProvider, useApp, useLive, type Route } from './lib/store';
import { Toast } from './components/ui';
import { Config } from './screens/Config';
import { Home } from './screens/Home';
import { Gallery, Upscales } from './screens/Library';
import { Models } from './screens/Models';
import { Onboarding } from './screens/Onboarding';
import { Processing } from './screens/Processing';
import { ResultScreen } from './screens/Result';
import { About, Help, SettingsScreen } from './screens/Settings';
import { Viewer } from './screens/Viewer';

function CurrentScreen({ route }: { route: Route }) {
  switch (route.name) {
    case 'home':
      return <Home />;
    case 'viewer':
      return <Viewer photoId={route.photoId} single={route.single} />;
    case 'config':
      return <Config photoId={route.photoId} />;
    case 'processing':
      return <Processing />;
    case 'result':
      return <ResultScreen resultId={route.resultId} />;
    case 'upscales':
      return <Upscales />;
    case 'gallery':
      return <Gallery />;
    case 'models':
      return <Models />;
    case 'settings':
      return <SettingsScreen />;
    case 'help':
      return <Help />;
    case 'about':
      return <About />;
  }
}

const CORNERS = [
  ['top-0 left-0', '100% 100%'],
  ['top-0 right-0', '0% 100%'],
  ['bottom-0 left-0', '100% 0%'],
  ['bottom-0 right-0', '0% 0%'],
] as const;

const routeKey = (r: Route, depth: number) => `${depth}:${r.name}:${'photoId' in r ? r.photoId : ''}${'resultId' in r ? r.resultId : ''}`;

/** A job that continues in the background stays one tap away on every screen. */
function JobStrip() {
  const app = useApp();
  const live = useLive();
  const job = live.job;
  if (!job) return null;
  const photo = app.getPhoto(job.photoId);
  const done = job.status === 'done';
  const pct = Math.floor(job.progress * 100);

  const open = () => {
    if (done && job.resultId) {
      live.clearJob();
      app.nav.push({ name: 'result', resultId: job.resultId });
    } else {
      live.setBackground(false);
      app.nav.push({ name: 'processing' });
    }
  };

  return (
    <button type="button" onClick={open} className="chrome safe-b relative flex w-full shrink-0 items-center gap-3 bg-dark px-4 pt-3 text-left text-on-dark" style={{ '--safe-b': 'env(safe-area-inset-bottom)' } as CSSProperties}>
      <span className="absolute inset-x-0 top-0 h-0.5 bg-dark-elevated" aria-hidden>
        <span className="block h-full bg-primary" style={{ width: `${done ? 100 : pct}%` }} />
      </span>
      {photo && <img src={photo.src} alt="" className="size-10 shrink-0 rounded-sm object-cover" />}
      <span className="min-w-0 flex-1" role="status">
        <span className="tnum flex items-center gap-1.5 text-sm font-medium">
          {done && <Check className="size-4 text-success" />}
          {done ? 'Upscale finished' : `Upscaling · ${pct}%`}
        </span>
        <span className="tnum block truncate text-xs text-on-dark-soft">{done ? 'Saved to Pictures/RealScale-Pro. Tap to view.' : `About ${fmtLeft(job.plan.etaSec * (1 - job.progress))} left · ${photo?.name ?? ''}`}</span>
      </span>
      <ChevronRight className="size-5 shrink-0 text-on-dark-soft" aria-hidden />
    </button>
  );
}

function Shell() {
  const app = useApp();
  const live = useLive();
  const top = app.nav.top;
  const strip = !!live.job && top.name !== 'processing' && (live.job.background || live.job.status === 'done');

  return (
    <div className="flex h-dvh w-full items-stretch justify-center desk:items-center">
      <div
        ref={app.setDevice}
        className="relative isolate flex h-full w-full max-w-[30rem] flex-col overflow-hidden bg-canvas text-ink desk:h-[min(54rem,calc(100dvh-3rem))] desk:w-[25rem]"
      >
        {app.phase !== 'app' ? (
          <Onboarding />
        ) : (
          <>
            <div className="relative min-h-0 flex-1" style={strip ? ({ '--safe-b': '0px' } as CSSProperties) : undefined}>
              <div key={routeKey(top, app.nav.stack.length)} className="absolute inset-0">
                <CurrentScreen route={top} />
              </div>
            </div>
            {strip && <JobStrip />}
          </>
        )}
        <Toast />
        {/* Desktop preview only: the phone-shaped block gets its rounded corners from painted caps,
            so scrolling layers are never clipped by a rounded mask. */}
        {CORNERS.map(([pos, at]) => (
          <span key={pos} aria-hidden className={`pointer-events-none absolute z-[70] hidden size-9 desk:block ${pos}`} style={{ background: `radial-gradient(circle at ${at}, transparent 35.25px, var(--c-backdrop) 36px)` }} />
        ))}
      </div>
    </div>
  );
}

export default function App() {
  return (
    <AppProvider>
      <Shell />
    </AppProvider>
  );
}
