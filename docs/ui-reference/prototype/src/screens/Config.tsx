import { useEffect, useState, type CSSProperties, type ReactNode } from 'react';
import { ChevronRight, CircleAlert, Crop, Download, Expand, FolderOpen, Gauge, Replace, RotateCcw, TriangleAlert } from 'lucide-react';
import { cn } from '../utils/cn';
import { DEVICE, MODELS, MODE_NAME, type Model, type Photo } from '../lib/data';
import { TILE_PRESETS, defaultPrefs, fmtDims, fmtEta, fmtMB, fmtMP, makePlan, recommendedId, type JobPrefs, type Level, type Plan } from '../lib/plan';
import { useApp, useLive } from '../lib/store';
import { ModelPickerSheet, useImport } from '../components/shared';
import { Badge, BottomBar, Button, Chip, Dot, Field, Group, IconButton, ProgressBar, Range, Screen, Segmented, Sheet, Stepper, SwitchRow, TopBar, useScrolled } from '../components/ui';

const cap = (t: string) => t[0].toUpperCase() + t.slice(1);
const CATALOG_COUNT = MODELS.filter((m) => m.kind === 'photo' || m.kind === 'anime').length;

function Sub({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="mt-3">
      <p className="text-sm text-ink">{label}</p>
      {children}
    </div>
  );
}

function Line({ k, v }: { k: string; v: ReactNode }) {
  return (
    <div className="flex gap-3">
      <dt className="w-14 shrink-0 text-body">{k}</dt>
      <dd className="min-w-0 flex-1 text-ink">{v}</dd>
    </div>
  );
}

/** Shown when the planner prefers a model that is not installed yet. */
function WantedHint({ wanted, current }: { wanted: Model; current: Model }) {
  const live = useLive();
  const p = live.downloads[wanted.id];
  return (
    <div className="mt-3 flex items-center gap-3 rounded-md bg-canvas p-3">
      <div className="min-w-0 flex-1">
        <p className="text-sm leading-snug font-medium text-ink">{p !== undefined ? `Downloading ${wanted.name}` : `${wanted.name} is recommended here`}</p>
        {p !== undefined ? (
          <>
            <p className="tnum mt-0.5 text-xs text-muted">
              {Math.round(p * 100)}% of {fmtMB(wanted.sizeMB)}
            </p>
            <ProgressBar value={p} label={`Downloading ${wanted.name}`} className="mt-2" />
          </>
        ) : (
          <p className="mt-0.5 text-xs leading-snug text-muted">Using {current.name} until it is installed.</p>
        )}
      </div>
      {p === undefined && (
        <Button size="sm" onClick={() => live.download(wanted.id)} icon={<Download className="size-4" />} aria-label={`Download ${wanted.name}, ${fmtMB(wanted.sizeMB)}`}>
          {fmtMB(wanted.sizeMB)}
        </Button>
      )}
    </div>
  );
}

