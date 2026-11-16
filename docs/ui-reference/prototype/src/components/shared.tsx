import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Check, Download, Gauge, Images, Lock, Trash2 } from 'lucide-react';
import { cn } from '../utils/cn';
import { DEVICE, MODELS, MODE_NAME, fitOf, modelById, type ImageType, type Model, type ModeId, type Photo } from '../lib/data';
import { fmtDims, fmtMB, fmtMP, recommendedId } from '../lib/plan';
import { useApp, useLive } from '../lib/store';
import { Badge, Button, Chip, Dot, Group, IconButton, RadioMark, Ring, Sheet } from './ui';

/* ---------- Mode switching (with the first-time Super Resolution sheet) ---------- */
export function useModeSwitch() {
  const app = useApp();
  const live = useLive();
  const [open, setOpen] = useState(false);

  const request = (m: ModeId) => {
    if (m === app.s.mode) return;
    app.haptic('light');
    if (m === 'super' && !app.s.superIntroSeen) {
      setOpen(true);
      return;
    }
    app.set({ mode: m });
  };

  const confirm = () => {
    app.set({ mode: 'super', superIntroSeen: true });
    if (!app.s.installed.includes('esrgan-x4plus')) live.download('esrgan-x4plus');
    setOpen(false);
  };

  const pro = modelById('esrgan-x4plus');
  const sheet = (
    <Sheet
      open={open}
      onClose={() => setOpen(false)}
      title="Super Resolution Mode"
      footer={
        <div className="flex flex-col gap-2">
          <Button variant="primary" size="lg" full onClick={confirm}>
            Use Super Resolution
          </Button>
          <Button variant="ghost" full onClick={() => setOpen(false)}>
            Stay in Upscale Mode
          </Button>
        </div>
      }
    >
      <p className="text-[0.9375rem] leading-relaxed text-body">Recommended for high-end devices and advanced users.</p>
      <div className="mt-4 flex items-center gap-3 rounded-lg bg-card p-4">
        <Gauge className="size-5 shrink-0 text-ink" />
        <div className="min-w-0 flex-1">
          <p className="text-[0.9375rem] font-medium text-ink">Your device: {DEVICE.label}</p>
          <p className="mt-0.5 text-[0.8125rem] leading-snug text-body">OK fit. Large photos may take a few minutes.</p>
        </div>
        <Badge tone="strong">
          <Dot tone="ok" />
          OK
        </Badge>
      </div>
      <ul className="mt-4 space-y-2.5 pb-4 text-[0.9375rem] leading-snug text-body">
        {['Uses your original file. Nothing is compressed.', 'Higher-quality models and lossless PNG output.', `Downloads ${pro.name} (${fmtMB(pro.sizeMB)}) over Wi-Fi.`].map((t) => (
          <li key={t} className="flex gap-2.5">
            <Check className="mt-0.5 size-4 shrink-0 text-ink" />
            <span>{t}</span>
          </li>
        ))}
      </ul>
    </Sheet>
  );

  return { request, sheet };
}

/* ---------- Importing from Files, Camera and Clipboard ---------- */
export function useImport(onPhoto: (p: Photo) => void) {
  const app = useApp();
  const fileRef = useRef<HTMLInputElement>(null);
  const camRef = useRef<HTMLInputElement>(null);

  const handle = async (file?: File | null) => {
    if (!file) return;
    try {
      onPhoto(await app.importFile(file));
    } catch (e) {
      app.haptic('warning');
      app.toast(e instanceof Error && e.message === 'tiny' ? 'This image is too small. Pick one at least 64 px on each side.' : "This file can't be opened. Try another image.");
    }
  };

  const paste = async () => {
    try {
      const items = await navigator.clipboard.read();
      for (const item of items) {
        const type = item.types.find((t) => t.startsWith('image/'));
        if (type) {
          const blob = await item.getType(type);
          await handle(new File([blob], `clipboard.${type.split('/')[1] || 'png'}`, { type }));
          return;
        }
      }
      app.toast('No image found on the clipboard.');
    } catch {
      app.toast('No image found on the clipboard.');
    }
  };

  const inputs = (
    <>
      <input ref={fileRef} type="file" accept="image/jpeg,image/png,image/webp,image/heic,image/bmp" className="hidden" tabIndex={-1} aria-hidden onChange={(e) => { void handle(e.target.files?.[0]); e.target.value = ''; }} />
      <input ref={camRef} type="file" accept="image/*" capture="environment" className="hidden" tabIndex={-1} aria-hidden onChange={(e) => { void handle(e.target.files?.[0]); e.target.value = ''; }} />
    </>
  );

  return { inputs, pickFile: () => fileRef.current?.click(), openCamera: () => camRef.current?.click(), paste };
}

/* ---------- Sharing ---------- */
export async function shareFile(src: string, name: string): Promise<boolean> {
  try {
    const blob = await (await fetch(src)).blob();
    const file = new File([blob], name, { type: blob.type || 'image/jpeg' });
    if (navigator.canShare?.({ files: [file] })) {
      await navigator.share({ files: [file], title: name });
      return true;
    }
  } catch (e) {
    if ((e as Error)?.name === 'AbortError') return true;
  }
  return false;
}

