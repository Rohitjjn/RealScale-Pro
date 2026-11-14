import { useState } from 'react';
import { Check, Ellipsis, Expand, Eye, FileText, Images, ImagePlus, RotateCcw, Share2, Trash2 } from 'lucide-react';
import { MODE_NAME, modelById } from '../lib/data';
import { fmtDims, fmtEta, fmtMB } from '../lib/plan';
import { useApp } from '../lib/store';
import { BeforeAfter, fitBox } from '../components/media';
import { DetailList, ShareSheet, shareFile } from '../components/shared';
import { Badge, BottomBar, Button, ConfirmSheet, Group, IconButton, Row, Screen, Sheet, TopBar, useScrolled } from '../components/ui';

export function ResultScreen({ resultId }: { resultId: string }) {
  const app = useApp();
  const { s, nav } = app;
  const r = s.results.find((x) => x.id === resultId);
  const photo = r ? app.getPhoto(r.photoId) : undefined;
  const [hold, setHold] = useState(false);
  const [sheet, setSheet] = useState<null | 'more' | 'details' | 'share' | 'delete'>(null);
  const { scrolled, onScroll } = useScrolled();

  if (!r || !photo)
    return (
      <Screen>
        <TopBar title="Result" onBack={() => nav.pop()} />
        <div className="flex flex-1 flex-col items-center justify-center px-8 text-center">
          <p className="display text-[1.75rem]">This result is no longer available.</p>
          <p className="mt-2 text-sm leading-relaxed text-muted">It may have been deleted.</p>
          <Button className="mt-5" onClick={() => nav.home()}>
            Back to Home
          </Button>
        </div>
      </Screen>
    );

  const model = modelById(r.modelId);
  const meta = `${fmtDims(r.outW, r.outH)} · ${fmtMB(r.sizeMB)}`;
  const share = async () => {
    app.haptic('light');
    if (!(await shareFile(photo.src, r.fileName))) setSheet('share');
  };
  const again = () => {
    if (s.mode !== r.mode) app.set({ mode: r.mode });
    nav.replace({ name: 'config', photoId: photo.id });
  };

  return (
    <Screen>
      <TopBar
        title="Result"
        onBack={() => nav.home()}
        close
        scrolled={scrolled}
        right={
          <IconButton label="More options" onClick={() => setSheet('more')}>
            <Ellipsis className="size-5" />
          </IconButton>
        }
      />

      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 pb-6" onScroll={onScroll}>
        <BeforeAfter before={photo.lo} after={photo.src} alt={`Upscaled ${photo.name}`} sweepFrom={94} initial={50} showOriginal={hold} className="mx-auto rounded-xl" style={fitBox(photo, 'min(46dvh, 25rem)')} />

        <div className="mt-3 flex items-center justify-between gap-3">
          <button
            type="button"
            onPointerDown={() => setHold(true)}
            onPointerUp={() => setHold(false)}
            onPointerLeave={() => setHold(false)}
            onPointerCancel={() => setHold(false)}
            onKeyDown={(e) => (e.key === ' ' || e.key === 'Enter') && setHold(true)}
            onKeyUp={() => setHold(false)}
            onContextMenu={(e) => e.preventDefault()}
            aria-pressed={hold}
            className="inline-flex min-h-11 touch-none items-center gap-2 rounded-md bg-card px-3.5 text-sm font-medium text-ink select-none active:bg-strong"
          >
            <Eye className="size-4.5" />
            Hold to see original
          </button>
          <IconButton label="Open full screen" onClick={() => nav.push({ name: 'viewer', photoId: photo.id, single: true })}>
            <Expand className="size-4.5" />
          </IconButton>
        </div>

        <div className="mt-7">
          <p className="eyebrow text-muted">
            Upscaled {r.scale}× in {fmtEta(r.timeSec)}
          </p>
          <p className="display tnum mt-2 text-[2.625rem]">{fmtDims(r.outW, r.outH)}</p>
          <p className="mt-1.5 text-[0.9375rem] text-muted">
            {fmtMB(r.sizeMB)} · up from {fmtDims(photo.w, photo.h)}
          </p>
          <Badge className="mt-4 py-1">
            <Check className="size-3.5" />
            Saved to Pictures/RealScale-Pro
          </Badge>
        </div>

        <div className="mt-5 border-t border-hairline">
          <DetailList
            rows={[
              ['Model', model.name],
              ['Mode', MODE_NAME[r.mode]],
              ['Format', r.format],
            ]}
          />
        </div>
      </div>

      <BottomBar className="flex flex-col gap-2.5">
        <Button variant="primary" size="lg" full onClick={share} icon={<Share2 className="size-5" />}>
          Share
        </Button>
        <div className="grid grid-cols-2 gap-2.5">
          <Button onClick={again} icon={<RotateCcw className="size-4 shrink-0" />} className="px-2 text-sm whitespace-nowrap">
            Upscale again
          </Button>
          <Button onClick={() => nav.home()} icon={<ImagePlus className="size-4 shrink-0" />} className="px-2 text-sm whitespace-nowrap">
            New image
          </Button>
        </div>
      </BottomBar>

      <Sheet open={sheet === 'more'} onClose={() => setSheet(null)} title="More">
        <Group className="mb-4">
          <Row
            icon={<Images />}
            title="Open in My Upscales"
            chevron
            onClick={() => {
              setSheet(null);
              nav.replace({ name: 'upscales' });
            }}
          />
          <Row icon={<FileText />} title="Details" chevron onClick={() => setSheet('details')} />
          <Row icon={<Trash2 />} title={<span className="text-error">Delete result</span>} onClick={() => setSheet('delete')} />
        </Group>
      </Sheet>

      <Sheet open={sheet === 'details'} onClose={() => setSheet(null)} title="Details">
        <DetailList
          rows={[
            ['File', <span className="font-mono text-[0.8125rem] break-all">{r.fileName}</span>],
            ['Saved to', 'Pictures/RealScale-Pro'],
            ['Resolution', fmtDims(r.outW, r.outH)],
            ['File size', fmtMB(r.sizeMB)],
            ['Scale', `${r.scale}×`],
            ['Model', model.name],
            ['Time taken', fmtEta(r.timeSec)],
            ['Original', `${photo.name} · ${fmtDims(photo.w, photo.h)}`],
          ]}
        />
        <div className="pb-2" />
      </Sheet>

      <ShareSheet open={sheet === 'share'} onClose={() => setSheet(null)} src={photo.src} name={r.fileName} meta={meta} />

      <ConfirmSheet
        open={sheet === 'delete'}
        onClose={() => setSheet(null)}
        title="Delete this result?"
        body="The upscaled file is removed from Pictures/RealScale-Pro. Your original photo is not affected."
        confirmLabel="Delete result"
        onConfirm={() => {
          setSheet(null);
          app.deleteResults([r.id]);
          nav.pop();
          app.toast('Result deleted');
        }}
        danger
      />
    </Screen>
  );
}
