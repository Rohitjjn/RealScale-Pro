import { useEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react';
import { ChevronsLeftRight } from 'lucide-react';
import { cn } from '../utils/cn';
import type { Photo } from '../lib/data';

const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v));

/** Sizes a box to the photo's aspect ratio without exceeding a height budget. */
export function fitBox(photo: Pick<Photo, 'w' | 'h'>, maxH: string): CSSProperties {
  return { aspectRatio: `${photo.w} / ${photo.h}`, width: `min(100%, calc(${maxH} * ${(photo.w / photo.h).toFixed(4)}))` };
}

/* ---------- BeforeAfterSlider ---------- */
export function BeforeAfter({ before, after, alt, initial = 50, sweepFrom, showOriginal, className, style }: { before: string; after: string; alt: string; initial?: number; sweepFrom?: number; showOriginal?: boolean; className?: string; style?: CSSProperties }) {
  const ref = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState(sweepFrom ?? initial);
  const [animating, setAnimating] = useState(sweepFrom != null);
  const dragging = useRef(false);

  // On first show, the divider sweeps in to reveal the result.
  useEffect(() => {
    if (sweepFrom == null) return;
    const a = setTimeout(() => setPos(initial), 380);
    const b = setTimeout(() => setAnimating(false), 1500);
    return () => {
      clearTimeout(a);
      clearTimeout(b);
    };
  }, [sweepFrom, initial]);

  const moveTo = (clientX: number) => {
    const r = ref.current?.getBoundingClientRect();
    if (!r) return;
    setPos(clamp(((clientX - r.left) / r.width) * 100, 0, 100));
  };
  const onKey = (e: KeyboardEvent) => {
    if (e.key === 'ArrowLeft') setPos((p) => clamp(p - 5, 0, 100));
    if (e.key === 'ArrowRight') setPos((p) => clamp(p + 5, 0, 100));
  };

  const shown = showOriginal ? 100 : pos;
  // Slow sweep on first reveal, quick snap for keyboard steps and "hold for original", none while dragging.
  const easeNow = dragging.current ? '' : animating ? 'transition-[clip-path,left] duration-[900ms] ease-spring' : 'transition-[clip-path,left,opacity] duration-200';

  return (
    <div
      ref={ref}
      className={cn('relative touch-pan-y overflow-hidden bg-card select-none', className)}
      style={style}
      onPointerDown={(e) => {
        dragging.current = true;
        setAnimating(false);
        e.currentTarget.setPointerCapture(e.pointerId);
        moveTo(e.clientX);
      }}
      onPointerMove={(e) => dragging.current && moveTo(e.clientX)}
      onPointerUp={() => (dragging.current = false)}
      onPointerCancel={() => (dragging.current = false)}
    >
      <img src={after} alt={alt} draggable={false} className="absolute inset-0 size-full object-cover" />
      <img src={before} alt="" aria-hidden draggable={false} className={cn('absolute inset-0 size-full object-cover', easeNow)} style={{ clipPath: `inset(0 ${100 - shown}% 0 0)` }} />

      <span className={cn('pointer-events-none absolute top-3 left-3 rounded-full bg-stage/75 px-2.5 py-1 text-xs font-medium text-on-dark transition-opacity duration-200', shown < 24 && 'opacity-0')}>Before</span>
      <span className={cn('pointer-events-none absolute top-3 right-3 rounded-full bg-stage/75 px-2.5 py-1 text-xs font-medium text-on-dark transition-opacity duration-200', shown > 76 && 'opacity-0')}>After</span>

      <div className={cn('absolute inset-y-0 w-0.5 -translate-x-1/2 bg-white shadow-[0_0_0_0.5px_rgba(20,20,19,0.3)]', easeNow, showOriginal && 'opacity-0')} style={{ left: `${shown}%` }}>
        <button
          type="button"
          role="slider"
          aria-label="Compare before and after"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(pos)}
          aria-valuetext={`${Math.round(pos)}% original`}
          onKeyDown={onKey}
          className="absolute top-1/2 left-1/2 flex size-10 -translate-x-1/2 -translate-y-1/2 cursor-ew-resize items-center justify-center rounded-full border border-hairline bg-canvas text-ink shadow-soft"
        >
          <ChevronsLeftRight className="size-5" />
        </button>
      </div>
    </div>
  );
}

/* ---------- ProgressFillView: tiles fill in from soft to sharp ---------- */
export function TileFill({ photo, cols, rows, done, active, className, style }: { photo: Photo; cols: number; rows: number; done: number; active: number; className?: string; style?: CSSProperties }) {
  const cells = cols * rows;
  return (
    <div className={cn('relative overflow-hidden bg-dark-soft', className)} style={{ ...style, '--tile-img': `url("${photo.src}")` } as CSSProperties}>
      <img src={photo.lo} alt="" draggable={false} className="absolute inset-0 size-full scale-[1.03] object-cover blur-[2px]" />
      <div className="absolute inset-0 grid" style={{ gridTemplateColumns: `repeat(${cols}, 1fr)`, gridTemplateRows: `repeat(${rows}, 1fr)` }}>
        {Array.from({ length: cells }, (_, i) => {
          const c = i % cols;
          const r = Math.floor(i / cols);
          const isDone = i < done;
          return (
            <div key={i} className="relative">
              <div
                className={cn('absolute -inset-[0.5px] bg-no-repeat transition-opacity duration-500', isDone ? 'opacity-100' : 'opacity-0')}
                style={{
                  backgroundImage: 'var(--tile-img)',
                  backgroundSize: `${cols * 100}% ${rows * 100}%`,
                  backgroundPosition: `${cols > 1 ? (c / (cols - 1)) * 100 : 0}% ${rows > 1 ? (r / (rows - 1)) * 100 : 0}%`,
                }}
              />
              <div className={cn('absolute inset-0 border-r border-b border-on-dark/20 transition-opacity duration-500', isDone && 'opacity-0')} />
              {i === active && <div className="anim-tile absolute inset-0 border-2 border-primary bg-primary/20" />}
            </div>
          );
        })}
      </div>
    </div>
  );
}
