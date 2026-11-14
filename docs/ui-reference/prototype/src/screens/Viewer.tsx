import { useEffect, useLayoutEffect, useMemo, useRef, useState, type PointerEvent as RPointerEvent } from 'react';
import { ArrowLeftRight, ChevronLeft, ChevronRight, ImageUpscale, Info, Share2 } from 'lucide-react';
import { cn } from '../utils/cn';
import { MODE_NAME, type Photo } from '../lib/data';
import { fmtDims, fmtMB } from '../lib/plan';
import { useApp } from '../lib/store';
import { PhotoDetailsSheet, ShareSheet, shareFile, useModeSwitch } from '../components/shared';
import { Button, IconButton, Screen } from '../components/ui';

const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v));

/** Pinch, double-tap and drag to zoom and pan. Pull down to close. */
function ZoomPan({ src, alt, active, onTap, onDismiss, onZoom }: { src: string; alt: string; active: boolean; onTap: () => void; onDismiss: () => void; onZoom: (z: boolean) => void }) {
  const box = useRef<HTMLDivElement>(null);
  const img = useRef<HTMLImageElement>(null);
  const g = useRef({ s: 1, x: 0, y: 0, pull: 0, pts: new Map<number, { x: number; y: number }>(), d0: 1, s0: 1, x0: 0, y0: 0, px: 0, py: 0, moved: false, tapAt: 0, timer: 0 });
  const [zoomed, setZoomed] = useState(false);

  const apply = (animate: boolean) => {
    const el = img.current;
    if (!el) return;
    const v = g.current;
    el.style.transition = animate ? 'transform 0.3s cubic-bezier(0.22, 1, 0.36, 1)' : 'none';
    el.style.transform = `translate3d(${v.x}px, ${v.y + v.pull}px, 0) scale(${v.s})`;
  };
  const bound = () => {
    const v = g.current;
    const r = box.current?.getBoundingClientRect();
    if (!r) return;
    const mx = (r.width * (v.s - 1)) / 2;
    const my = (r.height * (v.s - 1)) / 2;
    v.x = clamp(v.x, -mx, mx);
    v.y = clamp(v.y, -my, my);
  };
  const settle = () => {
    const v = g.current;
    if (v.s < 1.05) {
      v.s = 1;
      v.x = 0;
      v.y = 0;
    }
    bound();
    apply(true);
    setZoomed(v.s > 1);
    onZoom(v.s > 1);
  };

  useEffect(() => {
    if (active) return;
    const v = g.current;
    v.s = 1;
    v.x = 0;
    v.y = 0;
    v.pull = 0;
    apply(false);
    setZoomed(false);
  }, [active]);
  useEffect(() => {
    const v = g.current;
    return () => clearTimeout(v.timer);
  }, []);

  const onDown = (e: RPointerEvent<HTMLDivElement>) => {
    const v = g.current;
    v.pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    try {
      e.currentTarget.setPointerCapture(e.pointerId);
    } catch {
      /* ignore */
    }
    if (v.pts.size === 1) {
      v.px = e.clientX;
      v.py = e.clientY;
      v.x0 = v.x;
      v.y0 = v.y;
      v.moved = false;
    } else if (v.pts.size === 2) {
      const [a, b] = [...v.pts.values()];
      v.d0 = Math.hypot(a.x - b.x, a.y - b.y) || 1;
      v.s0 = v.s;
      v.moved = true;
    }
  };

  const onMove = (e: RPointerEvent<HTMLDivElement>) => {
    const v = g.current;
    if (!v.pts.has(e.pointerId)) return;
    v.pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (v.pts.size >= 2) {
      const [a, b] = [...v.pts.values()];
      v.s = clamp(v.s0 * (Math.hypot(a.x - b.x, a.y - b.y) / v.d0), 1, 4);
      bound();
      apply(false);
      return;
    }
    const dx = e.clientX - v.px;
    const dy = e.clientY - v.py;
    if (Math.abs(dx) + Math.abs(dy) > 8) v.moved = true;
    if (v.s > 1) {
      v.x = v.x0 + dx;
      v.y = v.y0 + dy;
      bound();
      apply(false);
    } else if (dy > 0 && Math.abs(dy) > Math.abs(dx)) {
      v.pull = dy;
      apply(false);
    }
  };

  const onUp = (e: RPointerEvent<HTMLDivElement>, cancelled: boolean) => {
    const v = g.current;
    if (!v.pts.has(e.pointerId)) return;
    v.pts.delete(e.pointerId);
    if (v.pts.size > 0) {
      const [a] = [...v.pts.values()];
      v.px = a.x;
      v.py = a.y;
      v.x0 = v.x;
      v.y0 = v.y;
      return;
    }
    if (v.pull > 0) {
      const far = v.pull > 96;
      v.pull = 0;
      if (far && !cancelled) {
        onDismiss();
        return;
      }
    }
    if (!v.moved && !cancelled) {
      const now = performance.now();
      if (now - v.tapAt < 300) {
        clearTimeout(v.timer);
        v.tapAt = 0;
        if (v.s > 1) {
          v.s = 1;
          v.x = 0;
          v.y = 0;
        } else {
          const r = e.currentTarget.getBoundingClientRect();
          v.s = 2.5;
          v.x = -(e.clientX - (r.left + r.width / 2)) * 1.5;
          v.y = -(e.clientY - (r.top + r.height / 2)) * 1.5;
        }
      } else {
        v.tapAt = now;
        v.timer = window.setTimeout(onTap, 300);
      }
    }
    settle();
  };

  return (
    <div
      ref={box}
      className="flex size-full items-center justify-center overflow-hidden"
      style={{ touchAction: zoomed ? 'none' : 'pan-x' }}
      onPointerDown={onDown}
      onPointerMove={onMove}
      onPointerUp={(e) => onUp(e, false)}
      onPointerCancel={(e) => onUp(e, true)}
    >
      <img ref={img} src={src} alt={alt} draggable={false} className="size-full object-contain will-change-transform select-none" />
    </div>
  );
}