export function Config({ photoId }: { photoId: string }) {
  const app = useApp();
  const live = useLive();
  const { s, nav } = app;
  const photo = app.getPhoto(photoId);
  const mode = s.mode;
  const prefs = s.prefs[mode];
  const setP = (patch: Partial<JobPrefs>) => app.setPrefs(mode, patch);

  const [detecting, setDetecting] = useState(true);
  const [large, setLarge] = useState<'optimize' | 'full'>('optimize');
  const [sheet, setSheet] = useState<null | 'models' | 'check' | 'swap' | 'crop'>(null);
  const { scrolled, onScroll } = useScrolled();
  const imp = useImport((p) => nav.replace({ name: 'config', photoId: p.id }));

  useEffect(() => {
    const t = setTimeout(() => setDetecting(false), 650);
    return () => clearTimeout(t);
  }, []);

  if (!photo)
    return (
      <Screen>
        <TopBar title="Image unavailable" onBack={() => nav.pop()} />
        <div className="flex flex-1 flex-col items-center justify-center px-8 text-center">
          <p className="display text-[1.75rem]">This image is no longer available.</p>
          <p className="mt-2 text-sm leading-relaxed text-muted">It may have been moved or deleted. Pick another one to continue.</p>
          <Button className="mt-5" onClick={() => nav.home()}>
            Back to Home
          </Button>
        </div>
      </Screen>
    );

  const plan = makePlan(photo, mode, prefs, s.installed, { large, compute: s.settings.compute, format: s.settings.format });
  const level = prefs.level;
  const manual = level !== 'default';
  const adv = level === 'advanced';
  const rawMP = (photo.w * photo.h) / 1e6;
  const busy = live.job?.status === 'running';
  const blockedReason = detecting ? 'Analyzing photo…' : busy ? 'Another upscale is running' : null;

  const setLevel = (l: Level) => {
    app.haptic('light');
    setP({ level: l });
  };

  const start = (force = false) => {
    if (blockedReason) return;
    if (!force && plan.check.level !== 'green') {
      app.haptic('warning');
      setSheet('check');
      return;
    }
    app.haptic('medium');
    live.startJob(photo, plan, mode);
    nav.replace({ name: 'processing' });
  };

  return (
    <Screen>
      {imp.inputs}
      <TopBar
        title={MODE_NAME[mode]}
        onBack={() => nav.pop()}
        scrolled={scrolled}
        right={
          <IconButton label="Choose a different image" onClick={() => setSheet('swap')}>
            <Replace className="size-5" />
          </IconButton>
        }
      />

      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 pb-6" onScroll={onScroll}>
        {/* 5.1 Image preview */}
        <div className="relative">
          <button
            type="button"
            onClick={() => nav.push({ name: 'viewer', photoId: photo.id, single: true })}
            aria-label={`Open ${photo.name} in full view`}
            className="block w-full overflow-hidden rounded-xl bg-card"
            style={{ aspectRatio: `${photo.w} / ${photo.h}`, maxHeight: 'min(34dvh, 20rem)' }}
          >
            <img src={photo.src} alt="" className="size-full object-contain" />
          </button>
          <span className="pointer-events-none absolute right-3 bottom-3 flex size-9 items-center justify-center rounded-full bg-stage/75 text-on-dark" aria-hidden>
            <Expand className="size-4" />
          </span>
        </div>
        <div className="mt-2.5 flex items-baseline justify-between gap-3">
          <p className="min-w-0 truncate font-mono text-[0.8125rem] text-ink">{photo.name}</p>
          <p className="tnum shrink-0 text-[0.8125rem] text-muted">
            {fmtDims(photo.w, photo.h)} · {fmtMB(photo.sizeMB)}
          </p>
        </div>

        {/* 6.2 Super Resolution banner */}
        {mode === 'super' && (
          <div className="chrome mt-5 flex items-center gap-3 rounded-lg bg-dark p-4 text-on-dark">
            <Gauge className="size-5 shrink-0" />
            <p className="min-w-0 flex-1 text-sm leading-snug">Recommended for high-end devices and advanced users.</p>
            <Badge tone="dark">
              <Dot tone="ok" />
              OK fit
            </Badge>
          </div>
        )}

        {/* 5.3 Setting level */}
        <div className="mt-5">
          <Segmented<Level>
            label="Setting level"
            value={level}
            onChange={setLevel}
            options={[
              { value: 'default', label: 'Default' },
              { value: 'manual', label: 'Manual' },
              { value: 'advanced', label: 'Advanced' },
            ]}
          />
        </div>

        {/* A. Default */}
        {!manual && (
          <div className="anim-fade mt-4 rounded-lg bg-card p-4" aria-live="polite">
            <div className="flex items-center justify-between gap-3">
              <p className="eyebrow text-body">{detecting ? 'Analyzing' : photo.lowConfidence ? 'Not sure' : 'Detected'}</p>
              {!detecting && (
                <button type="button" onClick={() => setLevel('manual')} className="-my-3 -mr-2 min-h-11 rounded-md px-2 text-sm font-medium text-link active:bg-strong">
                  Change
                </button>
              )}
            </div>
            {detecting ? (
              <div className="mt-3 space-y-3" aria-label="Analyzing photo">
                <div className="h-7 w-3/4 rounded-sm bg-strong" />
                <div className="h-3 w-full rounded-sm bg-strong" />
                <div className="h-3 w-5/6 rounded-sm bg-strong" />
                <div className="h-3 w-2/3 rounded-sm bg-strong" />
              </div>
            ) : (
              <>
                <p className="display mt-1.5 text-[1.75rem]">{photo.lowConfidence ? `Using ${cap(plan.type)} · 2×` : `${cap(plan.type)} · 2× · Auto settings`}</p>
                <dl className="mt-3 space-y-2 border-t border-muted-soft/25 pt-3 text-[0.8125rem] leading-snug">
                  <Line k="Model" v={plan.model.name} />
                  <Line k="Tiles" v={`${plan.tilePx} px · ${plan.compute}`} />
                  <Line k="Input" v={plan.inputNote} />
                  <Line k="Output" v={`${fmtDims(plan.outW, plan.outH)} · ${plan.format}`} />
                </dl>
                {plan.wanted && <WantedHint wanted={plan.wanted} current={plan.model} />}
              </>
            )}
          </div>
        )}

        {/* B. Manual */}
        {manual && (
          <div className="anim-fade mt-6 space-y-6">
            {plan.overLimit && (
              <div className="rounded-lg border border-hairline p-4">
                <div className="flex gap-3">
                  <TriangleAlert className="mt-0.5 size-5 shrink-0 text-ink" />
                  <div>
                    <p className="text-[0.9375rem] font-medium text-ink">Large photo · {fmtMP(rawMP)}</p>
                    <p className="mt-0.5 text-[0.8125rem] leading-snug text-muted">
                      Above the {DEVICE.inputLimitMP} MP comfortable limit for your device. Optimizing resizes a temporary copy; the original stays untouched.
                    </p>
                  </div>
                </div>
                <div className="mt-3">
                  <Segmented
                    label="Large photo handling"
                    value={large}
                    onChange={setLarge}
                    options={[
                      { value: 'optimize', label: 'Optimize size' },
                      { value: 'full', label: 'Full size' },
                    ]}
                  />
                </div>
              </div>
            )}

            <Field label="Image type" hint={detecting ? 'Detecting…' : `Detected: ${cap(photo.type)}${photo.lowConfidence ? ' (not sure)' : ''}`}>
              <div className="grid grid-cols-3 gap-2">
                {(['auto', 'photo', 'anime'] as const).map((t) => (
                  <Chip key={t} selected={prefs.type === t} onClick={() => { app.haptic('light'); setP({ type: t }); }} className="w-full">
                    {cap(t)}
                  </Chip>
                ))}
              </div>
            </Field>

            <Field label="Model" hint={`${CATALOG_COUNT} models in the catalog. Tap to browse, download or switch.`}>
              <button type="button" onClick={() => setSheet('models')} aria-label={`Model: ${plan.model.name}. Browse all models`} className="flex w-full items-center gap-3 rounded-lg border border-hairline p-4 text-left transition-colors active:bg-soft">
                <div className="min-w-0 flex-1">
                  <p className="text-[0.9375rem] leading-snug font-medium text-ink">{plan.model.name}</p>
                  <p className="mt-0.5 text-[0.8125rem] leading-snug text-muted">
                    {plan.model.desc} · {plan.model.badge}
                  </p>
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {plan.isAuto && <Badge>Automatic</Badge>}
                    {plan.model.id === recommendedId(mode, plan.type) && <Badge tone="amber">Recommended</Badge>}
                  </div>
                </div>
                <ChevronRight className="size-5 shrink-0 text-muted" aria-hidden />
              </button>
              {plan.wanted && (
                <div className="mt-2 rounded-lg bg-card px-1 pb-1">
                  <WantedHint wanted={plan.wanted} current={plan.model} />
                </div>
              )}
            </Field>

            <Field
              label="Scale"
              hint={
                <>
                  Output {fmtDims(plan.outW, plan.outH)} · {fmtMP(plan.outMP)} · about {fmtEta(plan.etaSec)}
                  {plan.extraResize && <span className="block">5× uses an extra resize step.</span>}
                </>
              }
            >
              <div className="grid grid-cols-4 gap-2">
                {([2, 3, 4, 5] as const).map((n) => (
                  <Chip key={n} selected={prefs.scale === n} onClick={() => { app.haptic('light'); setP({ scale: n }); }} className="w-full text-base">
                    {n}×
                  </Chip>
                ))}
              </div>
            </Field>
          </div>
        )}

        {/* C. Advanced */}
        {adv && <Advanced prefs={prefs} setP={setP} plan={plan} photo={photo} isSuper={mode === 'super'} onCrop={() => setSheet('crop')} />}
      </div>

      {/* 5.5 Upscale button */}
      <BottomBar>
        <Button variant="primary" size="lg" full disabled={!!blockedReason} onClick={() => start()} className="min-h-15 flex-col gap-0.5 py-2">
          {blockedReason ? (
            <span>{blockedReason}</span>
          ) : (
            <>
              <span className="text-[1.0625rem] leading-tight font-semibold">Upscale</span>
              <span className="tnum text-[0.8125rem] leading-tight font-medium">
                {fmtDims(plan.outW, plan.outH)} · about {fmtEta(plan.etaSec)}
              </span>
            </>
          )}
        </Button>
      </BottomBar>

      <ModelPickerSheet open={sheet === 'models'} onClose={() => setSheet(null)} mode={mode} type={plan.type} selectedId={plan.isAuto ? 'auto' : plan.model.id} onSelect={(id) => setP({ modelId: id })} />

      <CheckSheet
        open={sheet === 'check'}
        onClose={() => setSheet(null)}
        plan={plan}
        onContinue={() => {
          setSheet(null);
          start(true);
        }}
        onScale={(n) => {
          setP({ scale: n as JobPrefs['scale'], level: manual ? level : 'manual' });
          setSheet(null);
        }}
        onFaster={() => {
          if (!manual) setP({ level: 'manual' });
          setSheet('models');
        }}
        onPng={() => {
          setP({ format: 'png' });
          setSheet(null);
        }}
        onOther={() => setSheet('swap')}
        onOptimize={() => {
          setLarge('optimize');
          setSheet(null);
        }}
      />

      <Sheet open={sheet === 'swap'} onClose={() => setSheet(null)} title="Choose another image" tall>
        <div className="grid grid-cols-3 gap-1.5">
          {app.photos.map((p) => (
            <button
              key={p.id}
              type="button"
              aria-label={`Use ${p.name}`}
              aria-current={p.id === photo.id}
              onClick={() => {
                setSheet(null);
                if (p.id !== photo.id) nav.replace({ name: 'config', photoId: p.id });
              }}
              className={cn('relative aspect-square overflow-hidden rounded-md bg-card', p.id === photo.id && 'ring-2 ring-inverse ring-offset-2 ring-offset-canvas')}
            >
              <img src={p.src} alt="" loading="lazy" className="size-full object-cover" />
            </button>
          ))}
        </div>
        <Button
          full
          className="mt-4 mb-4"
          icon={<FolderOpen className="size-4.5" />}
          onClick={() => {
            setSheet(null);
            imp.pickFile();
          }}
        >
          Browse files
        </Button>
      </Sheet>

      <CropSheet open={sheet === 'crop'} onClose={() => setSheet(null)} photo={photo} model={plan.model} />
    </Screen>
  );
}