/** Fallback when the system share sheet is not available (desktop browsers). */
export function ShareSheet({ open, onClose, src, name, meta }: { open: boolean; onClose: () => void; src: string; name: string; meta: string }) {
  const app = useApp();
  return (
    <Sheet open={open} onClose={onClose} title="Share">
      <div className="flex items-center gap-3 rounded-lg bg-card p-3">
        <img src={src} alt="" className="size-14 shrink-0 rounded-md object-cover" />
        <div className="min-w-0 flex-1">
          <p className="truncate font-mono text-[0.8125rem] text-ink">{name}</p>
          <p className="mt-0.5 text-[0.8125rem] text-body">{meta}</p>
        </div>
      </div>
      <p className="mt-3 text-[0.8125rem] leading-snug text-muted">On a phone this opens the system share sheet with the full-resolution file.</p>
      <div className="mt-4 flex flex-col gap-2 pb-3">
        <a href={src} download={name} onClick={onClose} className="inline-flex min-h-13 items-center justify-center gap-2 rounded-md bg-primary px-5 text-base font-semibold text-on-primary active:bg-primary-active">
          <Download className="size-5" />
          Save image
        </a>
        <Button
          full
          onClick={() => {
            navigator.clipboard?.writeText(name).then(
              () => app.toast('File name copied'),
              () => app.toast("Couldn't copy the file name"),
            );
            onClose();
          }}
        >
          Copy file name
        </Button>
      </div>
    </Sheet>
  );
}

/* ---------- Photo details ---------- */
export function DetailList({ rows }: { rows: [string, ReactNode][] }) {
  return (
    <dl className="divide-y divide-hairline-soft">
      {rows.map(([k, v]) => (
        <div key={k} className="flex items-baseline justify-between gap-4 py-3">
          <dt className="shrink-0 text-sm text-muted">{k}</dt>
          <dd className="min-w-0 text-right text-sm break-words text-ink">{v}</dd>
        </div>
      ))}
    </dl>
  );
}

export function PhotoDetailsSheet({ open, onClose, photo }: { open: boolean; onClose: () => void; photo: Photo }) {
  return (
    <Sheet open={open} onClose={onClose} title="Details">
      <DetailList
        rows={[
          ['Name', <span className="font-mono text-[0.8125rem]">{photo.name}</span>],
          ['Resolution', `${fmtDims(photo.w, photo.h)} · ${fmtMP((photo.w * photo.h) / 1e6)}`],
          ['File size', fmtMB(photo.sizeMB)],
          ['Format', photo.ext === 'jpg' ? 'JPEG' : photo.ext.toUpperCase()],
          ['Date', photo.date],
          ['Album', photo.album],
        ]}
      />
      <div className="pb-2" />
    </Sheet>
  );
}

/* ---------- Photo access (permission missing state) ---------- */
export function PhotoAccessCard({ className }: { className?: string }) {
  const app = useApp();
  return (
    <div className={cn('rounded-lg bg-card p-5', className)}>
      <Images className="size-6 text-ink" />
      <h3 className="mt-3 text-base font-medium text-ink">Allow photo access</h3>
      <p className="mt-1 text-sm leading-relaxed text-body">See your latest photos here and upscale one in a single tap. Gallery, Files and Camera work without it.</p>
      <Button
        variant="primary"
        className="mt-4"
        onClick={() => {
          app.set({ photoAccess: true });
          app.toast('Photo access allowed');
        }}
      >
        Allow photo access
      </Button>
    </div>
  );
}

