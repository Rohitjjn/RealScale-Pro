import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { BookOpen, Camera, ChevronRight, CircleHelp, Clipboard, Cloud, Ellipsis, FolderOpen, Images, ImageUpscale, Info, Layers, Settings, Share2 } from 'lucide-react';
import { cn } from '../utils/cn';
import { MODE_NAME, type ModeId, type Photo } from '../lib/data';
import { session, useApp, type Route } from '../lib/store';
import { PhotoAccessCard, useImport, useModeSwitch } from '../components/shared';
import { Badge, Button, Group, IconButton, Mark, RadioMark, Row, Screen, Sheet } from '../components/ui';

const MODES: { id: ModeId; title: string; desc: string; pro?: boolean }[] = [
  { id: 'upscale', title: 'Upscale Mode', desc: 'Fast and simple. Everyday photos. Works on any device.' },
  { id: 'super', title: 'Super Resolution Mode', desc: 'Maximum quality. Original file, no compression. For powerful devices.', pro: true },
];

export function Home() {
  const app = useApp();
  const { s, nav } = app;
  const scroller = useRef<HTMLDivElement>(null);
  const modeRef = useRef<HTMLDivElement>(null);
  const [scrolled, setScrolled] = useState(false);
  const [loading, setLoading] = useState(!session.recentsLoaded);
  const [sheet, setSheet] = useState<null | 'more' | 'camera'>(null);
  const modeSwitch = useModeSwitch();
  const toConfig = (p: Photo) => nav.push({ name: 'config', photoId: p.id });
  const imp = useImport(toConfig);

  useLayoutEffect(() => {
    if (scroller.current) scroller.current.scrollTop = session.homeScroll;
  }, []);
  useEffect(() => {
    if (!loading) return;
    const t = setTimeout(() => {
      session.recentsLoaded = true;
      setLoading(false);
    }, 600);
    return () => clearTimeout(t);
  }, [loading]);

  const go = (r: Route) => nav.push(r);
  const installedCount = s.installed.length;

  const openCamera = () => {
    if (!s.cameraAsked) setSheet('camera');
    else imp.openCamera();
  };

  const tiles = [
    { Icon: Images, title: 'Gallery', sub: 'Albums and all photos', on: () => go({ name: 'gallery' }) },
    { Icon: FolderOpen, title: 'Files', sub: 'Browse folders', on: imp.pickFile },
    { Icon: Camera, title: 'Camera', sub: 'Take a photo', on: openCamera },
    { Icon: Ellipsis, title: 'More', sub: 'Clipboard, cloud, other apps', on: () => setSheet('more') },
  ];

  return (
    <Screen>
      {imp.inputs}
      <header className={cn('safe-t relative z-10 shrink-0 border-b bg-canvas transition-colors duration-200', scrolled ? 'border-hairline' : 'border-transparent')}>
        <div className="flex h-14 items-center gap-2.5 pr-3 pl-5">
          <Mark className="size-9 shrink-0" />
          <span className="display min-w-0 truncate text-[1.375rem] leading-none font-semibold">RealScale-Pro</span>
          <span className="flex-1" />
          <IconButton label="Models" onClick={() => go({ name: 'models' })}>
            <Layers className="size-5" />
          </IconButton>
          <IconButton label="Settings" onClick={() => go({ name: 'settings' })}>
            <Settings className="size-5" />
          </IconButton>
        </div>
      </header>

      <div
        ref={scroller}
        className="min-h-0 flex-1 overflow-y-auto overscroll-contain"
        onScroll={(e) => {
          const top = e.currentTarget.scrollTop;
          session.homeScroll = top;
          setScrolled((cur) => (cur === top > 56 ? cur : top > 56));
        }}
      >
        {/* 1. Tagline */}
        <div className="px-5 pt-3 pb-6">
          <p className="text-[0.9375rem] leading-relaxed text-muted">Sharper, bigger photos. Nothing leaves your device.</p>
        </div>

        {/* 2. Recent photos */}
        <section aria-labelledby="recents-title">
          <div className="flex items-end justify-between gap-3 px-5">
            <div>
              <h2 id="recents-title" className="display text-[1.625rem]">
                Recent photos
              </h2>
              <button
                type="button"
                onClick={() => modeRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })}
                aria-label={`Active mode: ${MODE_NAME[s.mode]}. Change mode`}
                className="relative mt-2 inline-flex min-h-7 items-center gap-1.5 rounded-full bg-card px-2.5 text-[0.8125rem] font-medium text-ink before:absolute before:inset-x-0 before:-inset-y-2 before:content-[''] active:bg-strong"
              >
                <span className="size-1.5 rounded-full bg-teal" />
                {MODE_NAME[s.mode]}
              </button>
            </div>
            {s.photoAccess && (
              <button type="button" onClick={() => go({ name: 'gallery' })} className="-mr-2 inline-flex min-h-11 items-center gap-0.5 rounded-md px-2 text-sm font-medium text-link active:bg-card">
                See all
                <ChevronRight className="size-4" />
              </button>
            )}
          </div>

          {!s.photoAccess ? (
            <PhotoAccessCard className="mx-5 mt-4" />
          ) : (
            <div className="no-scrollbar mt-4 flex snap-x scroll-px-5 gap-3 overflow-x-auto px-5 pb-1" role="list" aria-label="Recent photos" aria-busy={loading}>
              {loading
                ? Array.from({ length: 4 }, (_, i) => (
                    <div key={i} className="w-[9.25rem] shrink-0" aria-hidden>
                      <div className="skeleton aspect-[4/5] rounded-lg" />
                      <div className="skeleton mt-2 h-11 rounded-md" />
                    </div>
                  ))
                : app.photos.map((p) => (
                    <div key={p.id} role="listitem" className="w-[9.25rem] shrink-0 snap-start">
                      <button type="button" onClick={() => go({ name: 'viewer', photoId: p.id })} aria-label={`View ${p.name}, ${p.w} by ${p.h} pixels`} className="relative block aspect-[4/5] w-full overflow-hidden rounded-lg bg-card">
                        <img src={p.src} alt="" loading="lazy" className="anim-fade size-full object-cover" />
                        <span className="absolute bottom-1.5 left-1.5 rounded-sm bg-stage/75 px-1.5 py-0.5 font-mono text-xs text-on-dark">
                          {p.w}×{p.h}
                        </span>
                      </button>
                      <Button full className="mt-2" onClick={() => toConfig(p)} icon={<ImageUpscale className="size-4 text-primary" />} aria-label={`Upscale ${p.name}`}>
                        Upscale
                      </Button>
                    </div>
                  ))}
            </div>
          )}
        </section>

        {/* 3. Choose another image */}
        <section className="mt-10 px-5" aria-labelledby="import-title">
          <h2 id="import-title" className="display text-[1.625rem]">
            Choose another image
          </h2>
          <div className="mt-4 grid grid-cols-2 gap-3">
            {tiles.map(({ Icon, title, sub, on }) => (
              <button key={title} type="button" onClick={on} className="flex min-h-[6.75rem] flex-col rounded-lg bg-card p-4 text-left transition-colors active:bg-strong">
                <Icon className="size-[1.375rem] text-ink" strokeWidth={1.7} />
                <span className="mt-auto pt-3 text-[0.9375rem] font-medium text-ink">{title}</span>
                <span className="mt-0.5 text-[0.8125rem] leading-snug text-body">{sub}</span>
              </button>
            ))}
          </div>
        </section>

        {/* 4. Mode selector */}
        <section ref={modeRef} className="mt-10 scroll-mt-4 px-5" aria-labelledby="mode-title">
          <h2 id="mode-title" className="display text-[1.625rem]">
            Mode
          </h2>
          <div role="radiogroup" aria-labelledby="mode-title" className="mt-4 space-y-3">
            {MODES.map((m) => {
              const on = s.mode === m.id;
              return (
                <button
                  key={m.id}
                  type="button"
                  role="radio"
                  aria-checked={on}
                  onClick={() => modeSwitch.request(m.id)}
                  className={cn('flex w-full items-start gap-4 rounded-lg border p-4 text-left transition-colors duration-200', on ? 'border-inverse bg-inverse' : 'border-hairline bg-canvas active:bg-soft')}
                >
                  <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap items-center gap-2">
                      <span className={cn('text-base font-medium', on ? 'text-on-inverse' : 'text-ink')}>{m.title}</span>
                      {m.pro && <Badge tone="amber">Pro</Badge>}
                    </span>
                    <span className={cn('mt-1 block text-sm leading-relaxed', on ? 'text-on-inverse-soft' : 'text-muted')}>{m.desc}</span>
                  </span>
                  <RadioMark checked={on} className={cn('mt-0.5', on && 'border-on-inverse bg-on-inverse text-inverse')} />
                </button>
              );
            })}
          </div>
        </section>

        {/* 5. App options */}
        <section className="mt-10 px-5" aria-label="App options">
          <Group>
            <Row icon={<Images />} title="My Upscales" value={s.results.length ? String(s.results.length) : 'Empty'} chevron onClick={() => go({ name: 'upscales' })} />
            <Row icon={<Layers />} title="Model Manager" value={`${installedCount} installed`} chevron onClick={() => go({ name: 'models' })} />
          </Group>
          <Group className="mt-3">
            <Row icon={<Settings />} title="Settings" chevron onClick={() => go({ name: 'settings' })} />
            <Row icon={<BookOpen />} title="Guide" chevron onClick={app.replayGuide} />
            <Row icon={<CircleHelp />} title="Help and feedback" chevron onClick={() => go({ name: 'help' })} />
            <Row icon={<Info />} title="About and open-source licenses" chevron onClick={() => go({ name: 'about' })} />
          </Group>
          <p className="py-8 text-center text-[0.8125rem] text-muted">RealScale-Pro 2.0 · Works fully offline</p>
        </section>
      </div>

      {modeSwitch.sheet}

      <Sheet open={sheet === 'more'} onClose={() => setSheet(null)} title="More ways to import">
        <Group className="mb-4">
          <Row
            icon={<Clipboard />}
            title="Paste from clipboard"
            sub="Use an image you copied"
            chevron
            onClick={() => {
              setSheet(null);
              void imp.paste();
            }}
          />
          <Row
            icon={<Cloud />}
            title="Cloud drives and other folders"
            sub="Opens the system picker"
            chevron
            onClick={() => {
              setSheet(null);
              imp.pickFile();
            }}
          />
          <Row icon={<Share2 />} title="Shared from other apps" sub="Send an image to RealScale-Pro from any app" trailing={<Badge tone="outline">Later</Badge>} />
        </Group>
      </Sheet>

      <Sheet
        open={sheet === 'camera'}
        onClose={() => setSheet(null)}
        title="Use the camera"
        footer={
          <div className="flex flex-col gap-2">
            <Button
              variant="primary"
              size="lg"
              full
              onClick={() => {
                app.set({ cameraAsked: true });
                setSheet(null);
                imp.openCamera();
              }}
            >
              Allow camera
            </Button>
            <Button variant="ghost" full onClick={() => setSheet(null)}>
              Not now
            </Button>
          </div>
        }
      >
        <p className="text-[0.9375rem] leading-relaxed text-body">Take a photo and upscale it right away. The camera is used only while you are taking the picture.</p>
        <p className="mt-3 pb-4 text-[0.9375rem] leading-relaxed text-body">Photos you take stay on this device.</p>
      </Sheet>

    </Screen>
  );
}