/* ---------- C. Advanced options ---------- */
function Advanced({ prefs, setP, plan, photo, isSuper, onCrop }: { prefs: JobPrefs; setP: (p: Partial<JobPrefs>) => void; plan: Plan; photo: Photo; isSuper: boolean; onCrop: () => void }) {
  const app = useApp();
  const lossy = plan.format !== 'PNG';
  const formatOptions: { value: JobPrefs['format']; label: string }[] = isSuper
    ? [
        { value: 'default', label: 'PNG' },
        { value: 'jpeg', label: 'JPEG' },
        { value: 'webp', label: 'WebP' },
      ]
    : [
        { value: 'default', label: 'Source' },
        { value: 'jpeg', label: 'JPEG' },
        { value: 'png', label: 'PNG' },
        { value: 'webp', label: 'WebP' },
      ];

  return (
    <div className="anim-fade mt-8 space-y-6">
      <h2 className="display text-[1.625rem]">Performance</h2>

      <Field label="Tile size" hint="Fast is recommended for your device. Maximum needs a Powerful device with 12 GB or more memory.">
        <div className="grid grid-cols-2 gap-2">
          {TILE_PRESETS.map((t) => (
            <Chip key={t.id} selected={prefs.tile === t.id} disabled={!!t.disabled} onClick={() => setP({ tile: t.id })} sub={t.px ? `${t.px} px · ${t.mem}` : t.mem} className="w-full">
              {t.label}
            </Chip>
          ))}
          <Chip selected={prefs.tile === 'custom'} onClick={() => setP({ tile: 'custom' })} sub="64 to 1024 px, in steps of 32" className="col-span-2 w-full">
            Custom
          </Chip>
        </div>
        {prefs.tile === 'custom' && (
          <div className="mt-3 space-y-3 rounded-lg bg-card p-3">
            <div className="flex items-center justify-between gap-3">
              <span className="text-sm text-ink">Tile size</span>
              <Stepper label="tile size" value={prefs.tileCustom} min={64} max={1024} step={32} onChange={(v) => setP({ tileCustom: v })} format={(v) => `${v} px`} />
            </div>
            <div>
              <span className="mb-2 block text-sm text-ink">Overlap</span>
              <Segmented
                label="Tile overlap"
                value={prefs.overlap}
                onChange={(v) => setP({ overlap: v })}
                options={[
                  { value: 'auto', label: 'Auto' },
                  { value: '16', label: '16' },
                  { value: '32', label: '32' },
                  { value: '64', label: '64' },
                ]}
              />
            </div>
          </div>
        )}
      </Field>

      <Field label="Compute device" hint={`Detected ${DEVICE.gpu} with Vulkan ${DEVICE.vulkan}. Auto follows the preference in Settings (now ${plan.compute}).`}>
        <Segmented
          label="Compute device"
          value={prefs.compute}
          onChange={(v) => setP({ compute: v })}
          options={[
            { value: 'auto', label: 'Auto' },
            { value: 'gpu', label: 'GPU' },
            { value: 'cpu', label: 'CPU' },
          ]}
        />
        {prefs.compute === 'cpu' && (
          <div className="mt-3 flex items-center justify-between gap-3">
            <span className="text-sm text-ink">CPU threads</span>
            <Stepper label="CPU threads" value={prefs.threads} min={0} max={DEVICE.cores} step={1} onChange={(v) => setP({ threads: v })} format={(v) => (v === 0 ? 'Auto' : String(v))} />
          </div>
        )}
      </Field>

      <Field label="Numeric precision" hint="FP16 is faster and lighter. FP32 is more exact.">
        <Segmented
          label="Numeric precision"
          value={prefs.precision}
          onChange={(v) => setP({ precision: v })}
          options={[
            { value: 'auto', label: 'Auto' },
            { value: 'fp16', label: 'FP16' },
            { value: 'fp32', label: 'FP32' },
          ]}
        />
      </Field>

      <Group>
        <SwitchRow title="Enhanced quality (TTA)" sub="Runs the image several ways and averages. Up to 8× slower." checked={prefs.tta} onChange={(v) => setP({ tta: v })} />
      </Group>

      <h2 className="display pt-2 text-[1.625rem]">Input and output</h2>

      {!isSuper && (
        <Field label="Image compression" hint="Auto re-encodes files over 2 MB at 90% quality into a temporary copy.">
          <Segmented
            label="Image compression"
            value={prefs.compression}
            onChange={(v) => setP({ compression: v })}
            options={[
              { value: 'auto', label: 'Auto' },
              { value: 'off', label: 'Off' },
              { value: 'custom', label: 'Custom' },
            ]}
          />
          {prefs.compression === 'custom' && (
            <Sub label="Quality">
              <Range label="Compression quality" value={prefs.compressionQ} min={60} max={100} onChange={(v) => setP({ compressionQ: v })} suffix="%" />
            </Sub>
          )}
        </Field>
      )}

      <Field label="Output format" hint={isSuper ? 'PNG is lossless and the default in this mode.' : `Source keeps ${photo.ext === 'png' ? 'PNG' : 'JPEG'} for this image.`}>
        <Segmented label="Output format" value={prefs.format === 'png' && isSuper ? 'default' : prefs.format} onChange={(v) => setP({ format: v })} options={formatOptions} />
        {lossy && (
          <Sub label={`${plan.format} quality`}>
            <Range label="Output quality" value={prefs.formatQ} min={60} max={100} onChange={(v) => setP({ formatQ: v })} suffix="%" />
          </Sub>
        )}
      </Field>

      {plan.model.denoise && (
        <Field label="Denoise strength" hint={`Supported by ${plan.model.name}.`}>
          <Range label="Denoise strength" value={prefs.denoise} min={0} max={100} onChange={(v) => setP({ denoise: v })} />
        </Field>
      )}

      <Group>
        <SwitchRow title="Keep photo metadata" sub="Date, camera and location stay in the result." checked={prefs.keepMeta} onChange={(v) => setP({ keepMeta: v })} />
      </Group>

      {isSuper && (
        <>
          <h2 className="display pt-2 text-[1.625rem]">Finishing</h2>

          <button type="button" onClick={onCrop} className="flex w-full items-center gap-3 rounded-lg border border-hairline p-4 text-left transition-colors active:bg-soft">
            <Crop className="size-5 shrink-0 text-ink" />
            <span className="min-w-0 flex-1">
              <span className="block text-[0.9375rem] font-medium text-ink">Preview crop</span>
              <span className="mt-0.5 block text-[0.8125rem] leading-snug text-muted">Test these settings on a 512 × 512 area in seconds.</span>
            </span>
            <ChevronRight className="size-4 shrink-0 text-muted-soft" aria-hidden />
          </button>

          <Field label="Sharpen after upscale">
            <Range label="Sharpen after upscale" value={prefs.sharpen} min={0} max={100} onChange={(v) => setP({ sharpen: v })} />
          </Field>

          {(plan.scale === 3 || plan.scale === 5) && (
            <Field label={`Resize method for ${plan.scale}×`}>
              <Segmented
                label="Resize method"
                value={prefs.resize}
                onChange={(v) => setP({ resize: v })}
                options={[
                  { value: 'lanczos', label: 'Lanczos' },
                  { value: 'bicubic', label: 'Bicubic' },
                ]}
              />
            </Field>
          )}

          <Field label="Color profile">
            <Segmented
              label="Color profile"
              value={prefs.color}
              onChange={(v) => setP({ color: v })}
              options={[
                { value: 'keep', label: 'Keep original' },
                { value: 'srgb', label: 'Convert to sRGB' },
              ]}
            />
          </Field>

          <Field label="Output size cap" hint="Optional limit on the longest side of the result.">
            <Segmented
              label="Output size cap"
              value={prefs.cap}
              onChange={(v) => setP({ cap: v })}
              options={[
                { value: 'none', label: 'None' },
                { value: '8k', label: '8K' },
                { value: '16k', label: '16K' },
              ]}
            />
          </Field>

        </>
      )}

      <Button
        full
        icon={<RotateCcw className="size-4.5" />}
        onClick={() => {
          setP({ ...defaultPrefs(), level: prefs.level });
          app.toast('Settings reset to defaults');
        }}
      >
        Reset to defaults
      </Button>
    </div>
  );
}

