import { useState, type ReactNode } from 'react';
import { ChevronDown, MessageSquare } from 'lucide-react';
import { DEVICE, FAQ, LICENSES, MODELS, MODE_NAME } from '../lib/data';
import { fmtMB } from '../lib/plan';
import { useApp, type Settings } from '../lib/store';
import { Button, ConfirmSheet, Group, Mark, RadioMark, Row, Screen, Segmented, Sheet, SwitchRow, TopBar, useScrolled } from '../components/ui';

function Label({ children }: { children: ReactNode }) {
  return <h2 className="eyebrow mt-8 mb-2.5 px-1 text-muted">{children}</h2>;
}

interface Option<T extends string> {
  value: T;
  label: string;
  sub?: string;
  disabled?: boolean;
}

function OptionSheet<T extends string>({ open, onClose, title, value, options, onChange }: { open: boolean; onClose: () => void; title: string; value: T; options: Option<T>[]; onChange: (v: T) => void }) {
  return (
    <Sheet open={open} onClose={onClose} title={title}>
      <Group className="mb-4">
        {options.map((o) => (
          <button
            key={o.value}
            type="button"
            role="radio"
            aria-checked={o.value === value}
            disabled={o.disabled}
            onClick={() => {
              onChange(o.value);
              onClose();
            }}
            className="flex min-h-14 w-full items-center gap-3 px-4 py-2.5 text-left transition-colors active:bg-soft disabled:opacity-55"
          >
            <span className="min-w-0 flex-1">
              <span className="block text-[0.9375rem] font-medium text-ink">{o.label}</span>
              {o.sub && <span className="mt-0.5 block text-[0.8125rem] text-muted">{o.sub}</span>}
            </span>
            <RadioMark checked={o.value === value} />
          </button>
        ))}
      </Group>
    </Sheet>
  );
}

const FORMAT_LABEL: Record<Settings['format'], string> = { auto: 'Auto', jpeg: 'JPEG', png: 'PNG', webp: 'WebP' };
const COMPUTE_LABEL: Record<Settings['compute'], string> = { auto: 'Auto', gpu: 'GPU', cpu: 'CPU' };