/* ---------- ModelListRow ---------- */
export function ModelRow({ m, variant, recommended, selected, onSelect, onDelete }: { m: Model; variant: 'pick' | 'manage'; recommended?: boolean; selected?: boolean; onSelect?: () => void; onDelete?: () => void }) {
  const app = useApp();
  const live = useLive();
  const installed = app.s.installed.includes(m.id);
  const progress = live.downloads[m.id];
  const downloading = progress !== undefined;
  const fit = fitOf(m);
  const unavailable = fit.tone === 'no';

  let action: ReactNode;
  if (downloading)
    action = (
      <button type="button" onClick={() => live.cancelDownload(m.id)} aria-label={`Cancel download of ${m.name}`} className="flex min-h-11 items-center gap-2">
        <span className="tnum font-mono text-xs text-muted">{Math.round(progress * 100)}%</span>
        <Ring value={progress} label={`Downloading ${m.name}`} />
      </button>
    );
  else if (installed)
    action =
      variant === 'pick' ? (
        <RadioMark checked={!!selected} className="mt-0.5" />
      ) : m.required ? (
        <Badge tone="outline">Required</Badge>
      ) : (
        <IconButton label={`Delete ${m.name}`} onClick={onDelete}>
          <Trash2 className="size-4.5" />
        </IconButton>
      );
  else if (unavailable)
    action = (
      <span className="flex size-10 items-center justify-center text-muted-soft">
        <Lock className="size-4.5" aria-hidden />
      </span>
    );
  else
    action = (
      <Button size="sm" onClick={() => live.download(m.id)} icon={<Download className="size-4" />} aria-label={`Download ${m.name}, ${fmtMB(m.sizeMB)}`}>
        {fmtMB(m.sizeMB)}
      </Button>
    );

  const info = (
    <div className="min-w-0 flex-1 text-left">
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <span className={cn('text-[0.9375rem] leading-snug font-medium', unavailable ? 'text-muted' : 'text-ink')}>{m.name}</span>
        {recommended && <Badge tone="amber">Recommended</Badge>}
      </div>
      <p className="mt-0.5 text-[0.8125rem] leading-snug text-body">{m.desc}</p>
      <p className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted">
        <span className="font-mono">{fmtMB(m.sizeMB)}</span>
        <span>{m.badge}</span>
        {variant === 'manage' && m.license !== 'To be verified' && <span>{m.license}</span>}
      </p>
      {m.blocked ? (
        <p className="mt-1 text-xs leading-snug text-muted">{m.blocked}</p>
      ) : variant === 'manage' && installed ? (
        <p className="mt-1 flex items-center gap-1.5 text-xs font-medium text-ink">
          <Check className="size-3.5" />
          Installed
        </p>
      ) : (
        <p className="mt-1 flex items-center gap-1.5 text-xs text-muted">
          <Dot tone={fit.tone} />
          {fit.label}
        </p>
      )}
    </div>
  );

  if (variant === 'pick' && installed && !downloading)
    return (
      <button type="button" role="radio" aria-checked={!!selected} onClick={onSelect} className={cn('flex w-full items-start gap-3 px-4 py-3.5 transition-colors active:bg-soft', selected && 'bg-soft')}>
        {info}
        <span className="shrink-0">{action}</span>
      </button>
    );
  return (
    <div className="flex items-start gap-3 px-4 py-3.5">
      {info}
      <div className="shrink-0">{action}</div>
    </div>
  );
}

/* ---------- Model picker (Manual and Advanced levels) ---------- */
export function ModelPickerSheet({ open, onClose, mode, type, selectedId, onSelect }: { open: boolean; onClose: () => void; mode: ModeId; type: ImageType; selectedId: string; onSelect: (id: string) => void }) {
  const app = useApp();
  const installed = app.s.installed;
  const [filter, setFilter] = useState<'all' | 'photo' | 'anime' | 'installed'>('all');
  const [pending, setPending] = useState<string | null>(null);
  const rec = recommendedId(mode, type);

  // A model downloaded from this list becomes the selection once it is installed.
  useEffect(() => {
    if (pending && installed.includes(pending)) {
      onSelect(pending);
      setPending(null);
    }
  }, [pending, installed, onSelect]);

  const rank = (m: Model) => (m.id === rec ? 0 : installed.includes(m.id) ? 1 : fitOf(m).tone === 'no' ? 3 : 2);
  const list = MODELS.filter((m) => m.kind === 'photo' || m.kind === 'anime')
    .filter((m) => (filter === 'all' ? true : filter === 'installed' ? installed.includes(m.id) : m.kind === filter))
    .sort((a, b) => rank(a) - rank(b));

  return (
    <Sheet open={open} onClose={onClose} title="Choose a model" tall>
      <p className="text-sm leading-relaxed text-body">
        Every model in the catalog for {MODE_NAME[mode]}. Download any of them here and it becomes your selection.
      </p>
      <div className="no-scrollbar -mx-5 mt-4 flex gap-2 overflow-x-auto px-5">
        {(['all', 'photo', 'anime', 'installed'] as const).map((f) => (
          <Chip key={f} selected={filter === f} onClick={() => setFilter(f)} className="shrink-0 capitalize">
            {f}
          </Chip>
        ))}
      </div>
      <Group className="mt-4 mb-4">
        {filter === 'all' && (
          <button
            type="button"
            role="radio"
            aria-checked={selectedId === 'auto'}
            onClick={() => {
              onSelect('auto');
              onClose();
            }}
            className={cn('flex w-full items-start gap-3 px-4 py-3.5 text-left transition-colors active:bg-soft', selectedId === 'auto' && 'bg-soft')}
          >
            <div className="min-w-0 flex-1">
              <span className="text-[0.9375rem] font-medium text-ink">Automatic</span>
              <p className="mt-0.5 text-[0.8125rem] leading-snug text-body">Picks the best installed model for each image and your device.</p>
            </div>
            <RadioMark checked={selectedId === 'auto'} className="mt-0.5" />
          </button>
        )}
        {list.map((m) => (
          <div key={m.id} onClickCapture={() => !installed.includes(m.id) && fitOf(m).tone !== 'no' && setPending(m.id)}>
            <ModelRow
              m={m}
              variant="pick"
              recommended={m.id === rec}
              selected={selectedId === m.id}
              onSelect={() => {
                onSelect(m.id);
                onClose();
              }}
            />
          </div>
        ))}
        {list.length === 0 && <p className="px-4 py-6 text-center text-sm text-muted">No models match this filter.</p>}
      </Group>
    </Sheet>
  );
}
