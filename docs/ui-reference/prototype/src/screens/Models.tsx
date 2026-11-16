import { useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { DEVICE, MODELS, PACKS, fitOf, type Model } from '../lib/data';
import { fmtMB } from '../lib/plan';
import { useApp, useLive } from '../lib/store';
import { ModelRow } from '../components/shared';
import { Button, Chip, ConfirmSheet, Group, Screen, TopBar, useScrolled } from '../components/ui';

export function Models() {
  const app = useApp();
  const live = useLive();
  const { s, nav } = app;
  const [type, setType] = useState<'all' | 'photo' | 'anime'>('all');
  const [del, setDel] = useState<Model | null>(null);
  const [checking, setChecking] = useState(false);
  const { scrolled, onScroll } = useScrolled();

  const usedMB = MODELS.filter((m) => s.installed.includes(m.id)).reduce((sum, m) => sum + m.sizeMB, 0);
  const freeGB = DEVICE.freeGB - usedMB / 1000;
  const otherPct = ((DEVICE.totalGB - DEVICE.freeGB) / DEVICE.totalGB) * 100;
  const modelsPct = Math.max(1.2, (usedMB / 1000 / DEVICE.totalGB) * 100);

  const visible = (m: Model) => type === 'all' || m.kind === type;

  const remove = (m: Model) => {
    app.set({ installed: s.installed.filter((id) => id !== m.id) });
    // Settings that used this model fall back to the automatic choice.
    (['upscale', 'super'] as const).forEach((mode) => s.prefs[mode].modelId === m.id && app.setPrefs(mode, { modelId: 'auto' }));
    app.toast(`${m.name} deleted`);
    setDel(null);
  };

  return (
    <Screen>
      <TopBar title="Model Manager" onBack={() => nav.pop()} scrolled={scrolled} />
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 pb-8" onScroll={onScroll}>
        {/* Storage */}
        <div className="rounded-lg bg-card p-5">
          <p className="eyebrow text-body">Storage</p>
          <p className="display tnum mt-2 text-[2.25rem]">{fmtMB(usedMB)}</p>
          <p className="mt-1 text-sm text-body">
            used by {s.installed.length} models · {freeGB.toFixed(1)} GB free of {DEVICE.totalGB} GB
          </p>
          <div className="mt-4 flex h-2 gap-0.5 overflow-hidden rounded-full" role="img" aria-label={`Models use ${fmtMB(usedMB)}. ${freeGB.toFixed(1)} gigabytes are free.`}>
            <span className="rounded-full bg-ink" style={{ width: `${modelsPct}%` }} />
            <span className="rounded-full bg-muted-soft" style={{ width: `${otherPct}%` }} />
            <span className="flex-1 rounded-full bg-canvas" />
          </div>
          <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-body">
            <span className="inline-flex items-center gap-1.5">
              <span className="size-2 rounded-full bg-ink" />
              Models
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="size-2 rounded-full bg-muted-soft" />
              Other
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="size-2 rounded-full border border-muted-soft bg-canvas" />
              Free
            </span>
          </div>
        </div>

        {/* Filters */}
        <div className="no-scrollbar -mx-5 mt-5 flex gap-2 overflow-x-auto px-5" role="group" aria-label="Filter by type">
          {(['all', 'photo', 'anime'] as const).map((t) => (
            <Chip key={t} selected={type === t} onClick={() => setType(t)} className="shrink-0 capitalize">
              {t}
            </Chip>
          ))}
        </div>

        {/* Packs */}
        {PACKS.map((pack) => {
          const models = MODELS.filter((m) => m.pack === pack.id && visible(m));
          if (models.length === 0) return null;
          const pending = models.filter((m) => !s.installed.includes(m.id) && live.downloads[m.id] === undefined && fitOf(m).tone !== 'no');
          const pendingMB = pending.reduce((sum, m) => sum + m.sizeMB, 0);
          return (
            <section key={pack.id} className="mt-8" aria-labelledby={`pack-${pack.id}`}>
              <div className="flex items-end justify-between gap-3">
                <div className="min-w-0">
                  <h2 id={`pack-${pack.id}`} className="display text-[1.625rem]">
                    {pack.name}
                  </h2>
                  <p className="mt-1 text-[0.8125rem] leading-snug text-muted">{pack.note}</p>
                </div>
                {pending.length > 1 && (
                  <button type="button" onClick={() => pending.forEach((m) => live.download(m.id))} className="-mr-2 min-h-11 shrink-0 rounded-md px-2 text-right text-sm leading-tight font-medium text-link active:bg-card">
                    Download all
                    <span className="block text-xs font-normal text-muted">{fmtMB(pendingMB)}</span>
                  </button>
                )}
              </div>
              <Group className="mt-3">
                {models.map((m) => (
                  <ModelRow key={m.id} m={m} variant="manage" onDelete={() => setDel(m)} />
                ))}
              </Group>
            </section>
          );
        })}

        <Button
          full
          className="mt-8"
          disabled={checking}
          icon={<RefreshCw className={checking ? 'size-4.5 animate-spin' : 'size-4.5'} />}
          onClick={() => {
            setChecking(true);
            setTimeout(() => {
              setChecking(false);
              app.toast('All models are up to date');
            }, 1200);
          }}
        >
          {checking ? 'Checking…' : 'Check for updates'}
        </Button>
      </div>

      <ConfirmSheet
        open={!!del}
        onClose={() => setDel(null)}
        title={`Delete ${del?.name ?? 'model'}?`}
        body={`This frees ${del ? fmtMB(del.sizeMB) : ''}. You can download it again at any time. Settings that use it switch to the automatic choice.`}
        confirmLabel="Delete model"
        onConfirm={() => del && remove(del)}
        danger
      />
    </Screen>
  );
}