/* ---------- 10.1 Settings ---------- */
export function SettingsScreen() {
  const app = useApp();
  const { s, nav, setSettings } = app;
  const st = s.settings;
  const [sheet, setSheet] = useState<null | 'language' | 'launch' | 'compute' | 'format' | 'reset'>(null);
  const [checking, setChecking] = useState(false);
  const [cacheMB, setCacheMB] = useState(12.4);
  const { scrolled, onScroll } = useScrolled();
  const usedMB = MODELS.filter((m) => s.installed.includes(m.id)).reduce((sum, m) => sum + m.sizeMB, 0);

  return (
    <Screen>
      <TopBar title="Settings" onBack={() => nav.pop()} scrolled={scrolled} />
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 pb-10" onScroll={onScroll}>
        <h2 className="eyebrow mb-2.5 px-1 pt-2 text-muted">General</h2>
        <Group>
          <Row title="Language" value="English" chevron onClick={() => setSheet('language')} />
          <div className="px-4 py-3.5">
            <p className="mb-2.5 text-[0.9375rem] font-medium text-ink">Theme</p>
            <Segmented
              label="Theme"
              value={st.theme}
              onChange={(v) => setSettings({ theme: v })}
              options={[
                { value: 'system', label: 'System' },
                { value: 'light', label: 'Light' },
                { value: 'dark', label: 'Dark' },
              ]}
            />
          </div>
          <SwitchRow title="Dynamic color" sub="Android 12 and later. Follows your wallpaper." checked={st.dynamicColor} onChange={(v) => setSettings({ dynamicColor: v })} />
          <SwitchRow
            title="Haptics"
            sub="Light taps for selections and progress."
            checked={st.haptics}
            onChange={(v) => {
              setSettings({ haptics: v });
              if (v) navigator.vibrate?.(10);
            }}
          />
        </Group>

        <Label>Processing</Label>
        <Group>
          <Row title="Default mode on launch" value={st.launchMode === 'last' ? 'Last used' : MODE_NAME[st.launchMode]} chevron onClick={() => setSheet('launch')} />
          <Row title="Compute preference" value={COMPUTE_LABEL[st.compute]} chevron onClick={() => setSheet('compute')} />
          <SwitchRow title="Keep screen awake" sub="While an upscale is running." checked={st.keepAwake} onChange={(v) => setSettings({ keepAwake: v })} />
          <SwitchRow title="Slow down when hot or low on battery" sub="Protects your phone during long jobs." checked={st.throttle} onChange={(v) => setSettings({ throttle: v })} />
          <Row
            title="Re-run device check"
            sub={`${DEVICE.ramGB} GB memory · ${DEVICE.gpu}`}
            value={checking ? 'Checking…' : DEVICE.label}
            disabled={checking}
            onClick={() => {
              setChecking(true);
              setTimeout(() => {
                setChecking(false);
                app.toast(`Device check complete. Performance: ${DEVICE.label}`);
              }, 1600);
            }}
          />
        </Group>

        <Label>Output</Label>
        <Group>
          <Row title="Save folder" sub="Pictures/RealScale-Pro · Can be changed on desktop." />
          <Row title="Default output format" value={FORMAT_LABEL[st.format]} chevron onClick={() => setSheet('format')} />
          <SwitchRow title="Keep photo metadata" sub="Date, camera and location." checked={st.keepMeta} onChange={(v) => setSettings({ keepMeta: v })} />
        </Group>

        <Label>Downloads and storage</Label>
        <Group>
          <SwitchRow title="Download on Wi-Fi only" checked={st.wifiOnly} onChange={(v) => setSettings({ wifiOnly: v })} />
          <Row title="Model Manager" value={fmtMB(usedMB)} chevron onClick={() => nav.push({ name: 'models' })} />
          <Row
            title="Clear cache"
            sub="Temporary files and thumbnails."
            value={fmtMB(cacheMB)}
            disabled={cacheMB === 0}
            onClick={() => {
              setCacheMB(0);
              app.toast('Cache cleared');
            }}
          />
        </Group>

        <Label>Privacy</Label>
        <Group>
          <SwitchRow title="Usage analytics" sub="Off by default. Never includes images." checked={st.analytics} onChange={(v) => setSettings({ analytics: v })} />
          <SwitchRow title="Crash reports" sub="Off by default." checked={st.crashReports} onChange={(v) => setSettings({ crashReports: v })} />
        </Group>
        <p className="mt-2.5 px-1 text-[0.8125rem] leading-relaxed text-muted">Your images never leave this device. There is no account and no cloud processing.</p>

        <Label>Help</Label>
        <Group>
          <Row title="Replay the guide" chevron onClick={app.replayGuide} />
          <Row title="FAQ and feedback" chevron onClick={() => nav.push({ name: 'help' })} />
        </Group>

        <Label>About</Label>
        <Group>
          <Row title="Version" value="2.0 · Images" />
          <Row title="Open-source licenses and credits" chevron onClick={() => nav.push({ name: 'about' })} />
        </Group>

        <Button variant="danger" full className="mt-8" onClick={() => setSheet('reset')}>
          Reset app and replay first launch
        </Button>
      </div>

      <OptionSheet
        open={sheet === 'language'}
        onClose={() => setSheet(null)}
        title="Language"
        value="en"
        onChange={() => undefined}
        options={[
          { value: 'en', label: 'English' },
          { value: 'hi', label: 'Hindi', sub: 'Coming in a later update', disabled: true },
        ]}
      />
      <OptionSheet
        open={sheet === 'launch'}
        onClose={() => setSheet(null)}
        title="Default mode on launch"
        value={st.launchMode}
        onChange={(v) => setSettings({ launchMode: v })}
        options={[
          { value: 'last', label: 'Last used' },
          { value: 'upscale', label: 'Upscale Mode', sub: 'Fast and simple' },
          { value: 'super', label: 'Super Resolution Mode', sub: 'Maximum quality' },
        ]}
      />
      <OptionSheet
        open={sheet === 'compute'}
        onClose={() => setSheet(null)}
        title="Compute preference"
        value={st.compute}
        onChange={(v) => setSettings({ compute: v })}
        options={[
          { value: 'auto', label: 'Auto', sub: 'GPU when it is stable, otherwise CPU' },
          { value: 'gpu', label: 'GPU', sub: `${DEVICE.gpu} · Vulkan ${DEVICE.vulkan}` },
          { value: 'cpu', label: 'CPU', sub: `${DEVICE.cores} cores. Slower, most compatible.` },
        ]}
      />
      <OptionSheet
        open={sheet === 'format'}
        onClose={() => setSheet(null)}
        title="Default output format"
        value={st.format}
        onChange={(v) => setSettings({ format: v })}
        options={[
          { value: 'auto', label: 'Auto', sub: 'Source format in Upscale Mode, PNG in Super Resolution' },
          { value: 'jpeg', label: 'JPEG' },
          { value: 'png', label: 'PNG', sub: 'Lossless' },
          { value: 'webp', label: 'WebP' },
        ]}
      />
      <ConfirmSheet
        open={sheet === 'reset'}
        onClose={() => setSheet(null)}
        title="Reset the app?"
        body="Settings, installed models and saved results in this preview are cleared, and the first-launch experience starts again."
        confirmLabel="Reset app"
        onConfirm={() => {
          setSheet(null);
          app.resetApp();
        }}
        danger
      />
    </Screen>
  );
}