/* ---------- 6.5 / 8.6 Feasibility check ---------- */
function CheckSheet({ open, onClose, plan, onContinue, onScale, onFaster, onPng, onOther, onOptimize }: { open: boolean; onClose: () => void; plan: Plan; onContinue: () => void; onScale: (n: number) => void; onFaster: () => void; onPng: () => void; onOther: () => void; onOptimize: () => void }) {
  const c = plan.check;
  if (c.level === 'green') return <Sheet open={false} onClose={onClose}>{null}</Sheet>;
  const red = c.level === 'red';
  const Icon = red ? CircleAlert : TriangleAlert;
  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={c.title}
      footer={
        <div className="flex flex-col gap-2">
          {c.level === 'amber' && c.kind === 'time' && (
            <>
              <Button variant="primary" size="lg" full onClick={onContinue}>
                Continue
              </Button>
              {plan.scale > 2 ? (
                <Button full onClick={() => onScale(plan.scale - 1)}>
                  Lower the scale to {plan.scale - 1}×
                </Button>
              ) : (
                <Button full onClick={onFaster}>
                  Pick a faster model
                </Button>
              )}
            </>
          )}
          {c.level === 'amber' && c.kind === 'format' && (
            <Button variant="primary" size="lg" full onClick={onPng}>
              Switch to PNG
            </Button>
          )}
          {c.level === 'red' &&
            (c.maxScale ? (
              <Button variant="primary" size="lg" full onClick={() => onScale(c.maxScale as number)}>
                Use {c.maxScale}× instead
              </Button>
            ) : plan.overLimit && !plan.optimized ? (
              <Button variant="primary" size="lg" full onClick={onOptimize}>
                Optimize size instead
              </Button>
            ) : (
              <Button variant="primary" size="lg" full onClick={onOther}>
                Pick another image
              </Button>
            ))}
          {red && plan.level === 'advanced' && (
            <Button variant="danger" full onClick={onContinue}>
              Continue anyway
            </Button>
          )}
          <Button variant="ghost" full onClick={onClose}>
            Cancel
          </Button>
        </div>
      }
    >
      <div className="flex gap-3 pb-4">
        <Icon className="mt-0.5 size-5 shrink-0 text-ink" />
        <div className="text-[0.9375rem] leading-relaxed text-body">
          <p>{c.body}</p>
          {red && c.maxScale && <p className="mt-2 font-medium text-ink">Largest scale that fits: {c.maxScale}×.</p>}
          {red && plan.level !== 'advanced' && <p className="mt-2 text-[0.8125rem] text-muted">Advanced settings allow continuing anyway.</p>}
        </div>
      </div>
    </Sheet>
  );
}