export function Viewer({ photoId, single }: { photoId: string; single?: boolean }) {
  const app = useApp();
  const { nav, s } = app;
  const list = useMemo<Photo[]>(() => (single ? app.photos.filter((p) => p.id === photoId) : app.photos), [single, photoId, app.photos]);
  const start = Math.max(0, list.findIndex((p) => p.id === photoId));
  const [index, setIndex] = useState(start);
  const [chrome, setChrome] = useState(true);
  const [zoomed, setZoomed] = useState(false);
  const [sheet, setSheet] = useState<null | 'details' | 'share'>(null);
  const scroller = useRef<HTMLDivElement>(null);
  const modeSwitch = useModeSwitch();
  const photo = list[index] ?? list[0];

  useLayoutEffect(() => {
    const el = scroller.current;
    if (el) el.scrollLeft = start * el.clientWidth;
  }, [start]);

  const goTo = (i: number) => {
    const el = scroller.current;
    if (!el) return;
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    el.scrollTo({ left: clamp(i, 0, list.length - 1) * el.clientWidth, behavior: reduce ? 'auto' : 'smooth' });
  };

  const indexRef = useRef(index);
  indexRef.current = index;
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') goTo(indexRef.current - 1);
      if (e.key === 'ArrowRight') goTo(indexRef.current + 1);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [list.length]);

  if (!photo) return null;

  const share = async () => {
    if (!(await shareFile(photo.src, photo.name))) setSheet('share');
  };
  const bar = cn('absolute inset-x-0 z-10 transition-opacity duration-200', chrome ? 'opacity-100' : 'pointer-events-none opacity-0');

  return (
    <Screen className="chrome bg-stage text-on-dark">
      <div
        ref={scroller}
        className={cn('no-scrollbar absolute inset-0 flex snap-x snap-mandatory', zoomed ? 'overflow-hidden' : 'overflow-x-auto')}
        onScroll={(e) => setIndex(Math.round(e.currentTarget.scrollLeft / e.currentTarget.clientWidth))}
      >
        {list.map((p, i) => (
          <div key={p.id} className="size-full shrink-0 snap-center" aria-hidden={i !== index}>
            <ZoomPan src={p.src} alt={p.name} active={i === index} onTap={() => setChrome((c) => !c)} onDismiss={() => nav.pop()} onZoom={setZoomed} />
          </div>
        ))}
      </div>

      {/* Top bar */}
      <div className={cn(bar, 'safe-t top-0 bg-gradient-to-b from-stage/85 to-transparent pb-8')}>
        <div className="flex h-14 items-center gap-3 px-3">
          <IconButton label="Back" variant="dark" onClick={() => nav.pop()}>
            <ChevronLeft className="size-6" />
          </IconButton>
          <div className="min-w-0 flex-1">
            <p className="truncate font-mono text-[0.8125rem] text-on-dark">{photo.name}</p>
            <p className="truncate text-xs text-on-dark-soft">
              {fmtDims(photo.w, photo.h)} · {fmtMB(photo.sizeMB)} · {photo.date}
            </p>
          </div>
          {list.length > 1 && (
            <span className="tnum shrink-0 pr-1 font-mono text-xs text-on-dark-soft">
              {index + 1}/{list.length}
            </span>
          )}
        </div>
      </div>

      {/* Previous and next, for mouse and keyboard users */}
      {list.length > 1 && !zoomed && (
        <div className={cn(bar, 'pointer-events-none top-1/2 flex -translate-y-1/2 justify-between px-3 pointer-coarse:hidden')}>
          <IconButton label="Previous photo" variant="dark" className={cn(chrome && 'pointer-events-auto', index === 0 && 'invisible')} onClick={() => goTo(index - 1)}>
            <ChevronLeft className="size-5" />
          </IconButton>
          <IconButton label="Next photo" variant="dark" className={cn(chrome && 'pointer-events-auto', index === list.length - 1 && 'invisible')} onClick={() => goTo(index + 1)}>
            <ChevronRight className="size-5" />
          </IconButton>
        </div>
      )}

      {/* Bottom bar */}
      <div className={cn(bar, 'safe-b bottom-0 bg-gradient-to-t from-stage/90 to-transparent px-4 pt-10')}>
        {single ? (
          <div className="flex items-center gap-3">
            <IconButton label="Share" variant="dark" className="size-13" onClick={share}>
              <Share2 className="size-5" />
            </IconButton>
            <Button variant="dark" size="lg" className="flex-1" onClick={() => nav.pop()}>
              Done
            </Button>
          </div>
        ) : (
          <>
            <button
              type="button"
              onClick={() => modeSwitch.request(s.mode === 'upscale' ? 'super' : 'upscale')}
              aria-label={`Mode: ${MODE_NAME[s.mode]}. Tap to switch`}
              className="mb-3 inline-flex min-h-9 items-center gap-2 rounded-full bg-dark-elevated/90 px-3.5 text-[0.8125rem] font-medium text-on-dark active:bg-dark-soft"
            >
              <span className="size-1.5 rounded-full bg-teal" />
              {MODE_NAME[s.mode]}
              <ArrowLeftRight className="size-3.5 text-on-dark-soft" />
            </button>
            <div className="flex items-center gap-3">
              <IconButton label="Details" variant="dark" className="size-13" onClick={() => setSheet('details')}>
                <Info className="size-5" />
              </IconButton>
              <IconButton label="Share" variant="dark" className="size-13" onClick={share}>
                <Share2 className="size-5" />
              </IconButton>
              <Button variant="primary" size="lg" className="flex-1" icon={<ImageUpscale className="size-5" />} onClick={() => nav.push({ name: 'config', photoId: photo.id })}>
                Upscale
              </Button>
            </div>
          </>
        )}
      </div>

      {modeSwitch.sheet}
      <PhotoDetailsSheet open={sheet === 'details'} onClose={() => setSheet(null)} photo={photo} />
      <ShareSheet open={sheet === 'share'} onClose={() => setSheet(null)} src={photo.src} name={photo.name} meta={`${fmtDims(photo.w, photo.h)} · ${fmtMB(photo.sizeMB)}`} />
    </Screen>
  );
}
