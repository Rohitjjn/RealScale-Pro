import { useEffect, useRef, useState } from 'react';
import { Thermometer } from 'lucide-react';
import { TIPS } from '../lib/data';
import { fmtClock, fmtLeft } from '../lib/plan';
import { useApp, useLive } from '../lib/store';
import { TileFill, fitBox } from '../components/media';
import { BottomBar, Button, ConfirmSheet, ProgressBar, Screen, TopBar } from '../components/ui';

const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v));

export function Processing() {
  const app = useApp();
  const live = useLive();
  const { nav, addGuard } = app;
  const job = live.job;
  const [confirm, setConfirm] = useState(false);
  const [tip, setTip] = useState(0);

  useEffect(() => {
    const t = setInterval(() => setTip((i) => (i + 1) % TIPS.length), 4500);
    return () => clearInterval(t);
  }, []);

  const toBackground = () => {
    live.setBackground(true);
    nav.home();
  };
  // The system back gesture never loses progress: it keeps the job running in the background.
  const bgRef = useRef(toBackground);
  bgRef.current = toBackground;
  useEffect(() => addGuard(() => bgRef.current()), [addGuard]);

  // Hand over to the result as soon as the job is saved.
  const doneId = job?.status === 'done' ? job.resultId : undefined;
  useEffect(() => {
    if (!doneId) return;
    live.clearJob();
    nav.replace({ name: 'result', resultId: doneId });
  }, [doneId, live, nav]);

  const photo = job ? app.getPhoto(job.photoId) : undefined;
  useEffect(() => {
    if (!job || !photo) nav.home();
  }, [job, photo, nav]);
  if (!job || !photo) return null;

  const { plan } = job;
  const p = job.progress;
  const stage = p < 0.06 ? 'Preparing image' : p < 0.94 ? 'Enhancing details' : 'Finishing and saving';
  const tileProgress = clamp((p - 0.06) / (0.94 - 0.06), 0, 1);
  const tileNow = Math.min(plan.tiles, Math.floor(tileProgress * plan.tiles) + 1);

  // The grid on screen follows the real tile layout, capped so cells stay visible on a phone.
  const cols = Math.min(plan.cols, 12);
  const rows = Math.min(plan.rows, 12);
  const cells = cols * rows;
  const doneCells = Math.floor(tileProgress * cells);
  const active = tileProgress < 1 ? doneCells : -1;

  const left = plan.etaSec * (1 - p);
  const throttled = plan.etaSec > 90 && p > 0.5 && p < 0.7;

  const cancel = () => {
    setConfirm(false);
    live.cancelJob();
    nav.replace({ name: 'config', photoId: photo.id });
    app.toast('Upscale cancelled. Temporary files removed.');
  };

  return (
    <Screen>
      <TopBar title="Upscaling" />

      <div className="flex min-h-0 flex-1 flex-col overflow-y-auto overscroll-contain px-5 pb-4 *:shrink-0">
        {/* Fill animation */}
        <div className="chrome rounded-lg bg-dark p-3">
          <div className="flex justify-center">
            <TileFill photo={photo} cols={cols} rows={rows} done={doneCells} active={active} className="rounded-md" style={fitBox(photo, 'min(38dvh, 21rem)')} />
          </div>
          <div className="tnum mt-3 flex items-center justify-between gap-3 px-1 font-mono text-xs text-on-dark-soft">
            <span className="text-on-dark">
              Tile {tileNow} of {plan.tiles}
            </span>
            <span className="truncate">
              {plan.tilePx ? `${plan.tilePx} px` : 'no tiling'} · {plan.compute} · {plan.scale}×
            </span>
          </div>
        </div>

        {/* Percentage, stage and ETA */}
        <div className="mt-6 flex items-end justify-between gap-4">
          <div className="min-w-0">
            <p className="display tnum text-[4.25rem] leading-[0.9]" aria-hidden>
              {Math.floor(p * 100)}
              <span className="text-[2.25rem]">%</span>
            </p>
          </div>
          <div className="min-w-0 pb-1 text-right">
            <p className="tnum text-[0.9375rem] font-medium text-ink">{p < 0.04 ? 'Estimating…' : `About ${fmtLeft(left)} left`}</p>
            <p className="tnum mt-0.5 text-[0.8125rem] text-muted">Elapsed {fmtClock(plan.etaSec * p)}</p>
          </div>
        </div>
        <ProgressBar value={p} label="Upscale progress" className="mt-4" />
        <p className="mt-3 text-[0.9375rem] font-medium text-ink" aria-live="polite">
          {stage}
        </p>

        {throttled && (
          <div className="anim-fade mt-4 flex items-center gap-3 rounded-lg bg-card p-3.5" role="status">
            <Thermometer className="size-5 shrink-0 text-ink" />
            <p className="text-sm leading-snug text-body">Slowing down to keep your phone cool.</p>
          </div>
        )}

        <div className="mt-auto pt-6">
          <p className="eyebrow text-muted">Tip</p>
          <p key={tip} className="anim-fade mt-1.5 min-h-10 text-sm leading-relaxed text-body">
            {TIPS[tip]}
          </p>
        </div>
      </div>

      <BottomBar className="flex flex-col gap-2">
        <Button size="lg" full onClick={toBackground}>
          Continue in background
        </Button>
        <Button variant="ghost" full onClick={() => setConfirm(true)}>
          Cancel
        </Button>
      </BottomBar>

      <ConfirmSheet
        open={confirm}
        onClose={() => setConfirm(false)}
        title="Cancel this upscale?"
        body={`${Math.floor(p * 100)}% is done. Progress will be lost and you will need to start again.`}
        confirmLabel="Cancel upscale"
        cancelLabel="Keep going"
        onConfirm={cancel}
        danger
      />
    </Screen>
  );
}
