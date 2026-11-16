import { useMemo, useState } from 'react';
import { FolderOpen, Share2, Trash2 } from 'lucide-react';
import { cn } from '../utils/cn';
import type { Photo } from '../lib/data';
import { useApp } from '../lib/store';
import { PhotoAccessCard, ShareSheet, shareFile, useImport } from '../components/shared';
import { BottomBar, Button, ConfirmSheet, Mark, RadioMark, Screen, TopBar, useScrolled } from '../components/ui';
import { fmtDims, fmtMB } from '../lib/plan';

/* ---------- My Upscales (7.3) ---------- */
export function Upscales() {
  const app = useApp();
  const { s, nav } = app;
  const results = s.results.filter((r) => app.getPhoto(r.photoId));
  const [selecting, setSelecting] = useState(false);
  const [sel, setSel] = useState<string[]>([]);
  const [confirm, setConfirm] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);
  const { scrolled, onScroll } = useScrolled();

  const exit = () => {
    setSelecting(false);
    setSel([]);
  };
  const toggle = (id: string) => setSel((cur) => (cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id]));
  const first = results.find((r) => sel.includes(r.id));
  const firstPhoto = first ? app.getPhoto(first.photoId) : undefined;

  const share = async () => {
    if (!first || !firstPhoto) return;
    if (sel.length === 1) {
      if (!(await shareFile(firstPhoto.src, first.fileName))) setShareOpen(true);
      return;
    }
    try {
      const files = await Promise.all(
        results
          .filter((r) => sel.includes(r.id))
          .map(async (r) => {
            const blob = await (await fetch(app.getPhoto(r.photoId)?.src ?? '')).blob();
            return new File([blob], r.fileName, { type: blob.type || 'image/jpeg' });
          }),
      );
      if (navigator.canShare?.({ files })) {
        await navigator.share({ files });
        return;
      }
    } catch (e) {
      if ((e as Error)?.name === 'AbortError') return;
    }
    app.toast('Sharing several images uses the system share sheet on a phone.');
  };

  return (
    <Screen>
      <TopBar
        title={selecting ? `${sel.length} selected` : 'My Upscales'}
        onBack={selecting ? exit : () => nav.pop()}
        close={selecting}
        scrolled={scrolled}
        right={
          results.length > 0 && (
            <button
              type="button"
              onClick={() => (selecting ? setSel(sel.length === results.length ? [] : results.map((r) => r.id)) : setSelecting(true))}
              className="mr-1 min-h-11 rounded-md px-3 text-[0.9375rem] font-medium text-link active:bg-card"
            >
              {selecting ? (sel.length === results.length ? 'Clear' : 'Select all') : 'Select'}
            </button>
          )
        }
      />

      {results.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center px-8 pb-16 text-center">
          <div className="flex size-20 items-center justify-center rounded-xl bg-card">
            <Mark className="size-9 text-ink" />
          </div>
          <h2 className="display mt-6 text-[2rem]">No upscales yet</h2>
          <p className="mt-2 max-w-64 text-[0.9375rem] leading-relaxed text-muted">Every result is saved here and in Pictures/RealScale-Pro on your device.</p>
          <Button variant="primary" className="mt-6" onClick={() => nav.home()}>
            Choose a photo
          </Button>
        </div>
      ) : (
        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 pb-6" onScroll={onScroll}>
          <p className="pb-3 text-[0.8125rem] text-muted">
            {results.length} {results.length === 1 ? 'image' : 'images'} · Saved in Pictures/RealScale-Pro
          </p>
          <div className="grid grid-cols-3 gap-1.5">
            {results.map((r) => {
              const p = app.getPhoto(r.photoId) as Photo;
              const on = sel.includes(r.id);
              return (
                <button
                  key={r.id}
                  type="button"
                  aria-label={`${p.name}, upscaled ${r.scale} times${selecting ? (on ? ', selected' : ', not selected') : ''}`}
                  aria-pressed={selecting ? on : undefined}
                  onClick={() => (selecting ? toggle(r.id) : nav.push({ name: 'result', resultId: r.id }))}
                  className="relative aspect-square overflow-hidden rounded-md bg-card"
                >
                  <img src={p.src} alt="" loading="lazy" className={cn('size-full object-cover transition-transform duration-200', on && 'scale-90 rounded-sm')} />
                  <span className="absolute bottom-1.5 left-1.5 rounded-sm bg-stage/75 px-1.5 py-0.5 font-mono text-xs text-on-dark">{r.scale}×</span>
                  {selecting && <RadioMark checked={on} className={cn('absolute top-1.5 right-1.5', !on && 'border-white bg-stage/40')} />}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {selecting && (
        <BottomBar className="grid grid-cols-2 gap-2.5">
          <Button disabled={!sel.length} onClick={share} icon={<Share2 className="size-4.5" />}>
            Share
          </Button>
          <Button variant="danger" disabled={!sel.length} onClick={() => setConfirm(true)} icon={<Trash2 className="size-4.5" />} className="disabled:text-muted-soft">
            Delete
          </Button>
        </BottomBar>
      )}

      {first && firstPhoto && <ShareSheet open={shareOpen} onClose={() => setShareOpen(false)} src={firstPhoto.src} name={first.fileName} meta={`${fmtDims(first.outW, first.outH)} · ${fmtMB(first.sizeMB)}`} />}

      <ConfirmSheet
        open={confirm}
        onClose={() => setConfirm(false)}
        title={`Delete ${sel.length} ${sel.length === 1 ? 'result' : 'results'}?`}
        body="The upscaled files are removed from Pictures/RealScale-Pro. Your original photos are not affected."
        confirmLabel="Delete"
        onConfirm={() => {
          app.deleteResults(sel);
          app.toast(`${sel.length} ${sel.length === 1 ? 'result' : 'results'} deleted`);
          setConfirm(false);
          exit();
        }}
        danger
      />
    </Screen>
  );
}

/* ---------- In-app gallery (4.2 "See all", 4.4 Gallery tile) ---------- */
export function Gallery() {
  const app = useApp();
  const { nav, s } = app;
  const [tab, setTab] = useState<'photos' | 'albums'>('photos');
  const [album, setAlbum] = useState<string | null>(null);
  const { scrolled, onScroll } = useScrolled();
  const pick = (p: Photo) => nav.push({ name: 'config', photoId: p.id });
  const imp = useImport(pick);

  const albums = useMemo(() => {
    const map = new Map<string, Photo[]>();
    app.photos.forEach((p) => map.set(p.album, [...(map.get(p.album) ?? []), p]));
    return [...map.entries()];
  }, [app.photos]);
  const list = album ? app.photos.filter((p) => p.album === album) : app.photos;
  const showGrid = tab === 'photos' || album !== null;

  return (
    <Screen>
      {imp.inputs}
      <TopBar title={album ?? 'Gallery'} onBack={album ? () => setAlbum(null) : () => nav.pop()} scrolled={scrolled} />

      {!s.photoAccess ? (
        <div className="px-5 pt-2">
          <PhotoAccessCard />
          <Button full className="mt-3" icon={<FolderOpen className="size-4.5" />} onClick={imp.pickFile}>
            Pick with the system picker instead
          </Button>
        </div>
      ) : (
        <>
          {!album && (
            <div className="flex shrink-0 gap-1 px-5 pb-3" role="tablist" aria-label="Gallery view">
              {(['photos', 'albums'] as const).map((t) => (
                <button
                  key={t}
                  type="button"
                  role="tab"
                  aria-selected={tab === t}
                  onClick={() => setTab(t)}
                  className={cn('min-h-10 rounded-md px-3.5 text-sm font-medium capitalize transition-colors', tab === t ? 'bg-card text-ink' : 'text-muted active:bg-soft')}
                >
                  {t}
                </button>
              ))}
            </div>
          )}

          <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 pb-6" onScroll={onScroll}>
            {showGrid ? (
              <>
                <p className="pb-3 text-[0.8125rem] text-muted">
                  {list.length} {list.length === 1 ? 'photo' : 'photos'} · Tap one to upscale
                </p>
                <div className="grid grid-cols-3 gap-1.5">
                  {list.map((p) => (
                    <button key={p.id} type="button" onClick={() => pick(p)} aria-label={`Upscale ${p.name}, ${p.w} by ${p.h} pixels`} className="relative aspect-square overflow-hidden rounded-md bg-card">
                      <img src={p.src} alt="" loading="lazy" className="size-full object-cover" />
                    </button>
                  ))}
                </div>
              </>
            ) : (
              <div className="grid grid-cols-2 gap-x-3 gap-y-5">
                {albums.map(([name, photos]) => (
                  <button key={name} type="button" onClick={() => setAlbum(name)} className="text-left">
                    <div className="aspect-square overflow-hidden rounded-lg bg-card">
                      <img src={photos[0].src} alt="" loading="lazy" className="size-full object-cover" />
                    </div>
                    <p className="mt-2 text-[0.9375rem] font-medium text-ink">{name}</p>
                    <p className="text-[0.8125rem] text-muted">
                      {photos.length} {photos.length === 1 ? 'photo' : 'photos'}
                    </p>
                  </button>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </Screen>
  );
}