/* ---------- 6.4 Preview crop ---------- */
function CropSheet({ open, onClose, photo, model }: { open: boolean; onClose: () => void; photo: Photo; model: Model }) {
  const [pt, setPt] = useState({ x: 50, y: 42 });
  const [running, setRunning] = useState(true);

  useEffect(() => {
    if (!open) return;
    setRunning(true);
    const t = setTimeout(() => setRunning(false), 1300);
    return () => clearTimeout(t);
  }, [open, pt]);

  const crop = (src: string): CSSProperties => ({ backgroundImage: `url("${src}")`, backgroundSize: '430%', backgroundPosition: `${pt.x}% ${pt.y}%` });

  return (
    <Sheet open={open} onClose={onClose} title="Preview crop" tall>
      <p className="text-sm leading-relaxed text-body">Tap the photo to choose a 512 × 512 area. The full pipeline runs on that area only.</p>
      <button
        type="button"
        aria-label="Choose the preview area"
        className="relative mx-auto mt-4 block max-h-52 overflow-hidden rounded-lg bg-card"
        style={{ aspectRatio: `${photo.w} / ${photo.h}`, width: `min(100%, calc(13rem * ${(photo.w / photo.h).toFixed(4)}))` }}
        onClick={(e) => {
          const r = e.currentTarget.getBoundingClientRect();
          setPt({ x: Math.round(((e.clientX - r.left) / r.width) * 100), y: Math.round(((e.clientY - r.top) / r.height) * 100) });
        }}
      >
        <img src={photo.src} alt="" className="size-full object-cover" />
        <span className="pointer-events-none absolute size-12 -translate-x-1/2 -translate-y-1/2 rounded-xs border-2 border-white shadow-[0_0_0_1px_rgba(20,20,19,0.5)] transition-all duration-200" style={{ left: `${Math.min(88, Math.max(12, pt.x))}%`, top: `${Math.min(86, Math.max(14, pt.y))}%` }} />
      </button>

      <div className="mt-4 grid grid-cols-2 gap-3">
        <figure>
          <div className="aspect-square rounded-lg bg-card bg-no-repeat" style={crop(photo.lo)} />
          <figcaption className="mt-2 text-[0.8125rem] font-medium text-ink">Original</figcaption>
        </figure>
        <figure>
          {running ? <div className="skeleton aspect-square rounded-lg" /> : <div className="anim-fade aspect-square rounded-lg bg-card bg-no-repeat" style={crop(photo.src)} />}
          <figcaption className="mt-2 text-[0.8125rem] font-medium text-ink" aria-live="polite">
            {running ? 'Running preview…' : 'Preview'}
          </figcaption>
        </figure>
      </div>
      <p className="mt-3 pb-4 text-[0.8125rem] leading-snug text-muted">
        {model.name} · {running ? 'estimating' : 'finished in about 6 s'}
      </p>
    </Sheet>
  );
}