/* ---------- 10.2 Help and FAQ ---------- */
export function Help() {
  const app = useApp();
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const [attach, setAttach] = useState(true);
  const { scrolled, onScroll } = useScrolled();

  return (
    <Screen>
      <TopBar title="Help and feedback" onBack={() => app.nav.pop()} scrolled={scrolled} />
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 pb-10" onScroll={onScroll}>
        <h2 className="display pt-2 text-[1.75rem]">Common questions</h2>
        <div className="mt-4 divide-y divide-hairline-soft overflow-hidden rounded-lg border border-hairline">
          {FAQ.map((f) => (
            <details key={f.q} className="group">
              <summary className="flex min-h-14 list-none items-center gap-3 px-4 py-3 text-[0.9375rem] leading-snug font-medium text-ink active:bg-soft [&::-webkit-details-marker]:hidden">
                <span className="flex-1">{f.q}</span>
                <ChevronDown className="size-4.5 shrink-0 text-muted transition-transform duration-200 group-open:rotate-180" aria-hidden />
              </summary>
              <p className="px-4 pb-4 text-sm leading-relaxed text-body">{f.a}</p>
            </details>
          ))}
        </div>

        <div className="mt-8 rounded-lg bg-card p-5">
          <MessageSquare className="size-6 text-ink" strokeWidth={1.7} />
          <h2 className="mt-3 text-base font-medium text-ink">Something not working?</h2>
          <p className="mt-1 text-sm leading-relaxed text-body">Tell us what happened. You can attach your device profile, never your images.</p>
          <Button variant="primary" className="mt-4" onClick={() => setOpen(true)}>
            Send feedback
          </Button>
        </div>

        <Group className="mt-3">
          <Row title="Replay the guide" chevron onClick={app.replayGuide} />
        </Group>
      </div>

      <Sheet
        open={open}
        onClose={() => setOpen(false)}
        title="Send feedback"
        footer={
          <Button
            variant="primary"
            size="lg"
            full
            disabled={text.trim().length < 3}
            onClick={() => {
              setOpen(false);
              setText('');
              app.toast('Thanks. Your feedback was saved to send.');
            }}
          >
            Send
          </Button>
        }
      >
        <label htmlFor="feedback" className="text-[0.9375rem] font-medium text-ink">
          What happened?
        </label>
        <textarea
          id="feedback"
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={4}
          placeholder="For example: the result looked too soft at 4×"
          className="mt-2 w-full resize-none rounded-md border border-hairline bg-canvas px-3.5 py-2.5 text-base leading-relaxed text-ink outline-none placeholder:text-muted-soft focus:border-primary focus:shadow-[0_0_0_3px_color-mix(in_srgb,#cc785c_18%,transparent)]"
        />
        <Group className="mt-3 mb-4">
          <SwitchRow title="Attach device profile" sub={`${DEVICE.label} · ${DEVICE.ramGB} GB · ${DEVICE.gpu}. Images are never attached.`} checked={attach} onChange={setAttach} />
        </Group>
      </Sheet>
    </Screen>
  );
}

/* ---------- About and open-source licenses ---------- */
export function About() {
  const app = useApp();
  const [doc, setDoc] = useState<null | 'terms' | 'privacy'>(null);
  const { scrolled, onScroll } = useScrolled();
  return (
    <Screen>
      <TopBar title="About" onBack={() => app.nav.pop()} scrolled={scrolled} />
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 pb-10" onScroll={onScroll}>
        <div className="pt-4">
          <Mark className="size-20 shadow-soft" />
          <p className="display mt-4 text-[2.25rem]">RealScale-Pro</p>
          <p className="mt-1.5 text-sm text-muted">Version 2.0 · Image upscaling</p>
          <p className="mt-4 text-[0.9375rem] leading-relaxed text-body">An offline, on-device AI image upscaler. Pick a photo, tap Upscale and get a sharper, bigger image with no account and no upload.</p>
        </div>

        <h2 className="display mt-9 text-[1.625rem]">Open-source licenses</h2>
        <p className="mt-1 text-[0.8125rem] leading-snug text-muted">RealScale-Pro is built on these projects.</p>
        <Group className="mt-3">
          {LICENSES.map((l) => (
            <Row key={l.name} title={l.name} sub={l.use} value={<span className="font-mono text-xs">{l.license}</span>} />
          ))}
        </Group>

        <h2 className="display mt-9 text-[1.625rem]">Model credits</h2>
        <p className="mt-2 text-sm leading-relaxed text-body">The models are the ones bundled with RealSR-NCNN-Android by tumuyan: the Real-ESRGAN, Real-CUGAN and Anime4K families. Each model is used under its own license.</p>

        <Group className="mt-8">
          <Row title="Terms of use" chevron onClick={() => setDoc('terms')} />
          <Row title="Privacy policy" chevron onClick={() => setDoc('privacy')} />
        </Group>

        <p className="mt-8 rounded-lg bg-card p-4 text-[0.8125rem] leading-relaxed text-body">
          <span className="font-medium text-ink">Design preview.</span> This build shows the interface and flows. Upscaling, downloads and device checks are simulated, so no image is actually processed.
        </p>
      </div>

      <Sheet open={doc !== null} onClose={() => setDoc(null)} title={doc === 'terms' ? 'Terms of use' : 'Privacy policy'}>
        <div className="space-y-3 pb-4 text-[0.9375rem] leading-relaxed text-body">
          {doc === 'terms' ? (
            <>
              <p>RealScale-Pro is provided as is. You keep full ownership of every image you upscale.</p>
              <p>Use the app only with images you have the right to edit.</p>
            </>
          ) : (
            <>
              <p>Images are processed on your device and are never uploaded.</p>
              <p>The network is used only to download models and to check for model updates. Analytics and crash reports are off unless you turn them on.</p>
            </>
          )}
        </div>
      </Sheet>
    </Screen>
  );
}
